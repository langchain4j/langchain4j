package dev.langchain4j.micrometer.metrics.listeners;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.micrometer.metrics.conventions.OTelGenAiAttributes;
import dev.langchain4j.micrometer.metrics.conventions.OTelGenAiMetricName;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.HashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MicrometerMetricsChatModelListenerTest {

    MicrometerMetricsChatModelListener listener;
    MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        listener = new MicrometerMetricsChatModelListener(meterRegistry);
    }

    @Test
    void should_record_provider_name_when_model_provider_is_present() {
        ChatModelResponseContext responseContext = createResponseContext(ModelProvider.MICROSOFT_FOUNDRY);

        listener.onResponse(responseContext);

        assertThat(meterRegistry
                        .find(OTelGenAiMetricName.TOKEN_USAGE.value())
                        .tag(OTelGenAiAttributes.PROVIDER_NAME.value(), "azure.ai.inference")
                        .meter())
                .isNotNull();
    }

    @Test
    void should_record_unknown_provider_name_when_model_provider_is_null() {
        ChatModelResponseContext responseContext = createResponseContext(null);

        listener.onResponse(responseContext);

        assertThat(meterRegistry
                        .find(OTelGenAiMetricName.TOKEN_USAGE.value())
                        .tag(OTelGenAiAttributes.PROVIDER_NAME.value(), "unknown")
                        .meter())
                .isNotNull();
    }

    @Test
    void should_record_unknown_model_names_when_model_names_are_null() {
        ChatModelResponseContext responseContext = createResponseContext(ModelProvider.MICROSOFT_FOUNDRY, null, null);

        listener.onResponse(responseContext);

        assertThat(meterRegistry
                        .find(OTelGenAiMetricName.TOKEN_USAGE.value())
                        .tag(OTelGenAiAttributes.REQUEST_MODEL.value(), "unknown")
                        .tag(OTelGenAiAttributes.RESPONSE_MODEL.value(), "unknown")
                        .meter())
                .isNotNull();
    }

    private ChatModelResponseContext createResponseContext(ModelProvider modelProvider) {
        return createResponseContext(modelProvider, "gpt-4o", "gpt-4o");
    }

    private ChatModelResponseContext createResponseContext(
            ModelProvider modelProvider, String requestModelName, String responseModelName) {
        ChatResponse.Builder responseBuilder =
                ChatResponse.builder().aiMessage(new AiMessage("Hello")).tokenUsage(new TokenUsage(10, 20));
        if (responseModelName != null) {
            responseBuilder.modelName(responseModelName);
        }

        ChatRequest.Builder requestBuilder = ChatRequest.builder().messages(UserMessage.from("Hi"));
        if (requestModelName != null) {
            requestBuilder.modelName(requestModelName);
        }

        return new ChatModelResponseContext(
                responseBuilder.build(), requestBuilder.build(), modelProvider, new HashMap<>());
    }
}
