package dev.langchain4j.micrometer.listeners;

import dev.langchain4j.Experimental;
import dev.langchain4j.micrometer.conventions.OTelGenAiMetricAttributes;
import dev.langchain4j.micrometer.conventions.OTelGenAiMetricNames;
import dev.langchain4j.micrometer.conventions.OTelGenAiObservationAttributes;
import dev.langchain4j.micrometer.conventions.OTelGenAiOperationType;
import dev.langchain4j.micrometer.conventions.OTelGenAiSystem;
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
public class ChatModelObservationListener implements ChatModelListener {

    private final ObservationRegistry observationRegistry;
    private final AtomicReference<Observation.Scope> scope;

    public ChatModelObservationListener(final MeterRegistry meterRegistry, ObservationRegistry observationRegistry) {
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
        final OTelGenAiSystem aiSystem =
                OTelGenAiSystem.fromClass(requestContext.request().getClass());
        requestContext.attributes().put(OTelGenAiMetricAttributes.SYSTEM, aiSystem.value());
    }

    private static Supplier<Observation.Context> createContextSupplier(ChatModelRequestContext requestContext) {
        return () -> new ChatModelObservationContext(requestContext, null, null);
    }

    private Observation createObservation(ChatModelRequestContext requestContext) {
        return Observation.createNotStarted(
                        OTelGenAiMetricNames.OPERATION_DURATION.value(),
                        createContextSupplier(requestContext),
                        observationRegistry)
                .lowCardinalityKeyValue(
                        OTelGenAiObservationAttributes.AI_OPERATION_TYPE.value(), OTelGenAiOperationType.CHAT.value())
                .lowCardinalityKeyValue(
                        OTelGenAiObservationAttributes.AI_SYSTEM.value(), getSystemValue(requestContext.attributes()))
                .lowCardinalityKeyValue(
                        OTelGenAiObservationAttributes.REQUEST_MODEL.value(),
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
                OTelGenAiObservationAttributes.RESPONSE_MODEL.value(),
                responseContext.response().model());
    }

    private String getSystemValue(Map<Object, Object> attributes) {
        return attributes.get(OTelGenAiMetricAttributes.SYSTEM.value()) != null
                ? String.valueOf(attributes.get(OTelGenAiMetricAttributes.SYSTEM.value()))
                : OTelGenAiSystem.LANGCHAIN4J.value();
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
                        OTelGenAiObservationAttributes.REQUEST_MODEL.value(),
                        errorContext.request().model())
                .lowCardinalityKeyValue(
                        OTelGenAiObservationAttributes.ERROR_TYPE.value(),
                        errorContext.error().getClass().getSimpleName());
    }
}
