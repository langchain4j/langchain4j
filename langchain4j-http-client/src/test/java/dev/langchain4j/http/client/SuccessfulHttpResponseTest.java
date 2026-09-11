package dev.langchain4j.http.client;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SuccessfulHttpResponseTest {

    @Test
    void should_decode_body_using_charset_from_content_type_header() {
        // 'é' is 0xE9 in ISO-8859-1 but 0xC3 0xA9 in UTF-8
        byte[] latin1Bytes = "café".getBytes(ISO_8859_1);

        SuccessfulHttpResponse response = SuccessfulHttpResponse.builder()
                .statusCode(200)
                .headers(Map.of("Content-Type", List.of("text/plain; charset=ISO-8859-1")))
                .body(latin1Bytes)
                .build();

        assertThat(response.body()).isEqualTo("café");
        assertThat(response.bodyBytes()).isEqualTo(latin1Bytes);
    }

    @Test
    void should_match_content_type_header_case_insensitively() {
        byte[] latin1Bytes = "café".getBytes(ISO_8859_1);

        SuccessfulHttpResponse response = SuccessfulHttpResponse.builder()
                .statusCode(200)
                .headers(Map.of("content-type", List.of("text/plain; CHARSET=iso-8859-1")))
                .body(latin1Bytes)
                .build();

        assertThat(response.body()).isEqualTo("café");
    }

    @Test
    void should_default_to_utf8_when_charset_param_is_absent() {
        byte[] utf8Bytes = "café".getBytes(UTF_8);

        SuccessfulHttpResponse response = SuccessfulHttpResponse.builder()
                .statusCode(200)
                .headers(Map.of("Content-Type", List.of("application/json")))
                .body(utf8Bytes)
                .build();

        assertThat(response.body()).isEqualTo("café");
    }

    @Test
    void should_default_to_utf8_when_content_type_header_is_absent() {
        byte[] utf8Bytes = "café".getBytes(UTF_8);

        SuccessfulHttpResponse response = SuccessfulHttpResponse.builder()
                .statusCode(200)
                .headers(Map.of())
                .body(utf8Bytes)
                .build();

        assertThat(response.body()).isEqualTo("café");
    }

    @Test
    void should_fall_back_to_utf8_when_charset_is_unsupported() {
        byte[] utf8Bytes = "café".getBytes(UTF_8);

        SuccessfulHttpResponse response = SuccessfulHttpResponse.builder()
                .statusCode(200)
                .headers(Map.of("Content-Type", List.of("text/plain; charset=not-a-real-charset")))
                .body(utf8Bytes)
                .build();

        assertThat(response.body()).isEqualTo("café");
    }

    @Test
    void should_return_null_body_when_not_set() {
        SuccessfulHttpResponse response =
                SuccessfulHttpResponse.builder().statusCode(200).build();

        assertThat(response.body()).isNull();
        assertThat(response.bodyBytes()).isNull();
    }

    @Test
    void should_look_up_headers_case_insensitively() {
        SuccessfulHttpResponse response = SuccessfulHttpResponse.builder()
                .statusCode(200)
                .headers(Map.of("content-type", List.of("application/json")))
                .build();

        assertThat(response.headers().get("Content-Type")).containsExactly("application/json");
        assertThat(response.headers().get("CONTENT-TYPE")).containsExactly("application/json");
        assertThat(response.headers().containsKey("Content-Type")).isTrue();
    }

    @Test
    void should_not_be_affected_by_mutations_of_the_source_headers() {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("Content-Type", List.of("application/json"));

        SuccessfulHttpResponse response = SuccessfulHttpResponse.builder()
                .statusCode(200)
                .headers(headers)
                .build();

        headers.put("X-Added-Later", List.of("value"));

        assertThat(response.headers()).containsOnlyKeys("Content-Type");
    }

    @Test
    void should_return_unmodifiable_headers() {
        SuccessfulHttpResponse response = SuccessfulHttpResponse.builder()
                .statusCode(200)
                .headers(Map.of("Content-Type", List.of("application/json")))
                .build();

        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> response.headers().put("X-Added-Later", List.of("value")));
    }

    @Test
    void should_return_empty_headers_when_not_set() {
        SuccessfulHttpResponse response =
                SuccessfulHttpResponse.builder().statusCode(200).build();

        assertThat(response.headers()).isEmpty();
    }
}
