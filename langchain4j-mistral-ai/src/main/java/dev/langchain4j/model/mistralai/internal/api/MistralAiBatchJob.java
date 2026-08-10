package dev.langchain4j.model.mistralai.internal.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * Metadata for a batch job returned by the Mistral Batch API
 * (create, retrieve, cancel, and each entry of the list endpoint).
 *
 * <p>{@link #status} is one of {@code QUEUED}, {@code RUNNING}, {@code SUCCESS}, {@code FAILED},
 * {@code TIMEOUT_EXCEEDED}, {@code CANCELLATION_REQUESTED}, or {@code CANCELLED}. Per-request outcomes
 * are not carried here; once the job has completed they are fetched from the file referenced by
 * {@link #outputFile} (and, for provider-level failures, {@link #errorFile}) via the Files API.</p>
 *
 * <p>Only the fields consumed by {@code MistralAiBatchChatModel} are declared; the remaining response
 * fields are ignored via {@link JsonIgnoreProperties}.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public class MistralAiBatchJob {

    public String id;
    public String status;
    public String outputFile;
    public String errorFile;
}
