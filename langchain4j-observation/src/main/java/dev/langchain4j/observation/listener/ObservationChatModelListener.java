package dev.langchain4j.observation.listener;

import static convention.ChatModelDocumentation.LowCardinalityValues.OPERATION_NAME;
import static convention.ChatModelDocumentation.LowCardinalityValues.PROVIDER_NAME;
import static convention.ChatModelDocumentation.LowCardinalityValues.REQUEST_MODEL;
import static convention.ChatModelDocumentation.LowCardinalityValues.RESPONSE_MODEL;
import static convention.ChatModelDocumentation.LowCardinalityValues.TOKEN_TYPE;
import static java.util.Optional.ofNullable;

import java.util.Optional;
import context.ChatModelObservationContext;
import convention.ChatModelConvention;
import convention.ChatModelDocumentation;
import convention.DefaultChatModelConvention;
import dev.langchain4j.Experimental;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Meter.MeterProvider;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

/**
 * Will use observations and micrometer metrics to generate telemetry based on the {@link ChatModelListener} lifecycle.
 * <p>
 * Observations will handle request durations and tracing. Observation lifecycle and context management is handled here.
 * <p>
 * There is a Micrometer DistributionSummary responsible to record token usage.
 */
@Experimental
public class ObservationChatModelListener implements ChatModelListener {

    private static final String OBSERVATION_SCOPE_KEY = "micrometer.observation.scope";
    static final String UNKNOWN = "unknown";
    static final String TOKEN_USAGE = "gen_ai.client.token.usage";

    private final ObservationRegistry observationRegistry;
    private final MeterProvider<DistributionSummary> tokenDistribution;
    private final ChatModelConvention chatModelConvention;

    public ObservationChatModelListener(final ObservationRegistry observationRegistry, final MeterRegistry meterRegistry) {
        this.observationRegistry = observationRegistry;
        tokenDistribution = DistributionSummary.builder(TOKEN_USAGE)
                .description("Measures the quantity of used tokens")
                .baseUnit("tokens")
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
        String requestModel = null;
        try {
            if (observation.getContext() instanceof ChatModelObservationContext chatModelObservationContext) {
                chatModelObservationContext.setResponseContext(responseContext);
                final ChatModelRequestContext requestContext = chatModelObservationContext.getRequestContext();

                providerName = ofNullable(requestContext)
                        .map(ChatModelRequestContext::modelProvider)
                        .map(ModelProvider::name)
                        .orElse(UNKNOWN);

                requestModel = ofNullable(requestContext)
                        .map(ChatModelRequestContext::chatRequest)
                        .map(ChatRequest::modelName)
                        .orElse(UNKNOWN);
            }

            final String responseModelName = ofNullable(responseContext)
                    .map(ChatModelResponseContext::chatResponse)
                    .map(ChatResponse::modelName)
                    .orElse(UNKNOWN);

            final Optional<Integer> outputTokens = ofNullable(responseContext)
                    .map(ChatModelResponseContext::chatResponse)
                    .map(ChatResponse::tokenUsage)
                    .map(TokenUsage::outputTokenCount);

            final Optional<Integer> inputTokens = ofNullable(responseContext)
                    .map(ChatModelResponseContext::chatResponse)
                    .map(ChatResponse::tokenUsage)
                    .map(TokenUsage::inputTokenCount);

            if (inputTokens.isPresent()) {
                tokenDistribution.withTags(
                                PROVIDER_NAME.asString(), providerName,
                                REQUEST_MODEL.asString(), requestModel,
                                RESPONSE_MODEL.asString(), responseModelName,
                                TOKEN_TYPE.asString(), "input")
                        .record(inputTokens.get());
            }

            if (outputTokens.isPresent()) {
                tokenDistribution.withTags(
                                PROVIDER_NAME.asString(), providerName,
                                REQUEST_MODEL.asString(), requestModel,
                                RESPONSE_MODEL.asString(), responseModelName,
                                TOKEN_TYPE.asString(), "output")
                        .record(outputTokens.get());
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
