package dev.langchain4j.model.batch;

/**
 * Represents a unique identifier for a batch operation.
 *
 * <p>A batch name is typically assigned by the API when a batch is created and is used
 * to track, retrieve, cancel, or delete the batch operation. The format of the value
 * depends on the provider (e.g., {@code "batches/abc123"} for Gemini, {@code "msgbatch_xyz"} for Anthropic).</p>
 *
 * @param value the unique identifier string for the batch
 */
public record BatchName(String value) {}
