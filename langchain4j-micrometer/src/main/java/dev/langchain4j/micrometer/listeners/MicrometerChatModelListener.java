package dev.langchain4j.micrometer.listeners;

import dev.langchain4j.micrometer.conventions.AiObservationMetricAttributes;
import dev.langchain4j.micrometer.conventions.AiObservationMetricNames;
import dev.langchain4j.micrometer.conventions.AiTokenType;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

public class MicrometerChatModelListener implements ChatModelListener {

    private final MeterRegistry meterRegistry;

    private static final String DESCRIPTION = "The number of tokens used by the model";

    public MicrometerChatModelListener(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void onRequest(final ChatModelRequestContext requestContext) {
        ChatModelListener.super.onRequest(requestContext);
    }

    @Override
    public void onResponse(final ChatModelResponseContext responseContext) {
        if (responseContext.response().tokenUsage() != null) {
            Counter.builder(AiObservationMetricNames.TOKEN_USAGE.value())
                    .tag(AiObservationMetricAttributes.TOKEN_TYPE.value(), AiTokenType.INPUT.value())
                    .description(DESCRIPTION)
                    .register(meterRegistry)
                    .increment(responseContext.response().tokenUsage().inputTokenCount());

            Counter.builder(AiObservationMetricNames.TOKEN_USAGE.value())
                    .tag(AiObservationMetricAttributes.TOKEN_TYPE.value(), AiTokenType.OUTPUT.value())
                    .description(DESCRIPTION)
                    .register(meterRegistry)
                    .increment(responseContext.response().tokenUsage().outputTokenCount());

            Counter.builder(AiObservationMetricNames.TOKEN_USAGE.value())
                    .tag(AiObservationMetricAttributes.TOKEN_TYPE.value(), AiTokenType.TOTAL.value())
                    .description(DESCRIPTION)
                    .register(meterRegistry)
                    .increment(responseContext.response().tokenUsage().totalTokenCount());
        }
    }

    @Override
    public void onError(final ChatModelErrorContext errorContext) {
        Counter.builder("langchain4j.chat.error")
                .description("The number of errors that occurred during the chat")
                .register(meterRegistry)
                .increment();
    }
}
