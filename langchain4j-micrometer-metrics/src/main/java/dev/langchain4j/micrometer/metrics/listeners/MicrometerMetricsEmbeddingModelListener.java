package dev.langchain4j.micrometer.metrics.listeners;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.micrometer.metrics.conventions.OTelGenAiAttributes;
import dev.langchain4j.micrometer.metrics.conventions.OTelGenAiMetricName;
import dev.langchain4j.micrometer.metrics.conventions.OTelGenAiOperationName;
import dev.langchain4j.micrometer.metrics.conventions.OTelGenAiProviderName;
import dev.langchain4j.micrometer.metrics.conventions.OTelGenAiTokenType;
import dev.langchain4j.model.embedding.listener.EmbeddingModelErrorContext;
import dev.langchain4j.model.embedding.listener.EmbeddingModelListener;
import dev.langchain4j.model.embedding.listener.EmbeddingModelRequestContext;
import dev.langchain4j.model.embedding.listener.EmbeddingModelResponseContext;
import dev.langchain4j.model.output.TokenUsage;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * An {@link EmbeddingModelListener} that uses a Micrometer {@link MeterRegistry} to collect metrics
 * about embedding model interactions following OpenTelemetry Semantic Conventions for Generative AI.
 * <p>
 * This listener records token usage metrics when an embedding model response is received, using a
 * {@link DistributionSummary} consistent with the
 * <a href="https://opentelemetry.io/docs/specs/semconv/gen-ai/gen-ai-metrics/#metric-gen_aiclienttokenusage">
 * OpenTelemetry Semantic Conventions for {@code gen_ai.client.token.usage}</a>.
 * Embedding calls have no output tokens, so {@code gen_ai.token.type} is always {@code input}.
 * <p>
 * It also records the duration of every embedding operation as {@code gen_ai.client.operation.duration} using a
 * {@link Timer}, consistent with the
 * <a href="https://opentelemetry.io/docs/specs/semconv/gen-ai/gen-ai-metrics/#metric-gen_aiclientoperationduration">
 * OpenTelemetry Semantic Conventions for {@code gen_ai.client.operation.duration}</a>.
 * The duration is recorded for both successful and failed calls, with an {@code outcome} tag set to
 * {@code SUCCESS} or {@code ERROR}, and an {@code error.type} tag set to {@code none} or to the class name of the
 * exception. A failed call has no response, so its {@code gen_ai.response.model} is {@code unknown}.
 * Every duration carries the same set of tags, as some registries (e.g., Prometheus) require that.
 * <p>
 * Histogram publishing and bucket boundaries are not configured by this listener.
 * Users can enable histograms and set bucket boundaries through their {@link MeterRegistry} configuration
 * (e.g., via Spring Boot properties or {@link io.micrometer.core.instrument.config.MeterFilter}).
 * <p>
 * Note: The {@link MicrometerMetricsEmbeddingModelListener}
 * must be instantiated separately (e.g., via Spring Boot auto-configuration or manual instantiation).
 */
@Experimental
public class MicrometerMetricsEmbeddingModelListener implements EmbeddingModelListener {

    /**
     * Key under which {@link #onRequest} stores the start timestamp, so that {@link #onResponse} and
     * {@link #onError} can turn it into a duration. The key is namespaced because the attributes map is shared
     * between listeners and can be pre-populated by the caller.
     */
    private static final String START_TIME_KEY = "micrometer.metrics.embeddings.startTime";

    private static final String OUTCOME = "outcome";
    private static final String OUTCOME_SUCCESS = "SUCCESS";
    private static final String OUTCOME_ERROR = "ERROR";

    private final MeterRegistry meterRegistry;

    /**
     * Creates a new {@link MicrometerMetricsEmbeddingModelListener}.
     *
     * @param meterRegistry the {@link MeterRegistry} to register metrics with
     */
    public MicrometerMetricsEmbeddingModelListener(MeterRegistry meterRegistry) {
        this.meterRegistry = ensureNotNull(meterRegistry, "meterRegistry");
    }

