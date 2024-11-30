package dev.langchain4j.micrometer.listeners;

import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import io.micrometer.core.instrument.MeterRegistry;

public class MicrometerChatModelListener implements ChatModelListener {

    private final MeterRegistry meterRegistry;
    private static final String METRIC_PREFIX = "langchain4j.chat.";

    public MicrometerChatModelListener(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void onRequest(final ChatModelRequestContext requestContext) {
        ChatModelListener.super.onRequest(requestContext);
    }

    @Override
    public void onResponse(final ChatModelResponseContext responseContext) {
        ChatModelListener.super.onResponse(responseContext);
    }

    @Override
    public void onError(final ChatModelErrorContext errorContext) {
        ChatModelListener.super.onError(errorContext);
    }
}
