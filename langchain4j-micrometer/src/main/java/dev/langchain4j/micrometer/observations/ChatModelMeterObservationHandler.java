package dev.langchain4j.micrometer.observations;

import dev.langchain4j.micrometer.conventions.AiObservationMetricAttributes;
import dev.langchain4j.micrometer.conventions.AiObservationMetricNames;
import dev.langchain4j.micrometer.conventions.AiProvider;
import dev.langchain4j.micrometer.conventions.AiTokenType;
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

    private static final String DESCRIPTION = "Measures number of input and output tokens used";

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
        if (requestContext.request() != null) {
            Counter.builder("langchain4j.chat.model.request")
                    .tag("gen_ai.operation.name", "chat")
                    .tag(
                            "gen_ai.system",
                            requestContext.attributes().get("gen_ai.provider") != null
                                    ? String.valueOf(requestContext.attributes().get("gen_ai.provider"))
                                    : "langchain4j")
                    .tag("gen_ai.request.model", requestContext.request().model())
                    .description("The number of requests that were made to the chat model")
                    .register(meterRegistry)
                    .increment();
        }
    }

    private void addErrorMetric(ChatModelErrorContext errorContext) {
        if (errorContext.request() != null) {
            Counter.builder("langchain4j.chat.model.error")
                    .tag("gen_ai.operation.name", "chat")
                    .tag("gen_ai.system", getSystemValue(errorContext.attributes()))
                    .tag("gen_ai.request.model", errorContext.request().model())
                    .description("The number of errors that occurred in the chat model")
                    .register(meterRegistry)
                    .increment();
        }
    }

    private void addResponseMetrics(ChatModelResponseContext responseContext) {
        if (responseContext.response().tokenUsage() != null) {
            addTokenUsageMetrics(responseContext);
        }
    }

    private void addTokenUsageMetrics(ChatModelResponseContext responseContext) {
        addTokenMetric(
                responseContext,
                AiTokenType.INPUT,
                responseContext.response().tokenUsage().inputTokenCount());
        addTokenMetric(
                responseContext,
                AiTokenType.OUTPUT,
                responseContext.response().tokenUsage().outputTokenCount());
        addTokenMetric(
                responseContext,
                AiTokenType.TOTAL,
                responseContext.response().tokenUsage().totalTokenCount());
    }

    private void addTokenMetric(ChatModelResponseContext responseContext, AiTokenType tokenType, int tokenCount) {
        Counter.builder(AiObservationMetricNames.TOKEN_USAGE.value())
                .tag("gen_ai.operation.name", "chat")
                .tag("gen_ai.system", getSystemValue(responseContext.attributes()))
                .tag("gen_ai.request.model", responseContext.request().model())
                .tag("gen_ai.response.model", responseContext.response().model())
                .tag(AiObservationMetricAttributes.TOKEN_TYPE.value(), tokenType.value())
                .description(DESCRIPTION)
                .register(meterRegistry)
                .increment(tokenCount);
    }

    private String getSystemValue(Map<Object, Object> attributes) {
        return attributes.get("gen_ai.provider") != null
                ? String.valueOf(attributes.get("gen_ai.provider"))
                : AiProvider.LANGCHAIN4J.value();
    }
}
