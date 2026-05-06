package dev.langchain4j.model.bedrock;

import java.util.Map;
import java.util.function.Supplier;
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
        SdkHttpRequest.Builder builder = context.httpRequest().toBuilder();
        headers.forEach(builder::appendHeader);
        return builder.build();
    }
}
