package dev.langchain4j.model.anthropic.internal.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * Metadata for a batch job returned by the Anthropic Message Batches API
 * (create, retrieve, cancel, and each entry of the list endpoint).
 *
 * <p>{@link #processingStatus} is one of {@code in_progress}, {@code canceling}, or {@code ended}. A canceled
 * batch also ends as {@code ended}, so {@link #cancelInitiatedAt} is what distinguishes a canceled batch from
 * one that ran to completion. Per-request outcomes are not carried here; they are fetched separately from the
 * batch's results endpoint once the batch has ended.</p>
 *
 * <p>Only the fields consumed by {@code AnthropicBatchChatModel} are declared; the remaining
 * response fields are ignored via {@link JsonIgnoreProperties}.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public class AnthropicBatch {

    public String id;
    public String processingStatus;
    public String cancelInitiatedAt;
}
