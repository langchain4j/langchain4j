package dev.langchain4j.http.client;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SuccessfulHttpResponseCharsetTest {

    @ParameterizedTest
    @ValueSource(
            strings = {
                "text/plain; note=\"a;charset=UTF-8;\"; charset=ISO-8859-1",
                "text/plain; note=\"a;CHARSET=UTF-8;\"; charset=\"ISO-8859-1\"",
                "text/plain; note=\"a\\\";charset=UTF-8;\"; charset=ISO-8859-1",
                "text/plain; note=\"a\\\\\"; charset=ISO-8859-1"
            })
    void should_ignore_charset_text_inside_other_quoted_parameters(String contentType) {
        byte[] bytes = "caf\u00e9".getBytes(ISO_8859_1);
        SuccessfulHttpResponse response = SuccessfulHttpResponse.builder()
                .statusCode(200)
                .headers(Map.of("Content-Type", List.of(contentType)))
                .body(bytes)
                .build();

        assertThat(response.body()).isEqualTo("caf\u00e9");
        assertThat(response.bodyBytes()).isEqualTo(bytes);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"text/plain; note=\"a;charset=ISO-8859-1;\"", "text/plain; note=\"a\\\";charset=ISO-8859-1;\""})
    void should_use_utf8_when_charset_only_appears_inside_a_quoted_parameter(String contentType) {
        SuccessfulHttpResponse response = SuccessfulHttpResponse.builder()
                .statusCode(200)
                .headers(Map.of("Content-Type", List.of(contentType)))
                .body("caf\u00e9".getBytes(UTF_8))
                .build();

        assertThat(response.body()).isEqualTo("caf\u00e9");
    }
}
