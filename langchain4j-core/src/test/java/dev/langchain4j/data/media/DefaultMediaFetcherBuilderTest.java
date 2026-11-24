package dev.langchain4j.data.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DefaultMediaFetcherBuilderTest {

    @Test
    void should_build_with_defaults() {
        assertThat(DefaultMediaFetcher.builder().build()).isNotNull();
        assertThat(DefaultMediaFetcher.builder()
                        .allowedHosts((List<String>) null)
                        .allowedHosts((String[]) null)
                        .allowNonPublicHosts((List<String>) null)
                        .allowNonPublicHosts((String[]) null)
                        .connectTimeout(null)
                        .timeout(null)
                        .maxSizeInBytes(null)
                        .maxRedirects(null)
                        .httpClientBuilder(null)
                        .build())
                .isNotNull();
    }

    @Test
    void should_accept_valid_options() {
        assertThat(DefaultMediaFetcher.builder()
                        .allowedHosts(List.of("images.example.com", "*.cdn.example.com"))
                        .allowNonPublicHosts(List.of())
                        .connectTimeout(Duration.ofMillis(1))
                        .timeout(Duration.ofMillis(1))
                        .maxSizeInBytes(1)
                        .maxRedirects(0)
                        .build())
                .isNotNull();
    }

    @Test
    void should_reject_an_empty_allow_list() {
        assertThatThrownBy(() -> DefaultMediaFetcher.builder().allowedHosts(List.of()).build())
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("allowedHosts must not be empty. Set it to null to allow every host");
        assertThatThrownBy(() -> DefaultMediaFetcher.builder().allowedHosts().build())
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("allowedHosts must not be empty");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "*", "https://images.example.com", "images.example.com:443", "[::1]:8080", "[::1"})
    void should_reject_invalid_host_names(String host) {
        assertThatThrownBy(() -> DefaultMediaFetcher.builder().allowedHosts(host).build())
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("allowedHosts must contain host names");
        assertThatThrownBy(() ->
                        DefaultMediaFetcher.builder().allowNonPublicHosts(host).build())
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("allowNonPublicHosts must contain host names");
    }

    @Test
    void should_reject_invalid_thresholds() {
        assertThatThrownBy(() -> DefaultMediaFetcher.builder()
                        .connectTimeout(Duration.ZERO)
                        .build())
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("connectTimeout must be positive, but is: PT0S");
        assertThatThrownBy(() -> DefaultMediaFetcher.builder()
                        .timeout(Duration.ofSeconds(-1))
                        .build())
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("timeout must be positive, but is: PT-1S");
        assertThatThrownBy(() -> DefaultMediaFetcher.builder().maxSizeInBytes(0).build())
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("maxSizeInBytes must be greater than zero, but is: 0");
        assertThatThrownBy(() -> DefaultMediaFetcher.builder().maxRedirects(-1).build())
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("maxRedirects must not be negative, but is: -1");
    }

    @Test
    void should_reject_timeouts_that_are_too_long() {
        Duration longest = Duration.ofNanos(Long.MAX_VALUE);

        assertThatThrownBy(() -> DefaultMediaFetcher.builder()
                        .connectTimeout(Duration.ofMillis(Long.MAX_VALUE))
                        .build())
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("connectTimeout must not be longer than " + longest + " (about 292 years), but is: "
                        + Duration.ofMillis(Long.MAX_VALUE));
        assertThatThrownBy(() -> DefaultMediaFetcher.builder()
                        .timeout(Duration.ofSeconds(Long.MAX_VALUE))
                        .build())
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("timeout must not be longer than " + longest + " (about 292 years), but is: "
                        + Duration.ofSeconds(Long.MAX_VALUE));
        assertThatThrownBy(() -> DefaultMediaFetcher.builder()
                        .timeout(longest.plusNanos(1))
                        .build())
                .isExactlyInstanceOf(IllegalArgumentException.class);

        assertThat(DefaultMediaFetcher.builder()
                        .connectTimeout(longest)
                        .timeout(longest)
                        .build())
                .isNotNull();
    }
}
