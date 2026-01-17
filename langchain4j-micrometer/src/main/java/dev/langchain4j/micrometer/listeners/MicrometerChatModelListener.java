package dev.langchain4j.micrometer.listeners;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.micrometer.conventions.OTelGenAiAttributes;
import dev.langchain4j.micrometer.conventions.OTelGenAiMetricName;
import dev.langchain4j.micrometer.conventions.OTelGenAiOperationName;
import dev.langchain4j.micrometer.observation.ChatModelObservationContext;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.util.Map;
import java.util.function.Supplier;

@Experimental
public class MicrometerChatModelListener implements ChatModelListener {

    private static final String OBSERVATION_SCOPE_KEY = "micrometer.observation.scope";
    private static final String OUTCOME_KEY = "outcome";
    private static final String OUTCOME_SUCCESS = "SUCCESS";
    private static final String OUTCOME_ERROR = "ERROR";

    private final ObservationRegistry observationRegistry;
    private final String aiSystemName;

    /**
     * Default constructor.
     * @param observationRegistry   Provided ObservationRegistry by Micrometer Observation API
     * @param aiSystemName          AI system name should be in line with OpenTelemetry Semantic Convention for Generative AI Metrics.
     */
    public MicrometerChatModelListener(ObservationRegistry observationRegistry, String aiSystemName) {
        this.observationRegistry = ensureNotNull(observationRegistry, "observationRegistry");
        this.aiSystemName = ensureNotNull(aiSystemName, "aiSystemName");
    }

    @Override
    public void onRequest(final ChatModelRequestContext requestContext) {
        setAiProvider(requestContext);
        Observation observation = createObservation(requestContext);
        Observation.Scope scope = observation.openScope();
        requestContext.attributes().put(OBSERVATION_SCOPE_KEY, scope);
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
        requestContext.attributes().put(OTelGenAiAttributes.SYSTEM, aiSystemName);
    }

    private static Supplier<Observation.Context> createContextSupplier(ChatModelRequestContext requestContext) {
        return () -> new ChatModelObservationContext(requestContext, null, null);
    }

    private Observation createObservation(ChatModelRequestContext requestContext) {
        return Observation.createNotStarted(
                        OTelGenAiMetricName.OPERATION_DURATION.value(),
                        createContextSupplier(requestContext),
                        observationRegistry)
                .lowCardinalityKeyValue(OTelGenAiAttributes.OPERATION_NAME.value(), OTelGenAiOperationName.CHAT.value())
                .lowCardinalityKeyValue(OTelGenAiAttributes.SYSTEM.value(), getSystemValue(requestContext.attributes()))
                .lowCardinalityKeyValue(
                        OTelGenAiAttributes.REQUEST_MODEL.value(),
                        requestContext.chatRequest().parameters().modelName())
                .contextualName("GenAI operation duration")
                .start();
    }

    private void handleResponseObservationScope(ChatModelResponseContext responseContext) {
        Observation.Scope currentScope = (Observation.Scope) responseContext.attributes().remove(OBSERVATION_SCOPE_KEY);
        if (currentScope != null) {
            Observation observation = currentScope.getCurrentObservation();
            try {
                if (observation.getContext() instanceof ChatModelObservationContext chatModelObservationContext) {
                    chatModelObservationContext.setResponseContext(responseContext);
                }
                updateObservationWithResponse(observation, responseContext);
                observation.lowCardinalityKeyValue(OUTCOME_KEY, OUTCOME_SUCCESS);
            } finally {
                currentScope.close();
                observation.stop();
            }
        }
    }

    private void updateObservationWithResponse(Observation observation, ChatModelResponseContext responseContext) {
        observation.lowCardinalityKeyValue(
                OTelGenAiAttributes.RESPONSE_MODEL.value(),
                responseContext.chatResponse().metadata().modelName());
    }

    private String getSystemValue(Map<Object, Object> attributes) {
        return (String) attributes.get(OTelGenAiAttributes.SYSTEM);
    }

    private void handleErrorObservationScope(ChatModelErrorContext errorContext) {
        Observation.Scope currentScope = (Observation.Scope) errorContext.attributes().remove(OBSERVATION_SCOPE_KEY);
        if (currentScope != null) {
            Observation observation = currentScope.getCurrentObservation();
            try {
                if (observation.getContext() instanceof ChatModelObservationContext chatModelObservationContext) {
                    chatModelObservationContext.setErrorContext(errorContext);
                }
                updateObservationWithError(observation, errorContext);
                observation.lowCardinalityKeyValue(OUTCOME_KEY, OUTCOME_ERROR);
                observation.error(errorContext.error());
            } finally {
                currentScope.close();
                observation.stop();
            }
        }
    }

    private void updateObservationWithError(Observation observation, ChatModelErrorContext errorContext) {
        observation
                .lowCardinalityKeyValue(
                        OTelGenAiAttributes.REQUEST_MODEL.value(),
                        errorContext.chatRequest().parameters().modelName())
                .lowCardinalityKeyValue(
                        OTelGenAiAttributes.ERROR_TYPE.value(),
                        errorContext.error().getClass().getSimpleName());
    }
}
