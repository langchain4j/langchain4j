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
import io.micrometer.core.instrument.Timer;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * A {@link ChatModelListener} that uses a Micrometer {@link MeterRegistry} to collect metrics
 * about chat model interactions following OpenTelemetry Semantic Conventions for Generative AI.
 * <p>
 * This listener records token usage metrics (input and output tokens) when a chat model response is received,
 * using a {@link DistributionSummary} consistent with the
 * <a href="https://opentelemetry.io/docs/specs/semconv/gen-ai/gen-ai-metrics/#metric-gen_aiclienttokenusage">
 * OpenTelemetry Semantic Conventions for {@code gen_ai.client.token.usage}</a>.
 * <p>
 * It also records the duration of every chat operation as {@code gen_ai.client.operation.duration} using a
 * {@link Timer}, consistent with the
 * <a href="https://opentelemetry.io/docs/specs/semconv/gen-ai/gen-ai-metrics/#metric-gen_aiclientoperationduration">
 * OpenTelemetry Semantic Conventions for {@code gen_ai.client.operation.duration}</a>.
 * The duration is recorded for both successful and failed calls; a failed call carries an additional
 * {@code error.type} tag, whose value is the class name of the exception and is therefore bounded.
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

    /**
     * Key under which {@link #onRequest} stores the start timestamp, so that {@link #onResponse} and
     * {@link #onError} can turn it into a duration. The key is namespaced because the attributes map is shared
     * between listeners and can be pre-populated by the caller.
     */
    private static final String START_TIME_KEY = "micrometer.metrics.chat.startTime";

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
        requestContext.attributes().put(START_TIME_KEY, System.nanoTime());
    }

    @Override
    public void onResponse(ChatModelResponseContext responseContext) {
        Long startNanos = takeStartNanos(responseContext.attributes());
        if (startNanos != null) {
            durationTimer(getProviderName(responseContext), getRequestModelName(responseContext))
                    .tag(OTelGenAiAttributes.RESPONSE_MODEL.value(), getResponseModelName(responseContext))
                    .register(meterRegistry)
                    .record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
        }

        recordTokenUsageMetrics(responseContext);
    }

    @Override
    public void onError(ChatModelErrorContext errorContext) {
        Long startNanos = takeStartNanos(errorContext.attributes());
        if (startNanos == null) {
            return;
        }

        Timer.Builder timer = durationTimer(
                OTelGenAiProviderName.fromModelProvider(errorContext.modelProvider()),
                getOrDefault(errorContext.chatRequest().parameters().modelName(), "unknown"));

        Throwable error = errorContext.error();
        if (error != null) {
            timer.tag(OTelGenAiAttributes.ERROR_TYPE.value(), error.getClass().getName());
        }

        timer.register(meterRegistry).record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
    }

    /**
     * Reads and removes the start timestamp written by {@link #onRequest}.
     *
     * @return the start timestamp in nanoseconds, or {@code null} when there is none, in which case the duration is
     * skipped rather than recorded as zero
     */
    private static Long takeStartNanos(Map<Object, Object> attributes) {
        Object startTime = attributes.remove(START_TIME_KEY);
        return startTime instanceof Long startNanos ? startNanos : null;
    }

    private static Timer.Builder durationTimer(String providerName, String requestModelName) {
        return Timer.builder(OTelGenAiMetricName.OPERATION_DURATION.value())
                .description("Measures operation duration")
                .tag(OTelGenAiAttributes.OPERATION_NAME.value(), OTelGenAiOperationName.CHAT.value())
                .tag(OTelGenAiAttributes.PROVIDER_NAME.value(), providerName)
                .tag(OTelGenAiAttributes.REQUEST_MODEL.value(), requestModelName);
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
            ChatModelResponseContext responseContext, OTelGenAiTokenType tokenType, Integer tokenCount) {

        if (tokenCount == null) {
            // Token counts are nullable (TokenUsage documents each component as "null if unknown").
            // Skip recording a token type whose count is unknown instead of failing the whole response.
            return;
        }

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
