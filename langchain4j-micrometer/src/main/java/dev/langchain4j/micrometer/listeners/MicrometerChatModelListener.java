package dev.langchain4j.micrometer.listeners;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.micrometer.conventions.OTelGenAiMetricAttributes;
import dev.langchain4j.micrometer.conventions.OTelGenAiMetricNames;
import dev.langchain4j.micrometer.conventions.OTelGenAiObservationAttributes;
import dev.langchain4j.micrometer.conventions.OTelGenAiOperationName;
import dev.langchain4j.micrometer.observation.ChatModelMeterObservationHandler;
import dev.langchain4j.micrometer.observation.ChatModelObservationContext;
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
public class MicrometerChatModelListener implements ChatModelListener {

    private final ObservationRegistry observationRegistry;
    private final AtomicReference<Observation.Scope> scope;

    private final String aiSystemName;

    /**
     * Default constructor.
     * @param meterRegistry         Provided MeterRegistry by Micrometer Actuator
     * @param observationRegistry   Provided ObservationRegistry by Micrometer Observation API
     * @param aiSystemName          AI system name should be in line with OpenTelemetry Semantic Convention for Generative AI Metrics.
     */
    public MicrometerChatModelListener(
            final MeterRegistry meterRegistry, ObservationRegistry observationRegistry, String aiSystemName) {
        this.observationRegistry = ensureNotNull(observationRegistry, "observationRegistry");
        this.scope = new AtomicReference<>();
        this.aiSystemName = ensureNotNull(aiSystemName, "aiSystemName");

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
        requestContext.attributes().put(OTelGenAiMetricAttributes.SYSTEM, aiSystemName);
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
                        OTelGenAiObservationAttributes.AI_OPERATION_TYPE.value(), OTelGenAiOperationName.CHAT.value())
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
        return (String) attributes.get(OTelGenAiMetricAttributes.SYSTEM);
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
