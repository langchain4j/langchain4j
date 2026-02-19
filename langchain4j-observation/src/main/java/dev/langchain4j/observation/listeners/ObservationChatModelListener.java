package dev.langchain4j.observation.listeners;

import static dev.langchain4j.observation.listeners.ChatModelDocumentation.HighCardinalityValues.TOKEN_USAGE;
import static dev.langchain4j.observation.listeners.ChatModelDocumentation.LowCardinalityValues.OPERATION_NAME;
import static dev.langchain4j.observation.listeners.ChatModelDocumentation.LowCardinalityValues.PROVIDER_NAME;
import static dev.langchain4j.observation.listeners.ChatModelDocumentation.LowCardinalityValues.RESPONSE_MODEL;
import static dev.langchain4j.observation.listeners.ChatModelDocumentation.LowCardinalityValues.TOKEN_TYPE;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter.MeterProvider;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

/**
 * Will use observations and micrometer metrics to generate telemetry based on the {@link ChatModelListener} lifecycle.
 * <p>
 * Observations will handle request durations and tracing. Observation lifecycle and context management is handled here.
 * <p>
 * There is a Micrometer Counter responsible for token usage.
 */
@Experimental
public class ObservationChatModelListener implements ChatModelListener {

    private static final String OBSERVATION_SCOPE_KEY = "micrometer.observation.scope";

    private final ObservationRegistry observationRegistry;
    private final MeterProvider<Counter> tokenCounter;
    private final ChatModelConvention chatModelConvention;

    public ObservationChatModelListener(final ObservationRegistry observationRegistry, final MeterRegistry meterRegistry) {
        this.observationRegistry = observationRegistry;
        tokenCounter = Counter.builder(TOKEN_USAGE.asString())
                .description("Measures the quantity of used tokens")
                .tag(OPERATION_NAME.asString(), "chat")
                .withRegistry(meterRegistry);
        chatModelConvention = null;
    }

    @Override
    public void onRequest(final ChatModelRequestContext requestContext) {
        final Observation onRequest = ChatModelDocumentation.INSTANCE
                .start(this.chatModelConvention,
                        new DefaultChatModelConvention(),
                        () -> new ChatModelObservationContext(requestContext, null, null),
                        observationRegistry);

        final Observation.Scope scope = onRequest.openScope();
        requestContext.attributes().put(OBSERVATION_SCOPE_KEY, scope);
    }

    @Override
    public void onResponse(final ChatModelResponseContext responseContext) {
        final Observation.Scope currentScope = (Observation.Scope) responseContext.attributes().remove(OBSERVATION_SCOPE_KEY);

        if (currentScope == null) {
            return;
        }

        final Observation observation = currentScope.getCurrentObservation();
        String providerName = null;
        try {
            if (observation.getContext() instanceof ChatModelObservationContext chatModelObservationContext) {
                chatModelObservationContext.setResponseContext(responseContext);
                final ChatModelRequestContext requestContext = chatModelObservationContext.getRequestContext();
                if (requestContext != null && requestContext.modelProvider() != null) {
                    providerName = requestContext.modelProvider().name();
                }
            }

            final String responseModelName = responseContext.chatResponse().metadata().modelName();
            final Integer outputTokens = responseContext.chatResponse().tokenUsage().outputTokenCount();
            final Integer inputTokens = responseContext.chatResponse().tokenUsage().inputTokenCount();

            if (responseModelName != null) {
                if (providerName != null && outputTokens != null) {
                    tokenCounter.withTags(
                                    PROVIDER_NAME.asString(), providerName,
                                    RESPONSE_MODEL.asString(), responseModelName,
                                    TOKEN_TYPE.asString(), "input")
                            .increment(inputTokens);

                    tokenCounter.withTags(
                                    PROVIDER_NAME.asString(), providerName,
                                    RESPONSE_MODEL.asString(), responseModelName,
                                    TOKEN_TYPE.asString(), "output")
                            .increment(outputTokens);
                }
            }
        } finally {
            currentScope.close();
            observation.stop();
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

                observation.error(errorContext.error());
            } finally {
                currentScope.close();
                observation.stop();
            }
        }
    }
}
