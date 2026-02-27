package dev.langchain4j.experimental.durable.task;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.Experimental;
import dev.langchain4j.internal.Utils;

/**
 * Unique identifier for a durable long-lived task.
 *
 * <p>Wraps a non-blank string value, typically a UUID. Provides type safety to prevent
 * accidental mixing with other string identifiers such as memoryId or agentId.
 */
@Experimental
public record TaskId(@JsonProperty("value") String value) {

    @JsonCreator
    public TaskId(@JsonProperty("value") String value) {
        this.value = ensureNotBlank(value, "taskId value");
    }

    /**
     * Creates a new TaskId with a random UUID value.
     *
     * @return a new unique TaskId
     */
    public static TaskId random() {
        return new TaskId(Utils.randomUUID());
    }

    @Override
    public String toString() {
        return value;
    }
}
