package dev.langchain4j.experimental.durable.store.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.Experimental;
import dev.langchain4j.experimental.durable.task.TaskId;
import java.time.Instant;

/**
 * Recorded when a failed task attempt is retried automatically.
 *
 * @param taskId       the task identifier
 * @param timestamp    when the retry was scheduled
 * @param attempt      the retry attempt number (1-based)
 * @param maxRetries   the configured maximum retries
 * @param errorMessage the error message from the failed attempt
 * @param delayMillis  the delay in milliseconds before this retry
 */
@Experimental
public record TaskRetryEvent(
        TaskId taskId, Instant timestamp, int attempt, int maxRetries, String errorMessage, long delayMillis)
        implements TaskEvent {

    @JsonCreator
    public TaskRetryEvent(
            @JsonProperty("taskId") TaskId taskId,
            @JsonProperty("timestamp") Instant timestamp,
            @JsonProperty("attempt") int attempt,
            @JsonProperty("maxRetries") int maxRetries,
            @JsonProperty("errorMessage") String errorMessage,
            @JsonProperty("delayMillis") long delayMillis) {
        this.taskId = taskId;
        this.timestamp = timestamp;
        this.attempt = attempt;
        this.maxRetries = maxRetries;
        this.errorMessage = errorMessage;
        this.delayMillis = delayMillis;
    }
}
