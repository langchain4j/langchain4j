package dev.langchain4j.http.client;

import static dev.langchain4j.http.client.HttpMethod.GET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class HttpRequestTest {

    @ParameterizedTest
    @MethodSource("validUrlCombinations")
    void should_correctly_concatenate_baseUrl_and_path(String baseUrl, String path, String expectedUrl) {

        // when
        String result =
                HttpRequest.builder().method(GET).url(baseUrl, path).build().url();

        // then
        assertThat(result).isEqualTo(expectedUrl);
    }

    private static Stream<Arguments> validUrlCombinations() {
        return Stream.of(
                Arguments.of("http://example.com", "/api", "http://example.com/api"),
                Arguments.of("http://example.com/", "/api", "http://example.com/api"),
                Arguments.of("http://example.com", "api", "http://example.com/api"),
                Arguments.of("http://example.com/", "api", "http://example.com/api"),
                Arguments.of("http://example.com/v1", "/api", "http://example.com/v1/api"),
                Arguments.of("http://example.com/v1/", "/api", "http://example.com/v1/api"));
    }

    @Test
    void should_throw_exception_when_baseUrl_is_null_or_blank() {

        assertThatThrownBy(() -> HttpRequest.builder().url(null, "/api"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("baseUrl cannot be null or blank");
        assertThatThrownBy(() -> HttpRequest.builder().url("", "/api"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("baseUrl cannot be null or blank");
        assertThatThrownBy(() -> HttpRequest.builder().url(" ", "/api"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("baseUrl cannot be null or blank");
    }

    @Test
    void should_throw_exception_when_path_is_null_or_blank() {

        assertThatThrownBy(() -> HttpRequest.builder().url("http://example.com", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("path cannot be null or blank");
        assertThatThrownBy(() -> HttpRequest.builder().url("http://example.com", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("path cannot be null or blank");
        assertThatThrownBy(() -> HttpRequest.builder().url("http://example.com", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("path cannot be null or blank");
    }

    @Test
    void should_add_single_header() {

        // given
        HttpRequest.Builder builder = HttpRequest.builder().method(GET).url("http://example.com");

        // when
        builder.addHeader("Content-Type", "application/json");

        // then
        assertThat(builder.build().headers()).containsEntry("Content-Type", List.of("application/json"));
    }

    @Test
    void should_add_multiple_values_for_single_header() {

        // given
        HttpRequest.Builder builder = HttpRequest.builder().method(GET).url("http://example.com");

        // when
        builder.addHeader("Accept", "application/json", "application/xml");

        // then
        assertThat(builder.build().headers()).containsEntry("Accept", List.of("application/json", "application/xml"));
    }

    @Test
    void should_throw_exception_when_header_name_is_null_or_blank() {

        // given
        HttpRequest.Builder builder = HttpRequest.builder().method(GET).url("http://example.com");

        // when/then
        assertThatThrownBy(() -> builder.addHeader(null, "value"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("name cannot be null or blank");
        assertThatThrownBy(() -> builder.addHeader("", "value"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("name cannot be null or blank");
        assertThatThrownBy(() -> builder.addHeader(" ", "value"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("name cannot be null or blank");
    }

    @Test
    void should_add_headers_from_map() {

        // given
        HttpRequest.Builder builder = HttpRequest.builder().method(GET).url("http://example.com");
        Map<String, String> headers = Map.of(
                "Content-Type", "application/json",
                "Accept", "text/plain");

        // when
        builder.addHeaders(headers);

        // then
        assertThat(builder.build().headers())
                .containsEntry("Content-Type", List.of("application/json"))
                .containsEntry("Accept", List.of("text/plain"));
    }

    @Test
    void should_handle_null_headers_map() {

        // given
        HttpRequest.Builder builder = HttpRequest.builder().method(GET).url("http://example.com");

        // when
        HttpRequest.Builder result = builder.addHeaders(null);

        // then
        assertThat(result).isSameAs(builder);
        assertThat(builder.build().headers()).isEmpty();
    }

    @Test
    void should_handle_empty_headers_map() {

        // given
        HttpRequest.Builder builder = HttpRequest.builder().method(GET).url("http://example.com");

        // when
        HttpRequest.Builder result = builder.addHeaders(Map.of());

        // then
        assertThat(result).isSameAs(builder);
        assertThat(builder.build().headers()).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("headerCombinations")
    void shouldCombineHeadersFromBothMethods(
            String singleHeaderName,
            String[] singleHeaderValues,
            Map<String, String> mapHeaders,
            Map<String, List<String>> expectedHeaders) {

        // given
        HttpRequest.Builder builder = HttpRequest.builder().method(GET).url("http://example.com");

        // when
        builder.addHeader(singleHeaderName, singleHeaderValues).addHeaders(mapHeaders);

        // then
        assertThat(builder.build().headers()).containsExactlyInAnyOrderEntriesOf(expectedHeaders);
    }

    private static Stream<Arguments> headerCombinations() {
        return Stream.of(
                Arguments.of(
                        "Content-Type",
                        new String[] {"application/json"},
                        Map.of("Accept", "text/plain"),
                        Map.of(
                                "Content-Type", List.of("application/json"),
                                "Accept", List.of("text/plain"))),
                Arguments.of(
                        "Accept",
                        new String[] {"application/json", "application/xml"},
                        Map.of("Content-Type", "application/json"),
                        Map.of(
                                "Accept", List.of("application/json", "application/xml"),
                                "Content-Type", List.of("application/json"))),
                Arguments.of(
                        "X-Custom-Header",
                        new String[] {"value1"},
                        Map.of(
                                "X-Custom-Header", "value2",
                                "Accept", "application/json"),
                        Map.of(
                                "X-Custom-Header", List.of("value2"),
                                "Accept", List.of("application/json"))));
    }

    @Test
    void should_overwrite_headers_when_added_multiple_times() {

        // given
        HttpRequest.Builder builder = HttpRequest.builder().method(GET).url("http://example.com");

        // when
        builder.addHeader("Accept", "application/json").addHeaders(Map.of("Accept", "text/plain"));

        // then
        assertThat(builder.build().headers()).containsEntry("Accept", List.of("text/plain"));
    }
}
