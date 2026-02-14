package dev.langchain4j.micrometer.metrics.listeners;

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
 * This listener records token usage metrics (input and output tokens) when a chat model response is received.
 * The token usage metric is recorded as a {@link DistributionSummary} (Histogram), consistent with the
 * <a href="https://opentelemetry.io/docs/specs/semconv/gen-ai/gen-ai-metrics/#metric-gen_aiclienttokenusage">
 * OpenTelemetry Semantic Conventions for {@code gen_ai.client.token.usage}</a>.
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
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void onRequest(final ChatModelRequestContext requestContext) {
        // Nothing to do at on request
    }

    @Override
    public void onResponse(final ChatModelResponseContext responseContext) {
        recordTokenUsageMetrics(responseContext);
    }

    @Override
    public void onError(final ChatModelErrorContext errorContext) {
        // ChatModelErrorContext does not contain the ChatModelResponseContext, therefor token usage is unavailable
    }

    private String getProviderName(ChatModelResponseContext responseContext) {
        return OTelGenAiProviderName.fromModelProvider(responseContext.modelProvider());
    }

    private String getRequestModelName(ChatModelResponseContext responseContext) {
        String modelName = responseContext.chatRequest().parameters().modelName();
        return modelName != null ? modelName : "unknown";
    }

    private String getResponseModelName(ChatModelResponseContext responseContext) {
        String modelName = responseContext.chatResponse().metadata().modelName();
        return modelName != null ? modelName : "unknown";
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
                .baseUnit("{token}")
                .tag(OTelGenAiAttributes.OPERATION_NAME.value(), OTelGenAiOperationName.CHAT.value())
                .tag(OTelGenAiAttributes.PROVIDER_NAME.value(), getProviderName(responseContext))
                .tag(OTelGenAiAttributes.REQUEST_MODEL.value(), getRequestModelName(responseContext))
                .tag(OTelGenAiAttributes.RESPONSE_MODEL.value(), getResponseModelName(responseContext))
                .tag(OTelGenAiAttributes.TOKEN_TYPE.value(), tokenType.value())
                .description(String.format("Measures %s tokens used", tokenType.value()))
                .publishPercentileHistogram()
                .minimumExpectedValue(1.0)
                .maximumExpectedValue(67108864.0)
                .serviceLevelObjectives(
                        1, 4, 16, 64, 256, 1024, 4096, 16384, 65536, 262144, 1048576, 4194304, 16777216, 67108864)
                .register(meterRegistry)
                .record(tokenCount);
    }
}
