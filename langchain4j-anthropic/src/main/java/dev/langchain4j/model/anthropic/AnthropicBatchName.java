package dev.langchain4j.model.anthropic;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

/**
 * Represents an Anthropic Message Batch identifier.
 *
 * <p>Batch IDs follow the format {@code msgbatch_XXXXX} where XXXXX is a unique identifier.</p>
 *
 * @param id the batch identifier string
 */
public record AnthropicBatchName(String id) {

    public AnthropicBatchName {
        ensureNotBlank(id, "id");
        if (!id.startsWith("msgbatch_")) {
            throw new IllegalArgumentException("Invalid batch ID format. Expected format: msgbatch_XXXXX, got: " + id);
        }
    }

    /**
     * Creates a new batch name from a string ID.
     *
     * @param id the batch ID string
     * @return a new {@link AnthropicBatchName}
     */
    public static AnthropicBatchName of(String id) {
        return new AnthropicBatchName(id);
    }

    @Override
    public String toString() {
        return id;
    }
}
