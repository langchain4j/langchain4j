package dev.langchain4j.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HttpParametersTest {

    @Test
    void should_have_no_custom_headers_by_default() {

        HttpParameters httpParameters = HttpParameters.builder().build();

        assertThat(httpParameters.customHeaders()).isEmpty();
    }

    @Test
    void should_copy_custom_headers() {

        Map<String, String> headers = new HashMap<>();
        headers.put("X-Correlation-Id", "123");

        HttpParameters httpParameters =
                HttpParameters.builder().customHeaders(headers).build();
        headers.put("X-Other", "456");

        assertThat(httpParameters.customHeaders()).containsExactly(entry("X-Correlation-Id", "123"));
    }

    @Test
    void to_string_should_not_contain_header_values() {

        HttpParameters httpParameters = HttpParameters.builder()
                .customHeaders(Map.of("Authorization", "Bearer secret-token"))
                .build();

        assertThat(httpParameters.toString()).contains("Authorization").doesNotContain("secret-token");
    }

    @Test
    void should_implement_equals_and_hash_code() {

        HttpParameters first =
                HttpParameters.builder().customHeaders(Map.of("X-A", "1")).build();
        HttpParameters second =
                HttpParameters.builder().customHeaders(Map.of("X-A", "1")).build();
        HttpParameters third =
                HttpParameters.builder().customHeaders(Map.of("X-A", "2")).build();

        assertThat(first).isEqualTo(second).hasSameHashCodeAs(second).isNotEqualTo(third);
    }

    @Test
    void should_reject_null_header_value_naming_the_header() {

        Map<String, String> headers = new HashMap<>();
        headers.put("X-Tenant-Id", null);

        assertThatThrownBy(() -> HttpParameters.builder().customHeaders(headers).build())
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("X-Tenant-Id");
    }

    @Test
    void should_reject_null_or_blank_header_name() {

        Map<String, String> nullName = new HashMap<>();
        nullName.put(null, "value");

        assertThatThrownBy(() -> HttpParameters.builder().customHeaders(nullName).build())
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("custom header name");
        assertThatThrownBy(() -> HttpParameters.builder()
                        .customHeaders(Map.of(" ", "value"))
                        .build())
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("custom header name");
    }

    @Test
    void should_reject_same_header_name_with_different_case() {

        assertThatThrownBy(() -> HttpParameters.builder()
                        .customHeaders(Map.of("X-Tenant-Id", "a", "x-tenant-id", "b"))
                        .build())
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("more than once");
    }

    @Test
    void should_accept_null_custom_headers() {
        assertThat(HttpParameters.builder().customHeaders(null).build().customHeaders())
                .isEmpty();
    }
}

