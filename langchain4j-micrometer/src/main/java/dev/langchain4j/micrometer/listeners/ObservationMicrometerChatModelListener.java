package dev.langchain4j.micrometer.listeners;

import dev.langchain4j.Experimental;
import dev.langchain4j.micrometer.conventions.AiObservationAttributes;
import dev.langchain4j.micrometer.conventions.AiObservationMetricAttributes;
import dev.langchain4j.micrometer.conventions.AiObservationMetricNames;
import dev.langchain4j.micrometer.conventions.AiOperationType;
import dev.langchain4j.micrometer.conventions.AiProvider;
import dev.langchain4j.micrometer.conventions.AiTokenType;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Experimental
public class ObservationMicrometerChatModelListener implements ChatModelListener {

    private final MeterRegistry meterRegistry;
    private static final String DESCRIPTION = "Measures number of input and output tokens used";
    private final ObservationRegistry observationRegistry;
    private final AtomicReference<Observation.Scope> scope;

    public ObservationMicrometerChatModelListener(
            final MeterRegistry meterRegistry, ObservationRegistry observationRegistry) {
        this.meterRegistry = meterRegistry;
        this.observationRegistry = observationRegistry;
        this.scope = new AtomicReference<>();
    }

    @Override
    public void onRequest(final ChatModelRequestContext requestContext) {
        setAiProvider(requestContext);
        Observation observation = createObservation(requestContext);
        scope.set(observation.openScope());
        addRequestMetrics(requestContext);
    }

    private void setAiProvider(ChatModelRequestContext requestContext) {
        final AiProvider aiProvider =
                AiProvider.fromClass(requestContext.request().getClass());
        requestContext.attributes().put("gen_ai.provider", aiProvider.value());
    }

    private Observation createObservation(ChatModelRequestContext requestContext) {
        return Observation.createNotStarted(AiObservationMetricNames.OPERATION_DURATION.value(), observationRegistry)
                .lowCardinalityKeyValue(AiObservationAttributes.AI_OPERATION_TYPE.value(), AiOperationType.CHAT.value())
                .lowCardinalityKeyValue(
                        AiObservationAttributes.AI_PROVIDER.value(), getSystemValue(requestContext.attributes()))
                .lowCardinalityKeyValue(
                        AiObservationAttributes.REQUEST_MODEL.value(),
                        requestContext.request().model())
                .contextualName("GenAI operation duration")
                .start();
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

    @Override
    public void onResponse(final ChatModelResponseContext responseContext) {
        handleResponseObservationScope(responseContext);
        addResponseMetrics(responseContext);
    }

    private void handleResponseObservationScope(ChatModelResponseContext responseContext) {
        Observation.Scope currentScope = this.scope.getAndSet(null);
        if (currentScope != null) {
            Observation observation = currentScope.getCurrentObservation();
            updateObservationWithResponse(observation, responseContext);
            observation.stop();
        }
    }

    private void updateObservationWithResponse(Observation observation, ChatModelResponseContext responseContext) {
        observation.lowCardinalityKeyValue(
                AiObservationAttributes.RESPONSE_MODEL.value(),
                responseContext.response().model());
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

    @Override
    public void onError(final ChatModelErrorContext errorContext) {
        handleErrorObservationScope(errorContext);
        addErrorMetric(errorContext);
    }

    private void handleErrorObservationScope(ChatModelErrorContext errorContext) {
        Observation.Scope currentScope = this.scope.getAndSet(null);
        if (currentScope != null) {
            Observation observation = currentScope.getCurrentObservation();
            updateObservationWithError(observation, errorContext);
            observation.error(errorContext.error());
            observation.stop();
        }
    }

    private void updateObservationWithError(Observation observation, ChatModelErrorContext errorContext) {
        observation
                .lowCardinalityKeyValue(
                        AiObservationAttributes.REQUEST_MODEL.value(),
                        errorContext.request().model())
                .lowCardinalityKeyValue(
                        AiObservationAttributes.ERROR_TYPE.value(),
                        errorContext.error().getClass().getSimpleName());
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
}
