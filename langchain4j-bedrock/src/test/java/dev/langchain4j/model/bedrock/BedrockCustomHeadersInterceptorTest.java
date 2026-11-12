package dev.langchain4j.model.bedrock;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.SdkRequest;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;

class BedrockCustomHeadersInterceptorTest {

    private static final SdkHttpFullRequest BASE_REQUEST = SdkHttpFullRequest.builder()
            .method(SdkHttpMethod.POST)
            .uri(URI.create("https://bedrock-runtime.us-east-1.amazonaws.com/test"))
            .build();

    private static final ExecutionAttributes EMPTY_ATTRS = new ExecutionAttributes();

    private SdkHttpRequest invoke(BedrockCustomHeadersInterceptor interceptor, SdkHttpFullRequest request) {
        return interceptor.modifyHttpRequest(
                contextFor(ConverseRequest.builder().build(), request), EMPTY_ATTRS);
    }

    private Context.ModifyHttpRequest contextFor(SdkRequest sdkRequest, SdkHttpFullRequest req) {
        return new Context.ModifyHttpRequest() {
            @Override
            public SdkRequest request() {
                return sdkRequest;
            }

            @Override
            public SdkHttpRequest httpRequest() {
                return req;
            }

            @Override
            public Optional<RequestBody> requestBody() {
                return Optional.empty();
            }

            @Override
            public Optional<AsyncRequestBody> asyncRequestBody() {
                return Optional.empty();
            }
        };
    }

    @Test
    void should_add_custom_headers() {
        BedrockCustomHeadersInterceptor interceptor =
                new BedrockCustomHeadersInterceptor(() -> Map.of("X-Custom-Header", "my-value"));

        SdkHttpRequest result = invoke(interceptor, BASE_REQUEST);

        assertThat(result.headers()).containsKey("X-Custom-Header");
        assertThat(result.headers().get("X-Custom-Header")).containsExactly("my-value");
    }

    @Test
    void should_call_supplier_per_request() {
        AtomicInteger counter = new AtomicInteger(0);
        Supplier<Map<String, String>> supplier = () -> Map.of("X-Count", String.valueOf(counter.incrementAndGet()));
        BedrockCustomHeadersInterceptor interceptor = new BedrockCustomHeadersInterceptor(supplier);

        SdkHttpRequest first = invoke(interceptor, BASE_REQUEST);
        SdkHttpRequest second = invoke(interceptor, BASE_REQUEST);

        assertThat(first.headers().get("X-Count")).containsExactly("1");
        assertThat(second.headers().get("X-Count")).containsExactly("2");
    }

    @Test
    void should_handle_null_from_supplier() {
        BedrockCustomHeadersInterceptor interceptor = new BedrockCustomHeadersInterceptor(() -> null);

        SdkHttpRequest result = invoke(interceptor, BASE_REQUEST);

        assertThat(result).isSameAs(BASE_REQUEST);
    }

    @Test
    void should_handle_empty_map() {
        BedrockCustomHeadersInterceptor interceptor = new BedrockCustomHeadersInterceptor(Map::of);

        SdkHttpRequest result = invoke(interceptor, BASE_REQUEST);

        assertThat(result).isSameAs(BASE_REQUEST);
    }

    @Test
    void should_add_multiple_custom_headers() {
        BedrockCustomHeadersInterceptor interceptor =
                new BedrockCustomHeadersInterceptor(() -> Map.of("X-Header-A", "value-a", "X-Header-B", "value-b"));

        SdkHttpRequest result = invoke(interceptor, BASE_REQUEST);

        assertThat(result.headers()).containsKey("X-Header-A");
        assertThat(result.headers()).containsKey("X-Header-B");
        assertThat(result.headers().get("X-Header-A")).containsExactly("value-a");
        assertThat(result.headers().get("X-Header-B")).containsExactly("value-b");
    }

    @Test
    void should_not_add_custom_headers_that_are_overridden_per_request_ignoring_case() {
        BedrockCustomHeadersInterceptor interceptor =
                new BedrockCustomHeadersInterceptor(() -> Map.of("X-Shared", "model", "X-Model-Header", "model"));
        ConverseRequest perRequest = ConverseRequest.builder()
                .overrideConfiguration(config -> config.putHeader("x-shared", "request"))
                .build();

        SdkHttpRequest result = interceptor.modifyHttpRequest(contextFor(perRequest, BASE_REQUEST), EMPTY_ATTRS);

        assertThat(result.headers()).doesNotContainKey("X-Shared");
        assertThat(result.headers().get("X-Model-Header")).containsExactly("model");
    }
}

