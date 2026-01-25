package dev.langchain4j.micrometer.metrics.listeners;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import java.util.Map;
import dev.langchain4j.Experimental;
import dev.langchain4j.micrometer.metrics.conventions.OTelGenAiAttributes;
import dev.langchain4j.micrometer.metrics.conventions.OTelGenAiMetricName;
import dev.langchain4j.micrometer.metrics.conventions.OTelGenAiOperationName;
import dev.langchain4j.micrometer.metrics.conventions.OTelGenAiTokenType;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * A {@link ChatModelListener} that uses Micrometer MeterRegistery to collect metrics
 * about chat model interactions following OpenTelemetry Semantic Conventions for Generative AI.
 * * <p>
 * Note: The {@link ChatModelMicrometerMetricsListener}
 * must be instantiated separately (e.g., via Spring Boot auto-configuration or manual instantiation).
 */
@Experimental
public class ChatModelMicrometerMetricsListener implements ChatModelListener {

    private final MeterRegistry meterRegistry;
    private final String aiSystemName;

    /**
     * Constructor.
     * @param meterRegistry
     * @param aiSystemName          AI system name should be in line with OpenTelemetry Semantic Convention
     *                              for Generative AI Metrics (e.g., "openai", "azure_openai", "anthropic").
     */
    public ChatModelMicrometerMetricsListener(MeterRegistry meterRegistry, String aiSystemName) {
        this.meterRegistry = meterRegistry;
        this.aiSystemName = ensureNotBlank(aiSystemName, "aiSystemName");
    }

    @Override
    public void onRequest(final ChatModelRequestContext requestContext) {
        setSystemName(requestContext);
    }

    @Override
    public void onResponse(final ChatModelResponseContext responseContext) {
        recordTokenUsageMetrics(responseContext);
    }

    @Override
    public void onError(final ChatModelErrorContext errorContext) {
        // ChatModelErrorContext does not contain the ChatModelResponseContext, therefor token usage is unavailable
    }

    private void setSystemName(ChatModelRequestContext requestContext) {
        requestContext.attributes().put(OTelGenAiAttributes.SYSTEM, aiSystemName);
    }

    private String getSystemName(Map<Object, Object> attributes) {
        return (String) attributes.get(OTelGenAiAttributes.SYSTEM);
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

    private void addTokenMetric(ChatModelResponseContext responseContext,
                                OTelGenAiTokenType tokenType,
                                int tokenCount) {

        Counter.builder(OTelGenAiMetricName.TOKEN_USAGE.value())
                .tag(OTelGenAiAttributes.OPERATION_NAME.value(), OTelGenAiOperationName.CHAT.value())
                .tag(OTelGenAiAttributes.SYSTEM.value(), getSystemName(responseContext.attributes()))
                .tag(OTelGenAiAttributes.REQUEST_MODEL.value(),
                     responseContext.chatRequest().parameters().modelName())
                .tag(OTelGenAiAttributes.RESPONSE_MODEL.value(),
                     responseContext.chatResponse().metadata().modelName())
                .tag(OTelGenAiAttributes.TOKEN_TYPE.value(), tokenType.value())
                .description(String.format("Counts %s tokens used", tokenType.value()))
                .register(meterRegistry)
                .increment(tokenCount);
    }
}
