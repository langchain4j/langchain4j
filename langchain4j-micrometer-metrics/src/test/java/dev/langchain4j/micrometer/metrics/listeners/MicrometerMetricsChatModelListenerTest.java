package dev.langchain4j.micrometer.metrics.listeners;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.micrometer.metrics.conventions.OTelGenAiAttributes;
import dev.langchain4j.micrometer.metrics.conventions.OTelGenAiMetricName;
import dev.langchain4j.micrometer.metrics.conventions.OTelGenAiOperationName;
import dev.langchain4j.micrometer.metrics.conventions.OTelGenAiTokenType;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MicrometerMetricsChatModelListenerTest {

    MicrometerMetricsChatModelListener listener;
    MeterRegistry meterRegistry;

    /**
     * Shared by the request, response and error contexts of a single lifecycle, the way {@code ChatModel} passes
     * the same map through every callback.
     */
    Map<Object, Object> attributes = new HashMap<>();

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        attributes = new HashMap<>();
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

    @Test
    void should_record_only_output_metric_when_input_token_count_is_null() {
        ChatModelResponseContext responseContext = responseContextWithTokenUsage(new TokenUsage(null, 20));

        listener.onResponse(responseContext);

        assertThat(meterRegistry
                        .find(OTelGenAiMetricName.TOKEN_USAGE.value())
                        .tag(OTelGenAiAttributes.TOKEN_TYPE.value(), OTelGenAiTokenType.OUTPUT.value())
                        .summary())
                .isNotNull();
        assertThat(meterRegistry
                        .find(OTelGenAiMetricName.TOKEN_USAGE.value())
                        .tag(OTelGenAiAttributes.TOKEN_TYPE.value(), OTelGenAiTokenType.INPUT.value())
                        .meter())
                .isNull();
    }

    @Test
    void should_record_only_input_metric_when_output_token_count_is_null() {
        ChatModelResponseContext responseContext = responseContextWithTokenUsage(new TokenUsage(10, null));

        listener.onResponse(responseContext);

        assertThat(meterRegistry
                        .find(OTelGenAiMetricName.TOKEN_USAGE.value())
                        .tag(OTelGenAiAttributes.TOKEN_TYPE.value(), OTelGenAiTokenType.INPUT.value())
                        .summary())
                .isNotNull();
        assertThat(meterRegistry
                        .find(OTelGenAiMetricName.TOKEN_USAGE.value())
                        .tag(OTelGenAiAttributes.TOKEN_TYPE.value(), OTelGenAiTokenType.OUTPUT.value())
                        .meter())
                .isNull();
    }

    @Test
    void should_record_no_token_metric_and_not_throw_when_all_token_counts_are_null() {
        ChatModelResponseContext responseContext = responseContextWithTokenUsage(new TokenUsage());

        assertThatCode(() -> listener.onResponse(responseContext)).doesNotThrowAnyException();

        assertThat(meterRegistry.find(OTelGenAiMetricName.TOKEN_USAGE.value()).meter())
                .isNull();
    }

    @Test
    void should_record_operation_duration_when_the_call_succeeds() {
        listener.onRequest(requestContext());
        listener.onResponse(responseContextWithTokenUsage(new TokenUsage(10, 20)));

        Timer timer = meterRegistry
                .find(OTelGenAiMetricName.OPERATION_DURATION.value())
                .tag(OTelGenAiAttributes.OPERATION_NAME.value(), OTelGenAiOperationName.CHAT.value())
                .tag(OTelGenAiAttributes.PROVIDER_NAME.value(), "azure.ai.inference")
                .tag(OTelGenAiAttributes.REQUEST_MODEL.value(), "gpt-4o")
                .tag(OTelGenAiAttributes.RESPONSE_MODEL.value(), "gpt-4o")
                .timer();

        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1L);
    }

    @Test
    void should_record_error_type_on_the_duration_when_the_call_fails() {
        listener.onRequest(requestContext());
        listener.onError(errorContext(new IllegalStateException("boom")));

        String errorType = IllegalStateException.class.getName();

        assertThat(meterRegistry
                        .find(OTelGenAiMetricName.OPERATION_DURATION.value())
                        .tag(OTelGenAiAttributes.ERROR_TYPE.value(), errorType)
                        .timer())
                .isNotNull();
        // a failed call has no response, so the duration must not claim a response model
        assertThat(meterRegistry
                        .find(OTelGenAiMetricName.OPERATION_DURATION.value())
                        .tag(OTelGenAiAttributes.ERROR_TYPE.value(), errorType)
                        .tag(OTelGenAiAttributes.RESPONSE_MODEL.value(), "gpt-4o")
                        .meter())
                .isNull();
    }

    @Test
    void should_not_record_operation_duration_when_the_request_was_not_started() {
        listener.onResponse(responseContextWithTokenUsage(new TokenUsage(10, 20)));

        assertThat(meterRegistry
                        .find(OTelGenAiMetricName.OPERATION_DURATION.value())
                        .meter())
                .isNull();
    }

    @Test
    void should_record_operation_duration_once_per_request() {
        listener.onRequest(requestContext());
        listener.onResponse(responseContextWithTokenUsage(new TokenUsage(10, 20)));
        listener.onResponse(responseContextWithTokenUsage(new TokenUsage(10, 20)));

        // the start timestamp is consumed by the first response, so the second one has nothing to measure
        assertThat(meterRegistry
                        .find(OTelGenAiMetricName.OPERATION_DURATION.value())
                        .timer()
                        .count())
                .isEqualTo(1L);
    }

    @Test
    void should_still_record_token_usage_when_the_request_was_started() {
        listener.onRequest(requestContext());
        listener.onResponse(responseContextWithTokenUsage(new TokenUsage(10, 20)));

        assertThat(meterRegistry
                        .find(OTelGenAiMetricName.TOKEN_USAGE.value())
                        .tag(OTelGenAiAttributes.TOKEN_TYPE.value(), OTelGenAiTokenType.INPUT.value())
                        .summary())
                .isNotNull();
        assertThat(meterRegistry
                        .find(OTelGenAiMetricName.TOKEN_USAGE.value())
                        .tag(OTelGenAiAttributes.TOKEN_TYPE.value(), OTelGenAiTokenType.OUTPUT.value())
                        .summary())
                .isNotNull();
    }

    private ChatModelRequestContext requestContext() {
        return new ChatModelRequestContext(chatRequest(), ModelProvider.MICROSOFT_FOUNDRY, attributes);
    }

    private ChatModelErrorContext errorContext(Throwable error) {
        return new ChatModelErrorContext(error, chatRequest(), ModelProvider.MICROSOFT_FOUNDRY, attributes);
    }

    private static ChatRequest chatRequest() {
        return ChatRequest.builder()
                .messages(UserMessage.from("Hi"))
                .modelName("gpt-4o")
                .build();
    }

    private ChatModelResponseContext createResponseContext(ModelProvider modelProvider) {
        return createResponseContext(modelProvider, "gpt-4o", "gpt-4o");
    }

    private ChatModelResponseContext responseContextWithTokenUsage(TokenUsage tokenUsage) {
        ChatResponse chatResponse = ChatResponse.builder()
                .aiMessage(new AiMessage("Hello"))
                .modelName("gpt-4o")
                .tokenUsage(tokenUsage)
                .build();

        return new ChatModelResponseContext(chatResponse, chatRequest(), ModelProvider.MICROSOFT_FOUNDRY, attributes);
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
