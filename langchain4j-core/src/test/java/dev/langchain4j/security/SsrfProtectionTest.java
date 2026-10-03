package dev.langchain4j.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Robust unit tests for {@link SsrfProtection}.
 *
 * <p>Categories covered:
 * <ol>
 *   <li>Scheme validation (HTTP, HTTPS, non-HTTP, missing)</li>
 *   <li>URL parsing (null, malformed, missing host)</li>
 *   <li>Embedded credentials bypass prevention</li>
 *   <li>Cloud metadata hostname blocking</li>
 *   <li>Cloud metadata IP blocking (all providers, even in dev mode)</li>
 *   <li>Localhost / loopback blocking by name and IP (IPv4 + IPv6)</li>
 *   <li>Private RFC 1918 IP blocking and dev-mode allow-list</li>
 *   <li>IPv6 unique-local (fc00::/7) blocking</li>
 *   <li>Link-local IPv4 range blocking (169.254.0.0/16)</li>
 *   <li>IPv4-mapped IPv6 (::ffff:x.x.x.x) unwrapping for bypass prevention</li>
 *   <li>Return-value contract of validateSafeUrl</li>
 *   <li>isSafeUrl never-throws contract</li>
 * </ol>
 */
class SsrfProtectionTest {

    // ── 1. Scheme validation ──────────────────────────────────────────────────

