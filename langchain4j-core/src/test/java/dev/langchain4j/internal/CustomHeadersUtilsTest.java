package dev.langchain4j.internal;

import static dev.langchain4j.internal.CustomHeadersUtils.customHeadersOf;
import static dev.langchain4j.internal.CustomHeadersUtils.mergeCustomHeaders;
import static dev.langchain4j.internal.CustomHeadersUtils.mergeHttpParameters;
import static dev.langchain4j.internal.CustomHeadersUtils.perRequestHeaders;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.HttpParameters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.Test;

class CustomHeadersUtilsTest {

    @Test
    void should_return_empty_map_when_both_are_null() {
        assertThat(mergeCustomHeaders(null, null)).isEmpty();
    }

    @Test
    void should_return_headers_when_overrides_are_null() {
        assertThat(mergeCustomHeaders(Map.of("X-A", "1"), null)).containsOnly(entry("X-A", "1"));
    }

    @Test
    void should_return_overrides_when_headers_are_null() {
        assertThat(mergeCustomHeaders(null, Map.of("X-A", "1"))).containsOnly(entry("X-A", "1"));
    }

    @Test
    void overrides_should_win_ignoring_case_and_keep_their_spelling() {

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer model-key");
        headers.put("User-Agent", "LangChain4j");
        headers.put("X-Keep", "keep");

        Map<String, String> overrides = new LinkedHashMap<>();
        overrides.put("authorization", "Bearer request-key");
        overrides.put("X-New", "new");

        Map<String, String> merged = mergeCustomHeaders(headers, overrides);

        assertThat(merged)
                .containsExactly(
                        entry("User-Agent", "LangChain4j"),
                        entry("X-Keep", "keep"),
                        entry("authorization", "Bearer request-key"),
                        entry("X-New", "new"));
    }

    @Test
    void custom_headers_of_should_return_empty_map_when_nothing_is_set() {
        assertThat(customHeadersOf(ChatRequestParameters.builder().build())).isEmpty();
        assertThat(customHeadersOf(ChatRequestParameters.builder()
                        .httpParameters(HttpParameters.builder().build())
                        .build()))
                .isEmpty();
    }

    @Test
    void custom_headers_of_should_return_headers_of_http_parameters() {

        ChatRequestParameters parameters = ChatRequestParameters.builder()
                .httpParameters(HttpParameters.builder()
                        .customHeaders(Map.of("X-Correlation-Id", "123"))
                        .build())
                .build();

        assertThat(customHeadersOf(parameters)).containsOnly(entry("X-Correlation-Id", "123"));
    }

    @Test
    void per_request_custom_headers_not_supported_should_name_the_client() {
        assertThat(CustomHeadersUtils.perRequestCustomHeadersNotSupported(String.class))
                .isExactlyInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("java.lang.String")
                .hasMessageContaining("per-request custom HTTP headers");
    }

    @Test
    void failing_publisher_should_signal_on_subscribe_then_on_error() {

        UnsupportedFeatureException error = new UnsupportedFeatureException("not supported");
        List<String> signals = new ArrayList<>();

        AsyncNotSupported.<String>failingPublisher(error).subscribe(new Flow.Subscriber<>() {

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                signals.add("onSubscribe");
            }

            @Override
            public void onNext(String item) {
                signals.add("onNext");
            }

            @Override
            public void onError(Throwable throwable) {
                signals.add(throwable == error ? "onError" : "onError(other)");
            }

            @Override
            public void onComplete() {
                signals.add("onComplete");
            }
        });

        assertThat(signals).containsExactly("onSubscribe", "onError");
    }

    @Test
    void merge_http_parameters_should_merge_custom_headers_by_name_ignoring_case() {

        HttpParameters current = HttpParameters.builder()
                .customHeaders(Map.of("X-Tenant-Id", "tenant-1", "User-Agent", "LangChain4j"))
                .build();
        HttpParameters overrides = HttpParameters.builder()
                .customHeaders(Map.of("user-agent", "my-app", "X-Correlation-Id", "123"))
                .build();

        HttpParameters merged = mergeHttpParameters(current, overrides);

        assertThat(merged.customHeaders())
                .containsOnly(
                        entry("X-Tenant-Id", "tenant-1"),
                        entry("user-agent", "my-app"),
                        entry("X-Correlation-Id", "123"));
        assertThat(current.customHeaders()).containsEntry("User-Agent", "LangChain4j");
    }

    @Test
    void merge_http_parameters_should_return_the_other_when_one_is_null() {

        HttpParameters httpParameters =
                HttpParameters.builder().customHeaders(Map.of("X-A", "1")).build();

        assertThat(mergeHttpParameters(httpParameters, null)).isSameAs(httpParameters);
        assertThat(mergeHttpParameters(null, httpParameters)).isSameAs(httpParameters);
        assertThat(mergeHttpParameters(null, null)).isNull();
    }

    @Test
    void per_request_headers_should_replace_model_headers_but_append_to_user_agent() {

        Map<String, String> modelHeaders = Map.of(
                "User-Agent", "langchain4j-openai", "Authorization", "Bearer model-key", "X-Model", "m");
        Map<String, String> requestHeaders = new LinkedHashMap<>();
        requestHeaders.put("user-agent", "google-adk/1.2.0 gl-java/21");
        requestHeaders.put("authorization", "Bearer request-key");
        requestHeaders.put("X-Correlation-Id", "123");

        assertThat(perRequestHeaders(modelHeaders, requestHeaders))
                .containsExactly(
                        entry("user-agent", "langchain4j-openai google-adk/1.2.0 gl-java/21"),
                        entry("authorization", "Bearer request-key"),
                        entry("X-Correlation-Id", "123"));
    }

    @Test
    void per_request_user_agent_should_be_used_as_is_when_model_sends_none() {
        assertThat(perRequestHeaders(Map.of(), Map.of("User-Agent", "my-app/1.0")))
                .containsExactly(entry("User-Agent", "my-app/1.0"));
    }

    @Test
    void per_request_headers_should_append_to_additional_header_names() {

        Map<String, String> modelHeaders = Map.of("x-goog-api-client", "gl-java/25", "X-Other", "model");
        Map<String, String> requestHeaders = new LinkedHashMap<>();
        requestHeaders.put("X-Goog-Api-Client", "google-adk/1.2.0");
        requestHeaders.put("x-other", "request");

        assertThat(perRequestHeaders(modelHeaders, requestHeaders, Set.of("x-goog-api-client")))
                .containsExactly(
                        entry("X-Goog-Api-Client", "gl-java/25 google-adk/1.2.0"),
                        entry("x-other", "request"));
    }

    @Test
    void custom_headers_of_http_parameters_should_be_null_safe() {
        assertThat(customHeadersOf((HttpParameters) null)).isEmpty();
        assertThat(customHeadersOf(HttpParameters.builder().customHeaders(Map.of("X-A", "1")).build()))
                .containsOnly(entry("X-A", "1"));
    }
}

