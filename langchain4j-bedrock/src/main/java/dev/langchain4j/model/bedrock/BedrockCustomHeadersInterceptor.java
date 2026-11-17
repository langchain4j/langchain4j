package dev.langchain4j.model.bedrock;

import static java.util.stream.Collectors.toSet;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import software.amazon.awssdk.core.SdkRequest;
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;
import software.amazon.awssdk.http.SdkHttpRequest;

@dev.langchain4j.Internal
class BedrockCustomHeadersInterceptor implements ExecutionInterceptor {

    private final Supplier<Map<String, String>> customHeadersSupplier;

    BedrockCustomHeadersInterceptor(Supplier<Map<String, String>> customHeadersSupplier) {
        this.customHeadersSupplier = customHeadersSupplier;
    }

    @Override
    public SdkHttpRequest modifyHttpRequest(
            Context.ModifyHttpRequest context, ExecutionAttributes executionAttributes) {
        Map<String, String> headers = customHeadersSupplier.get();
        if (headers == null || headers.isEmpty()) {
            return context.httpRequest();
        }
        Set<String> perRequestHeaderNames = perRequestHeaderNames(context.request());
        SdkHttpRequest.Builder builder = context.httpRequest().toBuilder();
        headers.forEach((name, value) -> {
            if (!perRequestHeaderNames.contains(name.toLowerCase(Locale.ROOT))) {
                builder.appendHeader(name, value);
            }
        });
        return builder.build();
    }

    /**
     * Names (lower case) of the per-request custom headers, which the SDK adds to the request later.
     * They win over the headers of the model builder with the same name.
     */
    private static Set<String> perRequestHeaderNames(SdkRequest request) {
        return request.overrideConfiguration()
                .map(config -> config.headers().keySet().stream()
                        .map(name -> name.toLowerCase(Locale.ROOT))
                        .collect(toSet()))
                .orElse(Set.of());
    }
}
