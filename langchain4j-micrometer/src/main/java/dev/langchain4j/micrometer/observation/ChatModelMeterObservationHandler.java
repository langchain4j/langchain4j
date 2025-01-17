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

    private static final String LC_REQUEST_COUNTER = "langchain4j.chat.model.request";
    private static final String LC_ERROR_COUNTER = "langchain4j.chat.model.error";

    public ChatModelMeterObservationHandler(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void onStart(ChatModelObservationContext context) {
        addRequestMetrics(context.getRequestContext());
    }

    @Override
    public void onError(ChatModelObservationContext context) {
        addErrorMetric(context.getErrorContext());
    }

    @Override
    public void onStop(ChatModelObservationContext context) {
        addResponseMetrics(context.getResponseContext());
    }

    @Override
    public boolean supportsContext(Observation.Context context) {
        return context instanceof ChatModelObservationContext;
    }

    private void addRequestMetrics(ChatModelRequestContext requestContext) {
        Counter.builder(LC_REQUEST_COUNTER)
                .tag(OTelGenAiAttributes.OPERATION_NAME.value(), OTelGenAiOperationName.CHAT.value())
                .tag(OTelGenAiAttributes.SYSTEM.value(), getSystemValue(requestContext.attributes()))
                .tag(
                        OTelGenAiAttributes.REQUEST_MODEL.value(),
                        requestContext.request().model())
                .description("The number of requests that were made to the chat model")
                .register(meterRegistry)
                .increment();
    }

    private void addErrorMetric(ChatModelErrorContext errorContext) {
        Counter.builder(LC_ERROR_COUNTER)
                .tag(OTelGenAiAttributes.OPERATION_NAME.value(), OTelGenAiOperationName.CHAT.value())
                .tag(OTelGenAiAttributes.SYSTEM.value(), getSystemValue(errorContext.attributes()))
                .tag(
                        OTelGenAiAttributes.REQUEST_MODEL.value(),
                        errorContext.request().model())
                .description("The number of errors that occurred in the chat model")
                .register(meterRegistry)
                .increment();
    }

    private void addResponseMetrics(ChatModelResponseContext responseContext) {
        if (responseContext.response().tokenUsage() != null) {
            addTokenUsageMetrics(responseContext);
        }
    }

    private void addTokenUsageMetrics(ChatModelResponseContext responseContext) {
        addTokenMetric(
                responseContext,
                OTelGenAiTokenType.INPUT,
                responseContext.response().tokenUsage().inputTokenCount(),
                "Measures the number of input tokens used");
        addTokenMetric(
                responseContext,
                OTelGenAiTokenType.OUTPUT,
                responseContext.response().tokenUsage().outputTokenCount(),
                "Measures the number of output tokens used");
        addTokenMetric(
                responseContext,
                OTelGenAiTokenType.TOTAL,
                responseContext.response().tokenUsage().totalTokenCount(),
                "Measures the total number of tokens used (input + output)");
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
                        responseContext.request().model())
                .tag(
                        OTelGenAiAttributes.RESPONSE_MODEL.value(),
                        responseContext.response().model())
                .tag(OTelGenAiAttributes.TOKEN_TYPE.value(), tokenType.value())
                .description(description)
                .register(meterRegistry)
                .increment(tokenCount);
    }

    private String getSystemValue(Map<Object, Object> attributes) {
        return (String) attributes.get(OTelGenAiAttributes.SYSTEM);
    }
}
