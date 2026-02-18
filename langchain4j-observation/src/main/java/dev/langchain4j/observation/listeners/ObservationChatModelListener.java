package dev.langchain4j.observation.listeners;

import static dev.langchain4j.observation.listeners.AttributeKeys.OPERATION_NAME;
import static dev.langchain4j.observation.listeners.AttributeKeys.PROVIDER_NAME;
import static dev.langchain4j.observation.listeners.AttributeKeys.REQUEST_MODEL;
import static dev.langchain4j.observation.listeners.AttributeKeys.RESPONSE_MODEL;
import static dev.langchain4j.observation.listeners.AttributeKeys.TOKEN_TYPE;
import static dev.langchain4j.observation.listeners.AttributeKeys.TOKEN_USAGE;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import io.micrometer.common.KeyValue;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter.MeterProvider;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

@Experimental
public class ObservationChatModelListener implements ChatModelListener {

    private static final String OBSERVATION_SCOPE_KEY = "micrometer.observation.scope";
    private static final String OUTCOME_KEY = "outcome";
    private static final String OUTCOME_SUCCESS = "SUCCESS";
    private static final String OUTCOME_ERROR = "ERROR";

    private final ObservationRegistry observationRegistry;
    private final MeterProvider<Counter> tokenCounter;

    public ObservationChatModelListener(final ObservationRegistry observationRegistry, final MeterRegistry meterRegistry) {
        this.observationRegistry = observationRegistry;
        tokenCounter = Counter.builder(TOKEN_USAGE.value())
                .description("Measures the quantity of used tokens")
                .tag(OPERATION_NAME.value(), "chat")
                .withRegistry(meterRegistry);
    }

    @Override
    public void onRequest(final ChatModelRequestContext requestContext) {
        final Observation onRequest = Observation.createNotStarted("gen_ai.client.operation.duration",
                        () -> new ChatModelObservationContext(requestContext, null, null),
                        observationRegistry)
                .lowCardinalityKeyValue(OPERATION_NAME.value(), "chat")
                .lowCardinalityKeyValue(PROVIDER_NAME.value(), requestContext.modelProvider().name())
                .lowCardinalityKeyValue(REQUEST_MODEL.value(), requestContext.chatRequest().parameters().modelName())
                .contextualName("GenAI request duration")
                .start();
        final Observation.Scope scope = onRequest.openScope();
        requestContext.attributes().put(OBSERVATION_SCOPE_KEY, scope);
    }

    @Override
    public void onResponse(final ChatModelResponseContext responseContext) {
        final Observation.Scope currentScope = (Observation.Scope) responseContext.attributes().remove(OBSERVATION_SCOPE_KEY);
        if (currentScope != null) {
            final Observation observation = currentScope.getCurrentObservation();
            try {
                if (observation.getContext() instanceof ChatModelObservationContext chatModelObservationContext) {
                    chatModelObservationContext.setResponseContext(responseContext);
                }

                final String responseModelName = responseContext.chatResponse().metadata().modelName();
                final Integer outputTokens = responseContext.chatResponse().tokenUsage().outputTokenCount();
                final Integer inputTokens = responseContext.chatResponse().tokenUsage().inputTokenCount();
                final KeyValue providerName = observation.getContextView().getLowCardinalityKeyValue(PROVIDER_NAME.value());

                if (responseModelName != null) {
                    observation.lowCardinalityKeyValue(RESPONSE_MODEL.value(), responseModelName);
                    if (providerName != null && providerName.getValue() != null && outputTokens != null) {
                        tokenCounter.withTags(
                                        PROVIDER_NAME.value(), providerName.getValue(),
                                        RESPONSE_MODEL.value(), responseModelName,
                                        TOKEN_TYPE.value(), "input")
                                .increment(inputTokens);

                        tokenCounter.withTags(
                                        PROVIDER_NAME.value(), providerName.getValue(),
                                        RESPONSE_MODEL.value(), responseModelName,
                                        TOKEN_TYPE.value(), "output")
                                .increment(outputTokens);
                    }
                }

                if (outputTokens != null) {
                    observation.highCardinalityKeyValue("output_tokens", "" + outputTokens);
                }

                if (inputTokens != null) {
                    observation.highCardinalityKeyValue("input_tokens", "" + inputTokens);
                }
                observation.lowCardinalityKeyValue(OUTCOME_KEY, OUTCOME_SUCCESS);
            } finally {
                currentScope.close();
                observation.stop();
            }
        }
    }

    @Override
    public void onError(final ChatModelErrorContext errorContext) {
        final Observation.Scope currentScope = (Observation.Scope) errorContext.attributes().remove(OBSERVATION_SCOPE_KEY);
        if (currentScope != null) {
            final Observation observation = currentScope.getCurrentObservation();
            try {
                if (observation.getContext() instanceof ChatModelObservationContext chatModelObservationContext) {
                    chatModelObservationContext.setErrorContext(errorContext);
                }
                final String modelName = errorContext.chatRequest().parameters().modelName();
                if (modelName != null) {
                    observation.lowCardinalityKeyValue(REQUEST_MODEL.value(), modelName);
                }
                observation.lowCardinalityKeyValue(OUTCOME_KEY, OUTCOME_ERROR);
                observation.error(errorContext.error());
            } finally {
                currentScope.close();
                observation.stop();
            }
        }
    }
}