    /**
     * HTTPS is always allowed; HTTP is allowed only when the {@code allowHttp} flag is true.
     * Both cases share the same assertion (no exception thrown), so they are combined here.
     */
    @ParameterizedTest(name = "safe URL passes: url={0} allowPrivate={1} allowHttp={2}")
    @CsvSource({
        "https://example.com/path, false, false", // HTTPS – always allowed
        "http://example.com/,      false, true" // HTTP  – allowed when flag is true
    })
    void validate_safe_urls_do_not_throw(String url, boolean allowPrivate, boolean allowHttp) {
        assertThatCode(() -> SsrfProtection.validateSafeUrl(url, allowPrivate, allowHttp))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_http_blocked_when_allow_http_flag_is_false() {
        assertThatThrownBy(() -> SsrfProtection.validateSafeUrl("http://example.com/", false, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HTTPS");
    }

    @ParameterizedTest(name = "scheme blocked: {0}")
    @ValueSource(strings = {"ftp://example.com", "file:///etc/passwd", "gopher://example.com", "data:text/plain,test"})
    void validate_non_http_schemes_are_blocked(String url) {
        assertThatThrownBy(() -> SsrfProtection.validateSafeUrl(url, false, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HTTP/HTTPS");
    }

    // ── 2. URL parsing ────────────────────────────────────────────────────────

    @Test
    void validate_null_url_throws_illegal_argument() {
        assertThatThrownBy(() -> SsrfProtection.validateSafeUrl(null, false, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validate_malformed_url_throws_with_invalid_url_message() {
        assertThatThrownBy(() -> SsrfProtection.validateSafeUrl("not a url %%bad", false, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid URL");
    }

    @Test
    void validate_url_with_missing_host_throws() {
        // http:///path has an empty authority / host segment
        assertThatThrownBy(() -> SsrfProtection.validateSafeUrl("http:///path", false, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── 3. Embedded credentials (user:pass@host) bypass prevention ────────────

    /**
     * All three variants embed credentials in the URL and must be blocked with the
     * same "credentials" error, regardless of form (user+pass, bare username, or
     * a metadata IP used as the username to fool the host parser).
     */
    @ParameterizedTest(name = "credentials blocked: {0}")
    @ValueSource(
            strings = {
                "http://user:pass@example.com/", // standard user:pass
                "http://169.254.169.254@example.com/", // metadata IP as username (parser-confusion attack)
                "https://admin@example.com/" // bare username only
            })
    void validate_urls_with_embedded_credentials_are_blocked(String url) {
        assertThatThrownBy(() -> SsrfProtection.validateSafeUrl(url, false, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("credentials");
    }

    // ── 4. Cloud metadata hostname blocking ───────────────────────────────────

    @ParameterizedTest(name = "metadata hostname blocked: {0}")
    @ValueSource(
            strings = {
                "http://metadata.google.internal/computeMetadata/v1/", // GCP
                "http://metadata/", // OpenStack short name
                "http://instance-data/" // Ubuntu cloud-init
            })
    void validate_cloud_metadata_hostnames_are_blocked(String url) {
        assertThatThrownBy(() -> SsrfProtection.validateSafeUrl(url, false, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("metadata");
    }

    @ParameterizedTest(name = "metadata hostname blocked in dev mode too: {0}")
    @ValueSource(strings = {"http://metadata.google.internal/", "http://metadata/", "http://instance-data/"})
    void validate_cloud_metadata_hostnames_blocked_even_with_allow_private(String url) {
        // allowPrivate=true MUST NOT loosen metadata hostname protection
        assertThatThrownBy(() -> SsrfProtection.validateSafeUrl(url, true, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("metadata");
    }

    // ── 5. Cloud metadata IP blocking ─────────────────────────────────────────

    @ParameterizedTest(name = "metadata IP blocked: {0}")
    @ValueSource(
            strings = {
                "http://169.254.169.254/latest/meta-data/", // AWS EC2 / GCP / Azure IMDS
                "http://169.254.170.2/", // AWS ECS task credentials
                "http://169.254.170.23/", // AWS EKS pod identity
                "http://100.100.100.200/" // Alibaba Cloud metadata
            })
    void validate_cloud_metadata_ips_are_blocked(String url) {
        assertThatThrownBy(() -> SsrfProtection.validateSafeUrl(url, false, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "metadata IP blocked even in dev mode: {0}")
    @ValueSource(strings = {"http://169.254.169.254/", "http://100.100.100.200/"})
    void validate_cloud_metadata_ips_blocked_even_with_allow_private(String url) {
        // allowPrivate=true MUST NOT allow cloud metadata IPs under any circumstances
        assertThatThrownBy(() -> SsrfProtection.validateSafeUrl(url, true, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── 6. Localhost / loopback blocking ──────────────────────────────────────

    @ParameterizedTest(name = "localhost name blocked: {0}")
    @ValueSource(strings = {"http://localhost/", "http://localhost:8080/admin", "http://localhost.localdomain/"})
    void validate_localhost_names_blocked_by_default(String url) {
        assertThatThrownBy(() -> SsrfProtection.validateSafeUrl(url, false, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Localhost");
    }

    /**
     * Both IPv4 (127.0.0.1) and IPv6 (::1) loopback addresses share the same
     * blocking behaviour, so they are covered by a single parameterized test.
     */
    @ParameterizedTest(name = "loopback IP blocked: {0}")
    @ValueSource(strings = {"http://127.0.0.1/", "http://[::1]/"})
    void validate_loopback_ips_are_blocked_by_default(String url) {
        assertThatThrownBy(() -> SsrfProtection.validateSafeUrl(url, false, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("localhost");
    }

    /**
     * When {@code allowPrivate=true}, all localhost names and loopback IPs
     * (both IPv4 and IPv6) must be permitted.
     */
    @ParameterizedTest(name = "localhost/loopback allowed in dev mode: {0}")
    @ValueSource(strings = {"http://localhost:8080/", "http://127.0.0.1/", "http://[::1]/"})
    void validate_localhost_and_loopback_allowed_when_allow_private_is_true(String url) {
        assertThatCode(() -> SsrfProtection.validateSafeUrl(url, true, true)).doesNotThrowAnyException();
    }

    // ── 7. Private RFC 1918 IP blocking ───────────────────────────────────────

    @ParameterizedTest(name = "private IP blocked: {0}")
    @ValueSource(
            strings = {
                "http://10.0.0.1/", // 10.0.0.0/8  – start of range
                "http://10.255.255.255/", // 10.0.0.0/8  – end of range
                "http://172.16.0.1/", // 172.16.0.0/12 – start of range
                "http://172.31.255.255/", // 172.16.0.0/12 – end of range
                "http://192.168.0.1/", // 192.168.0.0/16 – start of range
                "http://192.168.255.255/" // 192.168.0.0/16 – end of range
            })
    void validate_private_rfc1918_ips_are_blocked(String url) {
        assertThatThrownBy(() -> SsrfProtection.validateSafeUrl(url, false, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("private");
    }

    @ParameterizedTest(name = "private IP allowed in dev mode: {0}")
    @ValueSource(strings = {"http://10.0.0.1/", "http://172.16.0.1/", "http://192.168.1.1/"})
    void validate_private_ips_allowed_when_allow_private_is_true(String url) {
        assertThatCode(() -> SsrfProtection.validateSafeUrl(url, true, true)).doesNotThrowAnyException();
    }

    @Test
    void validate_any_local_address_0_0_0_0_is_blocked() {
        assertThatThrownBy(() -> SsrfProtection.validateSafeUrl("http://0.0.0.0/", false, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── 8. IPv6 unique-local (fc00::/7) blocking ──────────────────────────────

    @ParameterizedTest(name = "IPv6 unique-local blocked: {0}")
    @ValueSource(
            strings = {
                "http://[fd00::1]/", // fd00::/8 – common unique-local prefix
                "http://[fd12:3456:789a::1]/", // arbitrary fd:: address
                "http://[fc00::1]/" // fc00::/8 – the other half of fc00::/7
            })
    void validate_ipv6_unique_local_is_blocked(String url) {
        assertThatThrownBy(() -> SsrfProtection.validateSafeUrl(url, false, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "IPv6 unique-local allowed in dev mode: {0}")
    @ValueSource(strings = {"http://[fd00::1]/", "http://[fc00::1]/"})
    void validate_ipv6_unique_local_allowed_when_allow_private_is_true(String url) {
        assertThatCode(() -> SsrfProtection.validateSafeUrl(url, true, true)).doesNotThrowAnyException();
    }

    // ── 9. Link-local IPv4 range (169.254.0.0/16) blocking ───────────────────

    /**
     * The entire 169.254.0.0/16 range must be blocked as a defence-in-depth
     * measure, not just the specific known metadata IPs.  Multiple addresses
     * across the range are checked here.
     */
    @ParameterizedTest(name = "link-local IPv4 blocked: {0}")
    @ValueSource(
            strings = {
                "http://169.254.0.1/", // start of range (just above the network address)
                "http://169.254.1.1/", // arbitrary address in range
                "http://169.254.254.254/" // near end of range
            })
    void validate_link_local_ipv4_range_is_blocked(String url) {
        assertThatThrownBy(() -> SsrfProtection.validateSafeUrl(url, false, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── 10. IPv4-mapped IPv6 bypass prevention (::ffff:x.x.x.x) ─────────────

    /**
     * An attacker can encode a blocked IPv4 address as an IPv4-mapped IPv6
     * address (::ffff:x.x.x.x) hoping the check only looks at the IPv6 form.
     * All three categories of blocked IPv4 addresses are verified here.
     */
    @ParameterizedTest(name = "IPv4-mapped IPv6 blocked: {0}")
    @ValueSource(
            strings = {
                "http://[::ffff:169.254.169.254]/", // metadata IP in IPv6 form
                "http://[::ffff:127.0.0.1]/", // loopback in IPv6 form
                "http://[::ffff:10.0.0.1]/" // private RFC 1918 IP in IPv6 form
            })
    void validate_ipv4_mapped_ipv6_addresses_are_blocked(String url) {
        assertThatThrownBy(() -> SsrfProtection.validateSafeUrl(url, false, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validate_ipv4_mapped_ipv6_of_metadata_blocked_even_in_dev_mode() {
        // Even allowPrivate=true must not permit the metadata IP in IPv6 form
        assertThatThrownBy(() -> SsrfProtection.validateSafeUrl("http://[::ffff:169.254.169.254]/", true, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── 11. Return-value contract ─────────────────────────────────────────────

    /**
     * {@code validateSafeUrl} must return the exact same {@code String} object
     * that was passed in (identity equality), preserving query strings, fragments,
     * and non-default ports unchanged.
     */
    @ParameterizedTest(name = "returns original URL unchanged: {0}")
    @ValueSource(strings = {"https://example.com/path?query=1#fragment", "https://example.com:8443/api"})
    void validate_safe_url_returns_original_url_unchanged(String url) {
        assertThat(SsrfProtection.validateSafeUrl(url, false, false)).isSameAs(url);
    }

    // ── 12. isSafeUrl – non-throwing facade ───────────────────────────────────

    /**
     * Public URLs that are genuinely safe: one HTTPS-only case and one HTTP-allowed
     * case, parameterized via {@code @CsvSource} so the {@code allowHttp} flag
     * difference is captured explicitly.
     */
    @ParameterizedTest(name = "isSafeUrl true: url={0} allowPrivate={1} allowHttp={2}")
    @CsvSource({
        "https://example.com, false, false", // HTTPS, strict mode
        "http://example.com,  false, true" // HTTP allowed
    })
    void is_safe_url_returns_true_for_public_urls(String url, boolean allowPrivate, boolean allowHttp) {
        assertThat(SsrfProtection.isSafeUrl(url, allowPrivate, allowHttp)).isTrue();
    }

    /**
     * All categories of blocked URLs returning {@code false}, including the two
     * edge cases where the flags differ from the standard (false, true) pair:
     * metadata IP in dev mode, and HTTP when only HTTPS is permitted.
     */
    @ParameterizedTest(name = "isSafeUrl false: url={0} allowPrivate={1} allowHttp={2}")
    @CsvSource({
        "http://localhost/,         false, true", // localhost by name
        "http://127.0.0.1/,        false, true", // loopback IPv4
        "http://192.168.1.1/,      false, true", // private RFC 1918
        "http://169.254.169.254/,  false, true", // cloud metadata IP
        "http://169.254.169.254/,  true,  true", // metadata blocked even in dev mode
        "http://example.com,       false, false" // HTTP when only HTTPS is allowed
    })
    void is_safe_url_returns_false_for_blocked_urls(String url, boolean allowPrivate, boolean allowHttp) {
        assertThat(SsrfProtection.isSafeUrl(url, allowPrivate, allowHttp)).isFalse();
    }

    /**
     * {@code isSafeUrl} must never throw — not for {@code null}, not for an empty
     * string, and not for syntactically invalid or disallowed inputs.  Each input
     * runs as its own parameterized invocation so failures are reported individually.
     */
    @ParameterizedTest(name = "isSafeUrl never throws: [{index}] ''{0}''")
    @NullAndEmptySource
    @ValueSource(strings = {"%%invalid%%", "ftp://example.com"})
    void is_safe_url_returns_false_and_never_throws_for_bad_inputs(String url) {
        assertThat(SsrfProtection.isSafeUrl(url, false, true)).isFalse();
    }
}
