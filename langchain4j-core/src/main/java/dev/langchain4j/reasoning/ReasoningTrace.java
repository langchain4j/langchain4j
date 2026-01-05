package dev.langchain4j.reasoning;

import static dev.langchain4j.internal.Utils.quoted;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.document.Metadata;
import java.util.Objects;

/**
 * Represents a reasoning trace captured from an agent's task execution.
 * A reasoning trace contains the thinking process, intermediate steps,
 * and insights generated while solving a task.
 * <p>
 * This is the raw material from which {@link ReasoningStrategy} instances
 * are distilled for storage in a {@link ReasoningBank}.
 *
 * @since 1.11.0
 */
@Experimental
public class ReasoningTrace {

    private final String taskDescription;
    private final String thinking;
    private final String solution;
    private final boolean successful;
    private final Metadata metadata;

    /**
     * Creates a new reasoning trace.
     *
     * @param taskDescription A description of the task that was attempted.
     * @param thinking        The reasoning/thinking process captured during execution.
     * @param solution        The final solution or answer produced.
     * @param successful      Whether the task was completed successfully.
     * @param metadata        Additional metadata about the trace.
     */
    public ReasoningTrace(
            String taskDescription, String thinking, String solution, boolean successful, Metadata metadata) {
        this.taskDescription = ensureNotBlank(taskDescription, "taskDescription");
        this.thinking = thinking;
        this.solution = solution;
        this.successful = successful;
        this.metadata = metadata != null ? metadata : new Metadata();
    }

    /**
     * Returns the task description.
     *
     * @return The task description.
     */
    public String taskDescription() {
        return taskDescription;
    }

    /**
     * Returns the thinking/reasoning process.
     *
     * @return The thinking content, may be null if not captured.
     */
    public String thinking() {
        return thinking;
    }

    /**
     * Returns the solution produced.
     *
     * @return The solution, may be null if task failed.
     */
    public String solution() {
        return solution;
    }

    /**
     * Returns whether the task was successful.
     *
     * @return true if successful, false otherwise.
     */
    public boolean isSuccessful() {
        return successful;
    }

    /**
     * Returns the metadata associated with this trace.
     *
     * @return The metadata.
     */
    public Metadata metadata() {
        return metadata;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReasoningTrace that = (ReasoningTrace) o;
        return successful == that.successful
                && Objects.equals(taskDescription, that.taskDescription)
                && Objects.equals(thinking, that.thinking)
                && Objects.equals(solution, that.solution)
                && Objects.equals(metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(taskDescription, thinking, solution, successful, metadata);
    }

    @Override
    public String toString() {
        return "ReasoningTrace{" + "taskDescription="
                + quoted(taskDescription) + ", thinking="
                + quoted(thinking) + ", solution="
                + quoted(solution) + ", successful="
                + successful + ", metadata="
                + metadata + '}';
    }

    /**
     * Creates a new builder for constructing ReasoningTrace instances.
     *
     * @return A new builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a successful reasoning trace with the given parameters.
     *
     * @param taskDescription The task description.
     * @param thinking        The thinking process.
     * @param solution        The solution produced.
     * @return A new successful ReasoningTrace.
     */
    public static ReasoningTrace successful(String taskDescription, String thinking, String solution) {
        return builder()
                .taskDescription(taskDescription)
                .thinking(thinking)
                .solution(solution)
                .successful(true)
                .build();
    }

    /**
     * Creates a failed reasoning trace with the given parameters.
     *
     * @param taskDescription The task description.
     * @param thinking        The thinking process before failure.
     * @return A new failed ReasoningTrace.
     */
    public static ReasoningTrace failed(String taskDescription, String thinking) {
        return builder()
                .taskDescription(taskDescription)
                .thinking(thinking)
                .successful(false)
                .build();
    }

    /**
     * Builder for constructing ReasoningTrace instances.
     */
    public static class Builder {

        private String taskDescription;
        private String thinking;
        private String solution;
        private boolean successful = true;
        private Metadata metadata;

        public Builder taskDescription(String taskDescription) {
            this.taskDescription = taskDescription;
            return this;
        }

        public Builder thinking(String thinking) {
            this.thinking = thinking;
            return this;
        }

        public Builder solution(String solution) {
            this.solution = solution;
            return this;
        }

        public Builder successful(boolean successful) {
            this.successful = successful;
            return this;
        }

        public Builder metadata(Metadata metadata) {
            this.metadata = metadata;
            return this;
        }

        public ReasoningTrace build() {
            return new ReasoningTrace(taskDescription, thinking, solution, successful, metadata);
        }
    }
}
