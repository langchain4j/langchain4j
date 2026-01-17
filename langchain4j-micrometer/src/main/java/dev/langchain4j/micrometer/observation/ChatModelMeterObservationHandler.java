package dev.langchain4j.micrometer.observation;

import dev.langchain4j.micrometer.conventions.OTelGenAiAttributes;
import dev.langchain4j.micrometer.conventions.OTelGenAiMetricName;
import dev.langchain4j.micrometer.conventions.OTelGenAiOperationName;
import dev.langchain4j.micrometer.conventions.OTelGenAiTokenType;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import java.util.Map;

public class ChatModelMeterObservationHandler implements ObservationHandler<ChatModelObservationContext> {

    private final MeterRegistry meterRegistry;

    public ChatModelMeterObservationHandler(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void onStart(ChatModelObservationContext context) {
        // Observation started - timing handled by default MeterObservationHandler
    }

    @Override
    public void onError(ChatModelObservationContext context) {
        // Error handling is done via observation.error() in the listener
    }

    @Override
    public void onStop(ChatModelObservationContext context) {
        addResponseMetrics(context.getResponseContext());
    }

    @Override
    public boolean supportsContext(Observation.Context context) {
        return context instanceof ChatModelObservationContext;
    }

    private void addResponseMetrics(ChatModelResponseContext responseContext) {
        if (responseContext != null && responseContext.chatResponse().tokenUsage() != null) {
            addTokenUsageMetrics(responseContext);
        }
    }

    private void addTokenUsageMetrics(ChatModelResponseContext responseContext) {
        addTokenMetric(
                responseContext,
                OTelGenAiTokenType.INPUT,
                responseContext.chatResponse().tokenUsage().inputTokenCount(),
                "Measures the number of input tokens used");
        addTokenMetric(
                responseContext,
                OTelGenAiTokenType.OUTPUT,
                responseContext.chatResponse().tokenUsage().outputTokenCount(),
                "Measures the number of output tokens used");
    }

    private void addTokenMetric(
            ChatModelResponseContext responseContext,
            OTelGenAiTokenType tokenType,
            int tokenCount,
            String description) {
        Counter.builder(OTelGenAiMetricName.TOKEN_USAGE.value())
                .tag(OTelGenAiAttributes.OPERATION_NAME.value(), OTelGenAiOperationName.CHAT.value())
                .tag(OTelGenAiAttributes.SYSTEM.value(), getSystemValue(responseContext.attributes()))
                .tag(
                        OTelGenAiAttributes.REQUEST_MODEL.value(),
                        responseContext.chatRequest().parameters().modelName())
                .tag(
                        OTelGenAiAttributes.RESPONSE_MODEL.value(),
                        responseContext.chatResponse().metadata().modelName())
                .tag(OTelGenAiAttributes.TOKEN_TYPE.value(), tokenType.value())
                .description(description)
                .register(meterRegistry)
                .increment(tokenCount);
    }

    private String getSystemValue(Map<Object, Object> attributes) {
        return (String) attributes.get(OTelGenAiAttributes.SYSTEM);
    }
}