    @Override
    public void onRequest(EmbeddingModelRequestContext requestContext) {
        requestContext.attributes().put(START_TIME_KEY, System.nanoTime());
    }

    @Override
    public void onResponse(EmbeddingModelResponseContext responseContext) {
        Long startNanos = takeStartNanos(responseContext.attributes());
        if (startNanos != null) {
            recordDuration(
                    startNanos,
                    getProviderName(responseContext),
                    getRequestModelName(responseContext),
                    getResponseModelName(responseContext),
                    OUTCOME_SUCCESS,
                    "none");
        }

        recordTokenUsageMetrics(responseContext);
    }

    @Override
    public void onError(EmbeddingModelErrorContext errorContext) {
        Long startNanos = takeStartNanos(errorContext.attributes());
        if (startNanos == null) {
            return;
        }

        Throwable error = errorContext.error();
        recordDuration(
                startNanos,
                OTelGenAiProviderName.fromModelProvider(errorContext.modelProvider()),
                getOrDefault(errorContext.embeddingRequest().modelName(), "unknown"),
                "unknown",
                OUTCOME_ERROR,
                error != null ? error.getClass().getName() : "unknown");
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

    private void recordDuration(
            long startNanos,
            String providerName,
            String requestModelName,
            String responseModelName,
            String outcome,
            String errorType) {
        Timer.builder(OTelGenAiMetricName.OPERATION_DURATION.value())
                .description("Measures operation duration")
                .tag(OTelGenAiAttributes.OPERATION_NAME.value(), OTelGenAiOperationName.EMBEDDINGS.value())
                .tag(OTelGenAiAttributes.PROVIDER_NAME.value(), providerName)
                .tag(OTelGenAiAttributes.REQUEST_MODEL.value(), requestModelName)
                .tag(OTelGenAiAttributes.RESPONSE_MODEL.value(), responseModelName)
                .tag(OUTCOME, outcome)
                .tag(OTelGenAiAttributes.ERROR_TYPE.value(), errorType)
                .register(meterRegistry)
                .record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
    }

    private void recordTokenUsageMetrics(EmbeddingModelResponseContext responseContext) {
        if (responseContext == null || responseContext.embeddingResponse().tokenUsage() == null) return;

        TokenUsage tokenUsage = responseContext.embeddingResponse().tokenUsage();
        Integer inputTokenCount = tokenUsage.inputTokenCount();
        if (inputTokenCount == null) {
            // Token counts are nullable (TokenUsage documents each component as "null if unknown").
            // Embedding calls have no output tokens, so input is the only type that can be recorded.
            return;
        }

        DistributionSummary.builder(OTelGenAiMetricName.TOKEN_USAGE.value())
                .baseUnit("tokens")
                .tag(OTelGenAiAttributes.OPERATION_NAME.value(), OTelGenAiOperationName.EMBEDDINGS.value())
                .tag(OTelGenAiAttributes.PROVIDER_NAME.value(), getProviderName(responseContext))
                .tag(OTelGenAiAttributes.REQUEST_MODEL.value(), getRequestModelName(responseContext))
                .tag(OTelGenAiAttributes.RESPONSE_MODEL.value(), getResponseModelName(responseContext))
                .tag(OTelGenAiAttributes.TOKEN_TYPE.value(), OTelGenAiTokenType.INPUT.value())
                .description("Measures token usage")
                .register(meterRegistry)
                .record(inputTokenCount);
    }

    private static String getProviderName(EmbeddingModelResponseContext responseContext) {
        return OTelGenAiProviderName.fromModelProvider(responseContext.modelProvider());
    }

    private static String getRequestModelName(EmbeddingModelResponseContext responseContext) {
        String modelName = responseContext.embeddingRequest().modelName();
        return getOrDefault(modelName, "unknown");
    }

    private static String getResponseModelName(EmbeddingModelResponseContext responseContext) {
        String modelName = responseContext.embeddingResponse().modelName();
        return getOrDefault(modelName, "unknown");
    }
}
