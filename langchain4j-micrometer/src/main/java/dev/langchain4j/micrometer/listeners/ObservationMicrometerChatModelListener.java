package dev.langchain4j.micrometer.listeners;

import dev.langchain4j.Experimental;
import dev.langchain4j.micrometer.conventions.AiObservationAttributes;
import dev.langchain4j.micrometer.conventions.AiObservationMetricNames;
import dev.langchain4j.micrometer.conventions.AiOperationType;
import dev.langchain4j.micrometer.conventions.AiProvider;
import dev.langchain4j.micrometer.observations.ChatModelMeterObservationHandler;
import dev.langchain4j.micrometer.observations.ChatModelObservationContext;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

@Experimental
public class ObservationMicrometerChatModelListener implements ChatModelListener {

    private final ObservationRegistry observationRegistry;
    private final AtomicReference<Observation.Scope> scope;
    private static final String GEN_AI_PROVIDER = "gen_ai.provider";

    public ObservationMicrometerChatModelListener(
            final MeterRegistry meterRegistry, ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry;
        this.scope = new AtomicReference<>();

        observationRegistry.observationConfig().observationHandler(new ChatModelMeterObservationHandler(meterRegistry));
    }

    @Override
    public void onRequest(final ChatModelRequestContext requestContext) {
        setAiProvider(requestContext);
        Observation observation = createObservation(requestContext);
        scope.set(observation.openScope());
    }

    @Override
    public void onResponse(final ChatModelResponseContext responseContext) {
        handleResponseObservationScope(responseContext);
    }

    @Override
    public void onError(final ChatModelErrorContext errorContext) {
        handleErrorObservationScope(errorContext);
    }

    private void setAiProvider(ChatModelRequestContext requestContext) {
        final AiProvider aiProvider =
                AiProvider.fromClass(requestContext.request().getClass());
        requestContext.attributes().put(GEN_AI_PROVIDER, aiProvider.value());
    }

    private static Supplier<Observation.Context> createContextSupplier(ChatModelRequestContext requestContext) {
        return () -> new ChatModelObservationContext(requestContext, null, null);
    }

    private Observation createObservation(ChatModelRequestContext requestContext) {
        return Observation.createNotStarted(
                        AiObservationMetricNames.OPERATION_DURATION.value(),
                        createContextSupplier(requestContext),
                        observationRegistry)
                .lowCardinalityKeyValue(AiObservationAttributes.AI_OPERATION_TYPE.value(), AiOperationType.CHAT.value())
                .lowCardinalityKeyValue(
                        AiObservationAttributes.AI_PROVIDER.value(), getSystemValue(requestContext.attributes()))
                .lowCardinalityKeyValue(
                        AiObservationAttributes.REQUEST_MODEL.value(),
                        requestContext.request().model())
                .contextualName("GenAI operation duration")
                .start();
    }

    private void handleResponseObservationScope(ChatModelResponseContext responseContext) {
        Observation.Scope currentScope = this.scope.getAndSet(null);
        if (currentScope != null) {
            Observation observation = currentScope.getCurrentObservation();
            if (observation.getContext() instanceof ChatModelObservationContext chatModelObservationContext) {
                chatModelObservationContext.setResponseContext(responseContext);
            }
            updateObservationWithResponse(observation, responseContext);
            observation.stop();
        }
    }

    private void updateObservationWithResponse(Observation observation, ChatModelResponseContext responseContext) {
        observation.lowCardinalityKeyValue(
                AiObservationAttributes.RESPONSE_MODEL.value(),
                responseContext.response().model());
    }

    private String getSystemValue(Map<Object, Object> attributes) {
        return attributes.get(GEN_AI_PROVIDER) != null
                ? String.valueOf(attributes.get(GEN_AI_PROVIDER))
                : AiProvider.LANGCHAIN4J.value();
    }

    private void handleErrorObservationScope(ChatModelErrorContext errorContext) {
        Observation.Scope currentScope = this.scope.getAndSet(null);
        if (currentScope != null) {
            Observation observation = currentScope.getCurrentObservation();
            if (observation.getContext() instanceof ChatModelObservationContext chatModelObservationContext) {
                chatModelObservationContext.setErrorContext(errorContext);
            }
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
}
