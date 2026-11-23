package dev.langchain4j.data.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HostPatternsTest {

    @Test
    void should_match_exact_hosts_and_subdomains_of_wildcards() {
        HostPatterns patterns = HostPatterns.from(List.of("Media.Example.com", "*.CDN.example.com."), "allowedHosts");

        assertThat(patterns.matches("media.example.com")).isTrue();
        assertThat(patterns.matches("a.cdn.example.com")).isTrue();
        assertThat(patterns.matches("a.b.cdn.example.com")).isTrue();

        assertThat(patterns.matches("cdn.example.com")).isFalse();
        assertThat(patterns.matches("acdn.example.com")).isFalse();
        assertThat(patterns.matches("example.com")).isFalse();
        assertThat(patterns.matches("other.media.example.com")).isFalse();
        assertThat(patterns.matches("media.example.com.evil.com")).isFalse();
    }

    @Test
    void should_match_ip_addresses_with_and_without_brackets() {
        HostPatterns patterns = HostPatterns.from(List.of("192.168.1.10", "[::1]", "fd00::5"), "allowNonPublicHosts");

        assertThat(patterns.matches(HostPatterns.normalize("192.168.1.10"))).isTrue();
        assertThat(patterns.matches(HostPatterns.normalize("[::1]"))).isTrue();
        assertThat(patterns.matches(HostPatterns.normalize("[FD00::5]"))).isTrue();
        assertThat(patterns.matches(HostPatterns.normalize("192.168.1.11"))).isFalse();
    }

    @Test
    void should_normalize_hosts() {
        assertThat(HostPatterns.normalize("MEDIA.Example.COM.")).isEqualTo("media.example.com");
        assertThat(HostPatterns.normalize("[::1]")).isEqualTo("::1");
    }

    @Test
    void none_should_match_nothing() {
        assertThat(HostPatterns.none().matches("localhost")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                " ",
                ".",
                "*",
                "*.",
                "a*.example.com",
                "*.*.example.com",
                "https://example.com",
                "example.com/images",
                "example.com:8080",
                "user@example.com",
                "exa mple.com",
                " example.com"
            })
    void should_reject_invalid_patterns(String pattern) {
        assertThatThrownBy(() -> HostPatterns.from(List.of(pattern), "allowedHosts"))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allowedHosts must contain host names such as");
    }

    @Test
    void should_reject_null_entries() {
        assertThatThrownBy(() -> HostPatterns.from(Arrays.asList("example.com", null), "allowNonPublicHosts"))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("allowNonPublicHosts must not contain null");
    }
}
