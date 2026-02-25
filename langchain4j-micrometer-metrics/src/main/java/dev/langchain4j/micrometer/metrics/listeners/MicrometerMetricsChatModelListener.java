package dev.langchain4j.micrometer.metrics.listeners;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.micrometer.metrics.conventions.OTelGenAiAttributes;
import dev.langchain4j.micrometer.metrics.conventions.OTelGenAiMetricName;
import dev.langchain4j.micrometer.metrics.conventions.OTelGenAiOperationName;
import dev.langchain4j.micrometer.metrics.conventions.OTelGenAiProviderName;
import dev.langchain4j.micrometer.metrics.conventions.OTelGenAiTokenType;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * A {@link ChatModelListener} that uses a Micrometer {@link MeterRegistry} to collect metrics
 * about chat model interactions following OpenTelemetry Semantic Conventions for Generative AI.
 * <p>
 * This listener records token usage metrics (input and output tokens) when a chat model response is received,
 * using a {@link DistributionSummary} consistent with the
 * <a href="https://opentelemetry.io/docs/specs/semconv/gen-ai/gen-ai-metrics/#metric-gen_aiclienttokenusage">
 * OpenTelemetry Semantic Conventions for {@code gen_ai.client.token.usage}</a>.
 * <p>
 * Histogram publishing and bucket boundaries are not configured by this listener.
 * Users can enable histograms and set bucket boundaries through their {@link MeterRegistry} configuration
 * (e.g., via Spring Boot properties or {@link io.micrometer.core.instrument.config.MeterFilter}).
 * <p>
 * Note: The {@link MicrometerMetricsChatModelListener}
 * must be instantiated separately (e.g., via Spring Boot auto-configuration or manual instantiation).
 */
@Experimental
public class MicrometerMetricsChatModelListener implements ChatModelListener {

    private final MeterRegistry meterRegistry;

    /**
     * Creates a new {@link MicrometerMetricsChatModelListener}.
     *
     * @param meterRegistry the {@link MeterRegistry} to register metrics with
     */
    public MicrometerMetricsChatModelListener(MeterRegistry meterRegistry) {
        this.meterRegistry = ensureNotNull(meterRegistry, "meterRegistry");
    }

    @Override
    public void onRequest(ChatModelRequestContext requestContext) {
        // Nothing to do on request
    }

    @Override
    public void onResponse(ChatModelResponseContext responseContext) {
        recordTokenUsageMetrics(responseContext);
    }

    @Override
    public void onError(ChatModelErrorContext errorContext) {
        // Nothing to do on error, ChatModelErrorContext does not contain TokenUsage
    }

    private void recordTokenUsageMetrics(ChatModelResponseContext responseContext) {
        if (responseContext == null || responseContext.chatResponse().tokenUsage() == null) return;

        addTokenMetric(
                responseContext,
                OTelGenAiTokenType.INPUT,
                responseContext.chatResponse().tokenUsage().inputTokenCount());
        addTokenMetric(
                responseContext,
                OTelGenAiTokenType.OUTPUT,
                responseContext.chatResponse().tokenUsage().outputTokenCount());
    }

    private void addTokenMetric(
            ChatModelResponseContext responseContext, OTelGenAiTokenType tokenType, int tokenCount) {

        DistributionSummary.builder(OTelGenAiMetricName.TOKEN_USAGE.value())
                .baseUnit("tokens")
                .tag(OTelGenAiAttributes.OPERATION_NAME.value(), OTelGenAiOperationName.CHAT.value())
                .tag(OTelGenAiAttributes.PROVIDER_NAME.value(), getProviderName(responseContext))
                .tag(OTelGenAiAttributes.REQUEST_MODEL.value(), getRequestModelName(responseContext))
                .tag(OTelGenAiAttributes.RESPONSE_MODEL.value(), getResponseModelName(responseContext))
                .tag(OTelGenAiAttributes.TOKEN_TYPE.value(), tokenType.value())
                .description("Measures token usage")
                .register(meterRegistry)
                .record(tokenCount);
    }

    private static String getProviderName(ChatModelResponseContext responseContext) {
        return OTelGenAiProviderName.fromModelProvider(responseContext.modelProvider());
    }

    private static String getRequestModelName(ChatModelResponseContext responseContext) {
        String modelName = responseContext.chatRequest().parameters().modelName();
        return getOrDefault(modelName, "unknown");
    }

    private static String getResponseModelName(ChatModelResponseContext responseContext) {
        String modelName = responseContext.chatResponse().metadata().modelName();
        return getOrDefault(modelName, "unknown");
    }
}
