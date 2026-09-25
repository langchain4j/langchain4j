package dev.langchain4j.security;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Utility class that protects your application against
 * <b>Server-Side Request Forgery (SSRF)</b> attacks.
 *
 * <h2>What is SSRF?</h2>
 *
 * <p>SSRF is a security vulnerability where an attacker tricks your server into
 * making HTTP requests on their behalf — often to internal systems that should
 * never be reachable from the public internet. For example:
 *
 * <ul>
 *   <li>An attacker submits {@code http://192.168.1.1/admin} as a "webhook URL".</li>
 *   <li>Your server fetches it, exposing your internal router admin page.</li>
 *   <li>Or worse: {@code http://169.254.169.254/latest/meta-data/} leaks the
 *       AWS instance credentials of your cloud server.</li>
 * </ul>
 *
 * <p>This class validates every URL before your application is allowed to
 * fetch it, rejecting anything that could reach internal infrastructure.
 *
 * <h2>What counts as a "private" or "unsafe" URL?</h2>
 *
 * <p>The following categories of URLs are considered unsafe and are blocked
 * by default (unless explicitly allowed via {@code allowPrivate=true}):
 *
 * <ul>
 *   <li><b>Localhost / loopback</b> — {@code http://localhost}, {@code http://127.0.0.1},
 *       {@code http://[::1]}: These resolve to the machine your server is running on.
 *       An attacker could use them to reach services bound only to 127.0.0.1.</li>
 *
 *   <li><b>Private network ranges (RFC 1918)</b> — IP addresses that are reserved
 *       for use inside private networks and are never routable on the public internet:
 *       <ul>
 *         <li>{@code 10.0.0.0 – 10.255.255.255} (10.0.0.0/8)</li>
 *         <li>{@code 172.16.0.0 – 172.31.255.255} (172.16.0.0/12)</li>
 *         <li>{@code 192.168.0.0 – 192.168.255.255} (192.168.0.0/16)</li>
 *       </ul>
 *       These typically hide internal services like databases, admin panels, or
 *       Kubernetes cluster nodes.</li>
 *
 *   <li><b>Link-local addresses</b> — {@code 169.254.0.0/16} (IPv4) and
 *       {@code fe80::/10} (IPv6): Addresses the operating system assigns
 *       automatically when no DHCP server is found. They are only reachable
 *       on the local network segment. Cloud providers (AWS, GCP, Azure) place
 *       their instance metadata services in this range.</li>
 *
 *   <li><b>IPv6 unique-local (fc00::/7)</b> — Addresses starting with
 *       {@code fc} or {@code fd}: the IPv6 equivalent of private RFC 1918
 *       ranges. For example {@code fd00::1} is a private address.</li>
 *
 *   <li><b>Wildcard / any-local</b> — {@code 0.0.0.0} or {@code ::}:
 *       These bind to all network interfaces and should never be targeted
 *       in an outbound request.</li>
 * </ul>
 *
 * <p><b>Cloud metadata endpoints are <em>always</em> blocked</b>, even when
 * {@code allowPrivate=true}. See {@link #CLOUD_METADATA_IPS} and
 * {@link #CLOUD_METADATA_HOSTNAMES} for details.
 *
 * <h2>Usage</h2>
 *
 * <pre>{@code
 * // Validate a URL (throws IllegalArgumentException if unsafe)
 * String safeUrl = SsrfProtection.validateSafeUrl(
 *     "https://example.com/webhook",
 *     false,   // allowPrivate  – false = block private IPs
 *     true     // allowHttp     – true  = allow both HTTP and HTTPS
 * );
 *
 * // Check if URL is safe without throwing (returns boolean)
 * boolean isSafe = SsrfProtection.isSafeUrl(
 *     "http://192.168.1.1",
 *     false,
 *     true
 * );
 * // → false: 192.168.1.1 is a private IP
 *
 * // Development / testing: allow private IPs (still blocks cloud metadata)
 * String devUrl = SsrfProtection.validateSafeUrl(
 *     "http://localhost:8080",
 *     true,    // allowPrivate – relaxed for local dev
 *     true
 * );
 * }</pre>
 */
public final class SsrfProtection {

    private SsrfProtection() {}

    // -----------------------------
    // Constants
    // -----------------------------

    /**
     * Maximum time (in milliseconds) allowed for a single DNS hostname resolution.
     *
     * <p>Without a timeout, a slow or unresponsive DNS server could cause the
     * calling thread to block indefinitely, opening the door to denial-of-service
     * conditions. If resolution does not complete within this window the URL is
     * treated as unsafe and an {@link IllegalArgumentException} is thrown.
     */
    private static final int DNS_TIMEOUT_MS = 5_000;

    /**
     * Hard-coded IP addresses of <b>cloud instance metadata services</b>.
     *
     * <h3>What is an instance metadata service?</h3>
     *
     * <p>Every major cloud provider exposes a special HTTP endpoint that a
     * virtual machine can query to learn about itself: its hostname, region,
     * attached IAM / service-account credentials, SSH keys, and more. This
     * service is only reachable from <em>inside</em> the virtual machine —
     * it is intentionally placed in the link-local address range
     * ({@code 169.254.0.0/16}) so it is never routable on the public internet.
     *
     * <p>However, if your server fetches arbitrary user-supplied URLs, an attacker
     * can supply one of these addresses and steal your cloud credentials.
     *
     * <h3>Entries in this set</h3>
     * <ul>
     *   <li>{@code 169.254.169.254} — Used by <b>AWS EC2</b>, <b>Google Cloud</b>,
     *       and <b>Azure</b> IMDS (Instance Metadata Service). Querying
     *       {@code /latest/meta-data/iam/security-credentials/} on AWS, for
     *       example, returns temporary AWS access keys.</li>
     *   <li>{@code 169.254.170.2} — AWS <b>ECS</b> (Elastic Container Service)
     *       task credential endpoint. Returns AWS credentials for the running
     *       container task.</li>
     *   <li>{@code 169.254.170.23} — AWS <b>EKS</b> (Elastic Kubernetes Service)
     *       pod identity endpoint.</li>
     *   <li>{@code 100.100.100.200} — <b>Alibaba Cloud</b> ECS metadata endpoint,
     *       equivalent to the AWS 169.254.x.x endpoint.</li>
     *   <li>{@code fd00:ec2::254} — IPv6 address of the AWS EC2 metadata
     *       service (same service as 169.254.169.254, reachable over IPv6).</li>
     *   <li>{@code fd00:ec2::23} — IPv6 address of the AWS EKS pod identity
     *       endpoint.</li>
     *   <li>{@code fe80::a9fe:a9fe} — Link-local IPv6 address used by
     *       <b>OpenStack</b> Nova metadata service ({@code a9fe:a9fe} is the
     *       IPv6 encoding of 169.254.169.254).</li>
     * </ul>
     *
     * <p>Note: even if an IP is not in this set, the entire link-local range
     * ({@code 169.254.0.0/16} and {@code fe80::/10}) is blocked as a
     * defence-in-depth measure by {@link #isCloudMetadataIp}.
     */
    private static final Set<String> CLOUD_METADATA_IPS = Set.of(
            "169.254.169.254", // AWS EC2 / GCP / Azure IMDS
            "169.254.170.2", // AWS ECS task credentials
            "169.254.170.23", // AWS EKS pod identity
            "100.100.100.200", // Alibaba Cloud ECS metadata
            "fd00:ec2::254", // AWS EC2 metadata (IPv6)
            "fd00:ec2::23", // AWS EKS pod identity (IPv6)
            "fe80::a9fe:a9fe" // OpenStack Nova metadata (IPv6 link-local)
            );

    /**
     * Well-known <b>hostnames</b> used by cloud providers for their metadata services.
     *
     * <p>In addition to blocking specific IPs ({@link #CLOUD_METADATA_IPS}), some
     * providers expose their metadata service under a friendly DNS name. These
     * hostnames are blocked <em>before</em> DNS resolution, so they are caught
     * regardless of the IP address the hostname resolves to.
     *
     * <ul>
     *   <li>{@code metadata.google.internal} — <b>Google Cloud</b> Compute Engine
     *       metadata server. Responds on {@code http://metadata.google.internal/
     *       computeMetadata/v1/} and can return service account tokens, SSH keys,
     *       project details, and more.</li>
     *   <li>{@code metadata} — Short hostname used inside some cloud and
     *       virtualisation environments (e.g. older OpenStack deployments) to
     *       reach the local metadata service without a fully-qualified domain name.</li>
     *   <li>{@code instance-data} — Hostname used by <b>Ubuntu cloud images</b>
     *       (via cloud-init) to reach the local metadata endpoint. Resolves to
     *       the link-local address on the guest.</li>
     * </ul>
     */
    private static final Set<String> CLOUD_METADATA_HOSTNAMES = Set.of(
            "metadata.google.internal", // Google Cloud Compute Engine metadata
            "metadata", // OpenStack / generic short hostname
            "instance-data" // Ubuntu cloud-init metadata alias
            );

    /**
     * Common hostname aliases for the <b>loopback</b> (localhost) interface.
     *
     * <p>The loopback interface ({@code 127.0.0.1} / {@code ::1}) is a virtual
     * network interface that always points back to the same machine. Services
     * listening only on localhost (databases, admin dashboards, internal APIs)
     * are intentionally not exposed to the network — they trust that only
     * processes on the same machine will connect to them.
     *
     * <p>An attacker who can make your server fetch a {@code localhost} URL can
     * reach those hidden services. For example:
     * <ul>
     *   <li>{@code http://localhost:6379} — might hit a Redis instance with
     *       no authentication configured.</li>
     *   <li>{@code http://localhost:8080/actuator/env} — might expose Spring
     *       Boot internal configuration endpoints.</li>
     * </ul>
     *
     * <ul>
     *   <li>{@code localhost} — The universal loopback hostname, defined in
     *       {@code /etc/hosts} on every OS. Resolves to {@code 127.0.0.1}
     *       (IPv4) or {@code ::1} (IPv6).</li>
     *   <li>{@code localhost.localdomain} — A common Linux alias for localhost,
     *       also mapped to {@code 127.0.0.1} in {@code /etc/hosts}.</li>
     * </ul>
     *
     * <p>Note: loopback IPs are also blocked by IP-range checks after DNS
     * resolution, providing a second layer of defence.
     */
    private static final Set<String> LOCALHOST_NAMES = Set.of(
            "localhost", // Standard loopback hostname
            "localhost.localdomain" // Common Linux alias for localhost
            );

    // -----------------------------
    // Public API
    // -----------------------------

    /**
     * Validates that a URL is safe to fetch and returns it unchanged if so.
     *
     * <p>The following checks are performed <em>in order</em>:
     * <ol>
     *   <li><b>Scheme check</b> — Only {@code http} and {@code https} are
     *       permitted. Schemes like {@code file://}, {@code ftp://}, or
     *       {@code gopher://} are rejected immediately.</li>
     *   <li><b>Credential check</b> — URLs containing a username or password
     *       (e.g. {@code http://user:pass@host}) are rejected to prevent
     *       host-confusion attacks.</li>
     *   <li><b>Hostname check</b> — The hostname must be non-empty and must
     *       not be a known cloud metadata hostname (see
     *       {@link #CLOUD_METADATA_HOSTNAMES}).</li>
     *   <li><b>Localhost name check</b> — If {@code allowPrivate} is
     *       {@code false}, names like {@code localhost} are rejected before
     *       DNS resolution even takes place.</li>
     *   <li><b>DNS resolution</b> — The hostname is resolved to one or more
     *       IP addresses. All of them are validated; a single unsafe IP
     *       causes the whole URL to be rejected.</li>
     *   <li><b>IP classification</b> (for each resolved address):
     *     <ul>
     *       <li>Cloud metadata IPs — always blocked, even when
     *           {@code allowPrivate=true}.</li>
     *       <li>Loopback IPs ({@code 127.x.x.x}, {@code ::1}) — blocked
     *           unless {@code allowPrivate=true}.</li>
     *       <li>Private / link-local / unique-local IPs — blocked unless
     *           {@code allowPrivate=true}.</li>
     *     </ul>
     *   </li>
     * </ol>
     *
     * @param url
     *        The URL string to validate (e.g. {@code "https://example.com/webhook"}).
     *        Must not be {@code null}.
     *
     * @param allowPrivate
     *        Set to {@code true} to permit private-network URLs (localhost,
     *        RFC 1918 ranges, etc.). Useful during local development or
     *        integration testing when your services run on {@code localhost}
     *        or an internal network. <b>Typically set to {@code false} in production.</b>
     *        Cloud metadata endpoints are <em>never</em> permitted regardless
     *        of this flag.
     *
     * @param allowHttp
     *        Set to {@code true} to allow both {@code http://} and
     *        {@code https://} URLs. Set to {@code false} to enforce HTTPS-only
     *        (recommended for production to prevent credential leakage over
     *        plain HTTP).
     *
     * @return The original {@code url} string, unchanged, if it passes all checks.
     *
     * @throws IllegalArgumentException
     *         If the URL is malformed, uses a disallowed scheme, contains
     *         embedded credentials, resolves to a blocked IP address, or if
     *         DNS resolution fails or times out.
     *
     * <h4>Examples</h4>
     *
     * <pre>{@code
     * // ✔ Safe public URL
     * validateSafeUrl("https://example.com", false, true);
     * // → returns "https://example.com"
     *
     * // ✘ Loopback IP
     * validateSafeUrl("http://127.0.0.1", false, true);
     * // → throws IllegalArgumentException: "URL resolves to localhost IP"
     *
     * // ✘ AWS metadata endpoint
     * validateSafeUrl("http://169.254.169.254/latest/meta-data/", false, true);
     * // → throws IllegalArgumentException: "URL resolves to cloud metadata IP"
     *
     * // ✔ Localhost allowed in dev mode
     * validateSafeUrl("http://localhost:8080", true, true);
     * // → returns "http://localhost:8080"
     * }</pre>
     */
    public static String validateSafeUrl(String url, boolean allowPrivate, boolean allowHttp) {

        URI uri = parseUrl(url);

        validateScheme(uri, allowHttp);

        // Block embedded credentials (e.g. http://169.254.169.254@legit.com) to prevent
        // host-parser confusion attacks across HTTP client implementations.
        if (uri.getUserInfo() != null) {
            throw new IllegalArgumentException("URLs with embedded credentials are not allowed");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("URL must have a valid hostname");
        }

        // Block metadata hostnames
        if (isCloudMetadataHost(host)) {
            throw new IllegalArgumentException("Cloud metadata endpoints are not allowed: " + host);
        }

        // Block localhost
        if (!allowPrivate && isLocalhost(host)) {
            throw new IllegalArgumentException("Localhost URLs are not allowed: " + host);
        }

        // Resolve DNS and validate all IPs
        InetAddress[] addresses = resolveAll(host);

        for (InetAddress addr : addresses) {

            String normalizedIp = normalizeIp(addr);

            // When an IPv4-mapped IPv6 address (::ffff:x.x.x.x) was unwrapped to plain
            // IPv4 notation, re-resolve to an Inet4Address so that loopback/private/link-local
            // checks (which inspect the InetAddress type) work correctly against the unwrapped
            // IPv4 rather than the original IPv6 object.
            InetAddress effectiveAddr = addr;
            if (addr instanceof Inet6Address && !normalizedIp.contains(":")) {
                try {
                    effectiveAddr = InetAddress.getByName(normalizedIp);
                } catch (UnknownHostException ignored) {
                    // Use original addr if re-resolution unexpectedly fails
                }
            }

            // Always block metadata IPs
            if (isCloudMetadataIp(normalizedIp, effectiveAddr)) {
                throw new IllegalArgumentException("URL resolves to cloud metadata IP: " + normalizedIp);
            }

            // Block localhost IPs
            if (!allowPrivate && isLocalhost(effectiveAddr)) {
                throw new IllegalArgumentException("URL resolves to localhost IP: " + normalizedIp);
            }

            // Block private IPs
            if (!allowPrivate && isPrivate(effectiveAddr)) {
                throw new IllegalArgumentException("URL resolves to private IP address: " + normalizedIp);
            }
        }

        return url;
    }

    /**
     * Returns {@code true} if the given URL passes all SSRF safety checks,
     * or {@code false} if it does not — without throwing an exception.
     *
     * <p>This is a convenience wrapper around {@link #validateSafeUrl} for
     * situations where you want a simple boolean decision (e.g. in a
     * filter, a UI pre-check, or a unit test) rather than exception handling.
     *
     * <p><b>This method never throws.</b> Any error — including a {@code null}
     * URL, a malformed URL, or a DNS failure — is silently treated as
     * {@code false} (unsafe).
     *
     * @param url          The URL to check. {@code null} and malformed values
     *                     return {@code false}.
     * @param allowPrivate {@code true} to permit private/loopback URLs
     *                     (still rejects cloud metadata endpoints).
     * @param allowHttp    {@code true} to permit plain {@code http://} URLs.
     * @return {@code true} if the URL is safe to fetch; {@code false} otherwise.
     *
     * @see #validateSafeUrl(String, boolean, boolean)
     */
    public static boolean isSafeUrl(String url, boolean allowPrivate, boolean allowHttp) {
        try {
            validateSafeUrl(url, allowPrivate, allowHttp);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // -----------------------------
    // Internal Helpers
    // -----------------------------

    /**
     * Parses a raw URL string into a structured {@link URI} object.
     *
     * <p>Using {@link URI} rather than {@link java.net.URL} is intentional:
     * {@code URL.equals()} performs a potentially blocking DNS lookup, and
     * {@code URL} automatically follows redirects in some contexts. {@code URI}
     * is a pure syntactic parser with no side effects.
     *
     * @throws IllegalArgumentException wrapping the underlying
     *         {@link java.net.URISyntaxException} if the string is not a
     *         valid URI (e.g. contains illegal characters like spaces or
     *         unencoded {@code <} / {@code >}).
     */
    private static URI parseUrl(String url) {
        try {
            return new URI(url);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid URL: " + url, e);
        }
    }

    /**
     * Ensures the URL uses an allowed scheme.
     *
     * <p>When {@code allowHttp} is {@code false}, plain {@code http://} is
     * also rejected. Enforcing HTTPS prevents credentials or sensitive
     * response data from being transmitted in clear text.
     *
     * @throws IllegalArgumentException if the scheme is absent, not
     *         {@code http}/{@code https}, or is {@code http} when only HTTPS
     *         is allowed.
     */
    private static void validateScheme(URI uri, boolean allowHttp) {
        String scheme = uri.getScheme();

        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("Only HTTP/HTTPS URLs are allowed");
        }

        if (!allowHttp && !scheme.equalsIgnoreCase("https")) {
            throw new IllegalArgumentException("Only HTTPS URLs are allowed");
        }
    }

    /**
     * Resolves a hostname to <em>all</em> of its IP addresses within the
     * {@link #DNS_TIMEOUT_MS} time limit.
     *
     * <p><b>Why use a timeout?</b> {@link InetAddress#getAllByName} is a blocking
     * call with no built-in timeout in the standard Java API. A slow or
     * adversarially crafted DNS server could stall the calling thread forever.
     * The resolution is run on a dedicated single-use thread and cancelled after
     * {@link #DNS_TIMEOUT_MS} milliseconds.
     *
     * @throws IllegalArgumentException if the hostname cannot be resolved,
     *         resolution times out, or the calling thread is interrupted.
     */
    private static InetAddress[] resolveAll(String host) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<InetAddress[]> future = executor.submit(() -> InetAddress.getAllByName(host));
            return future.get(DNS_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new IllegalArgumentException("DNS resolution timed out for hostname: " + host, e);
        } catch (ExecutionException e) {
            throw new IllegalArgumentException("Failed to resolve hostname: " + host, e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("DNS resolution interrupted for hostname: " + host, e);
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Normalizes an IP address into a canonical string form suitable for set lookup.
     *
     * <ul>
     *   <li>IPv4-mapped IPv6 addresses (::ffff:x.x.x.x) are unwrapped to their embedded
     *       IPv4 dotted-decimal form so they match entries in {@code CLOUD_METADATA_IPS}.</li>
     *   <li>Zone IDs (e.g. {@code fe80::1%eth0}) are stripped from the returned string.</li>
     * </ul>
     */
    private static String normalizeIp(InetAddress addr) {
        if (addr instanceof Inet6Address) {
            Inet6Address v6 = (Inet6Address) addr;
            // Unwrap IPv4-mapped IPv6: first 10 bytes = 0x00, bytes 10-11 = 0xff 0xff
            if (isIpv4MappedAddress(v6)) {
                byte[] raw = v6.getAddress();
                try {
                    InetAddress v4 = InetAddress.getByAddress(new byte[] {raw[12], raw[13], raw[14], raw[15]});
                    return v4.getHostAddress();
                } catch (UnknownHostException ignored) {
                    // Fall through to default handling
                }
            }
            // Strip zone ID (e.g. "fe80::1%2" -> "fe80::1")
            String ip = v6.getHostAddress();
            int zoneIdx = ip.indexOf('%');
            return zoneIdx >= 0 ? ip.substring(0, zoneIdx) : ip;
        }
        return addr.getHostAddress();
    }

    /** Returns true when {@code addr} is an IPv4-mapped IPv6 address (::ffff:x.x.x.x). */
    private static boolean isIpv4MappedAddress(Inet6Address addr) {
        byte[] b = addr.getAddress();
        for (int i = 0; i < 10; i++) {
            if (b[i] != 0) return false;
        }
        return b[10] == (byte) 0xff && b[11] == (byte) 0xff;
    }

    // -----------------------------
    // Classification Helpers
    // -----------------------------

    /**
     * Returns {@code true} if the address belongs to any private or
     * non-routable range that should not be reachable from the public internet.
     *
     * <p>Covered ranges:
     * <ul>
     *   <li><b>Site-local (RFC 1918)</b> — {@code 10.0.0.0/8},
     *       {@code 172.16.0.0/12}, {@code 192.168.0.0/16}: Standard private
     *       IPv4 ranges used in home and corporate networks.</li>
     *   <li><b>Link-local</b> — {@code 169.254.0.0/16} (IPv4) and
     *       {@code fe80::/10} (IPv6): Auto-assigned when no DHCP is available;
     *       also where cloud metadata services live.</li>
     *   <li><b>Any-local (wildcard)</b> — {@code 0.0.0.0} / {@code ::}:
     *       Represents "all interfaces"; meaningless as an outbound destination.</li>
     *   <li><b>IPv6 unique-local ({@code fc00::/7})</b> — The IPv6 counterpart
     *       of RFC 1918: {@code fc…} and {@code fd…} addresses that are
     *       private by convention. Java has no built-in API for this range;
     *       see {@link #isUniqueLocalAddress}.</li>
     * </ul>
     *
     * <p>Note: loopback addresses ({@code 127.x.x.x}, {@code ::1}) are
     * handled separately by {@link #isLocalhost(InetAddress)}.
     */
    private static boolean isPrivate(InetAddress addr) {
        return addr.isSiteLocalAddress() // 10/8, 172.16/12, 192.168/16
                || addr.isLinkLocalAddress() // 169.254/16, fe80::/10
                || addr.isAnyLocalAddress() // 0.0.0.0 / ::
                || isUniqueLocalAddress(addr); // fc00::/7 (includes fd00::)
    }

    /**
     * Returns true for IPv6 unique-local addresses (fc00::/7).
     *
     * <p>Java's {@link InetAddress} API has no built-in method for this range,
     * so we check the first byte: 0xfc or 0xfd.
     */
    private static boolean isUniqueLocalAddress(InetAddress addr) {
        if (!(addr instanceof Inet6Address)) return false;
        // fc00::/7 means the top 7 bits are 1111110x → first byte is 0xfc or 0xfd.
        // Use plain int literal 0xfc (= 252); casting to (byte) would give -4 (signed),
        // causing the comparison to always fail because (byte & 0xfe) yields an int.
        return (addr.getAddress()[0] & 0xfe) == 0xfc;
    }

    private static boolean isLocalhost(InetAddress addr) {
        return addr.isLoopbackAddress();
    }

    private static boolean isLocalhost(String host) {
        return LOCALHOST_NAMES.contains(host.toLowerCase());
    }

    /**
     * Returns {@code true} if the given hostname is a known cloud metadata
     * service name (case-insensitive).
     *
     * <p>This check happens <em>before</em> DNS resolution, so even if the
     * hostname were to resolve to a public IP in some edge case, it is still
     * rejected. See {@link #CLOUD_METADATA_HOSTNAMES} for the full list and
     * the rationale for each entry.
     */
    private static boolean isCloudMetadataHost(String host) {
        return CLOUD_METADATA_HOSTNAMES.contains(host.toLowerCase());
    }

    /**
     * Returns {@code true} if the given IP address is a known cloud metadata
     * service IP <em>or</em> falls within the entire link-local range as a
     * defence-in-depth measure.
     *
     * <p>Two layers of protection are applied:
     * <ol>
     *   <li><b>Known-IP list</b> — The {@code ip} string is checked against
     *       {@link #CLOUD_METADATA_IPS}. The string is already normalised
     *       (IPv4-mapped IPv6 unwrapped, zone IDs stripped) by
     *       {@link #normalizeIp} before this method is called.</li>
     *   <li><b>Full link-local block</b> — Even if a cloud provider introduces
     *       a new metadata endpoint IP that is not yet in our list, it will
     *       still be blocked because the entire {@code 169.254.0.0/16} (IPv4)
     *       and {@code fe80::/10} (IPv6) ranges are link-local and blocked
     *       by {@link InetAddress#isLinkLocalAddress()}.</li>
     * </ol>
     *
     * @param ip   Normalised IP string (output of {@link #normalizeIp}).
     * @param addr The original resolved address (used for range checks).
     */
    private static boolean isCloudMetadataIp(String ip, InetAddress addr) {
        if (CLOUD_METADATA_IPS.contains(ip)) {
            return true;
        }

        // Defence-in-depth: block the full link-local range so any future
        // metadata IPs we haven't hard-coded are still caught.
        return addr.isLinkLocalAddress();
    }
}
