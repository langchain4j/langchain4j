package dev.langchain4j.http.client;

import static dev.langchain4j.http.client.HttpMethod.GET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

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

    @Test
    void should_build_request_with_all_components() {
        // given
        HttpRequest request = HttpRequest.builder()
                .method(HttpMethod.POST)
                .url("https://api.example.com/v1/users")
                .addHeader("Content-Type", "application/json")
                .body("request body content")
                .build();

        // then
        assertThat(request.method()).isEqualTo(HttpMethod.POST);
        assertThat(request.url()).isEqualTo("https://api.example.com/v1/users");
        assertThat(request.headers()).containsEntry("Content-Type", List.of("application/json"));
        assertThat(request.body()).isEqualTo("request body content");
    }

    @Test
    void should_handle_urls_with_query_parameters() {
        // when
        String result = HttpRequest.builder()
                .method(GET)
                .url("https://api.example.com", "/search?q=test&limit=5")
                .build()
                .url();

        // then
        assertThat(result).isEqualTo("https://api.example.com/search?q=test&limit=5");
    }

    @Test
    void should_handle_null_body() {
        // when
        HttpRequest request = HttpRequest.builder()
                .method(HttpMethod.GET)
                .url("http://example.com")
                .body(null)
                .build();

        // then
        assertThat(request.body()).isNull();
    }

    @Test
    void should_throw_exception_when_method_is_null() {
        // when/then
        assertThatThrownBy(() -> HttpRequest.builder()
                        .method(null)
                        .url("http://example.com")
                        .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_add_single_query_parameter() {
        // given
        HttpRequest.Builder builder = HttpRequest.builder().method(GET).url("http://example.com/api");

        // when
        builder.addQueryParam("key", "value");

        // then
        assertThat(builder.build().url()).isEqualTo("http://example.com/api?key=value");
    }

    @Test
    void should_add_multiple_query_parameters() {
        // given
        HttpRequest.Builder builder = HttpRequest.builder().method(GET).url("http://example.com/api");

        // when
        builder.addQueryParam("key1", "value1").addQueryParam("key2", "value2");

        // then
        assertThat(builder.build().url()).isEqualTo("http://example.com/api?key1=value1&key2=value2");
    }

    @Test
    void should_add_query_parameters_from_map() {
        // given
        HttpRequest.Builder builder = HttpRequest.builder().method(GET).url("http://example.com/api");
        Map<String, String> queryParams = new LinkedHashMap<>();
        queryParams.put("key1", "value1");
        queryParams.put("key2", "value2");

        // when
        builder.addQueryParams(queryParams);

        // then
        assertThat(builder.build().url()).isEqualTo("http://example.com/api?key1=value1&key2=value2");
    }

    @Test
    void should_url_encode_query_parameters() {
        // given
        HttpRequest.Builder builder = HttpRequest.builder().method(GET).url("http://example.com/api");

        // when
        builder.addQueryParam("search", "hello world").addQueryParam("special", "a+b=c&d");

        // then
        assertThat(builder.build().url()).isEqualTo("http://example.com/api?search=hello+world&special=a%2Bb%3Dc%26d");
    }

    @Test
    void should_append_query_parameters_to_url_with_existing_query_string() {
        // given
        HttpRequest.Builder builder = HttpRequest.builder().method(GET).url("http://example.com/api?existing=param");

        // when
        builder.addQueryParam("new", "value");

        // then
        assertThat(builder.build().url()).isEqualTo("http://example.com/api?existing=param&new=value");
    }

    @Test
    void should_handle_null_query_params_map_with_add() {
        // given
        HttpRequest.Builder builder = HttpRequest.builder().method(GET).url("http://example.com/api");

        // when
        HttpRequest.Builder result = builder.addQueryParams(null);

        // then
        assertThat(result).isSameAs(builder);
        assertThat(builder.build().url()).isEqualTo("http://example.com/api");
    }

    @Test
    void should_handle_empty_query_params_map_with_add() {
        // given
        HttpRequest.Builder builder = HttpRequest.builder().method(GET).url("http://example.com/api");

        // when
        HttpRequest.Builder result = builder.addQueryParams(Map.of());

        // then
        assertThat(result).isSameAs(builder);
        assertThat(builder.build().url()).isEqualTo("http://example.com/api");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void should_throw_exception_when_query_param_name_is_null_or_blank(String name) {
        // given
        HttpRequest.Builder builder = HttpRequest.builder().method(GET).url("http://example.com");

        // when/then
        assertThatThrownBy(() -> builder.addQueryParam(name, "value"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("name cannot be null or blank");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void should_throw_exception_when_query_param_value_is_null_or_blank(String value) {
        // given
        HttpRequest.Builder builder = HttpRequest.builder().method(GET).url("http://example.com");

        // when/then
        assertThatThrownBy(() -> builder.addQueryParam("key", value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("value cannot be null or blank");
    }

    @Test
    void should_combine_query_parameters_from_both_methods() {
        // given
        HttpRequest.Builder builder = HttpRequest.builder().method(GET).url("http://example.com/api");
        Map<String, String> additionalParams = new LinkedHashMap<>();
        additionalParams.put("map1", "value2");
        additionalParams.put("map2", "value3");

        // when
        builder.addQueryParam("single", "value1").addQueryParams(additionalParams);

        // then
        assertThat(builder.build().url()).isEqualTo("http://example.com/api?single=value1&map1=value2&map2=value3");
    }

    @Test
    void should_overwrite_query_parameters_when_added_multiple_times() {
        // given
        HttpRequest.Builder builder = HttpRequest.builder().method(GET).url("http://example.com/api");
        Map<String, String> overwriteParams = new LinkedHashMap<>();
        overwriteParams.put("key", "value2");

        // when
        builder.addQueryParam("key", "value1").addQueryParams(overwriteParams);

        // then
        assertThat(builder.build().url()).isEqualTo("http://example.com/api?key=value2");
    }

    @Test
    void should_not_fail_when_adding_to_immutable_query_params() {
        // given
        HttpRequest.Builder builder = HttpRequest.builder().method(GET).url("http://example.com/api");

        // when
        builder.queryParams(Map.of("key1", "value1")).addQueryParam("key2", "value2");

        // then
        assertThat(builder.build().url()).isEqualTo("http://example.com/api?key1=value1&key2=value2");
    }

    @Test
    void should_replace_all_query_parameters_with_queryParams() {
        // given
        HttpRequest.Builder builder = HttpRequest.builder().method(GET).url("http://example.com/api");
        Map<String, String> newParams = new LinkedHashMap<>();
        newParams.put("new1", "newValue1");
        newParams.put("new2", "newValue2");

        // when
        builder.addQueryParam("old1", "oldValue1")
                .addQueryParam("old2", "oldValue2")
                .queryParams(newParams);

        // then
        assertThat(builder.build().url()).isEqualTo("http://example.com/api?new1=newValue1&new2=newValue2");
    }

    @Test
    void should_handle_null_query_params_map_with_replace() {
        // given
        HttpRequest.Builder builder = HttpRequest.builder().method(GET).url("http://example.com/api");

        // when
        builder.addQueryParam("key", "value").queryParams(null);

        // then
        assertThat(builder.build().url()).isEqualTo("http://example.com/api");
    }

    @Test
    void should_handle_empty_query_params_map_with_replace() {
        // given
        HttpRequest.Builder builder = HttpRequest.builder().method(GET).url("http://example.com/api");
        Map<String, String> emptyParams = new LinkedHashMap<>();

        // when
        builder.addQueryParam("key", "value").queryParams(emptyParams);

        // then
        assertThat(builder.build().url()).isEqualTo("http://example.com/api");
    }
}
