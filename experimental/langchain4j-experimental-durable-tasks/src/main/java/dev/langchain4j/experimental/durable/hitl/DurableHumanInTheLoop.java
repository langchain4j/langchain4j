package dev.langchain4j.experimental.durable.hitl;

import dev.langchain4j.Experimental;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.internal.AgentSpecsProvider;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.experimental.durable.task.TaskPausedException;

/**
 * A durable variant of HumanInTheLoop that pauses the task when human input is needed.
 *
 * <p>On first invocation, this agent checks whether the scope already contains the
 * expected output (indicating this is a resume after the human provided input). If so,
 * it returns the value from the scope. Otherwise, it throws {@link TaskPausedException}
 * to signal that the task should be paused and persisted.
 *
 * <p>The task service catches the exception, checkpoints the task in PAUSED status,
 * and waits for the application to call {@code resume(taskId, userInput)} with the
 * human's answer. On resume, the answer is written to the scope under the
 * {@link #outputKey()} before re-entering the agent topology, so this agent
 * finds it and returns it immediately.
 *
 * <p>Usage:
 * <pre>{@code
 * DurableHumanInTheLoop humanAgent = DurableHumanInTheLoop.builder()
 *         .outputKey("userApproval")
 *         .reason("Waiting for manager approval")
 *         .build();
 * }</pre>
 *
 * <p>No changes to the core {@code langchain4j-agentic} module are required.
 */
@Experimental
public record DurableHumanInTheLoop(
        String outputKey, String description, boolean async, String reason, AgentListener listener)
        implements AgentSpecsProvider {

    /**
     * Agent method invoked by the planner. Returns existing human input from
     * the scope if present (resume path), or throws {@link TaskPausedException}
     * to pause the task and wait for input (initial path).
     *
     * @param scope the current agentic scope
     * @return the human-provided input from the scope
     * @throws TaskPausedException if human input is not yet available
     */
    @Agent("A durable agent that pauses the task to wait for human input")
    public Object askUser(AgenticScope scope) {
        if (scope.hasState(outputKey)) {
            return scope.readState(outputKey);
        }
        throw new TaskPausedException(reason != null ? reason : "Waiting for human input", outputKey);
    }

    /**
     * Creates a new builder for {@link DurableHumanInTheLoop}.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link DurableHumanInTheLoop}.
     */
    public static final class Builder {

        private String outputKey = "humanInput";
        private String description = "A durable agent that pauses the task to wait for human input";
        private boolean async = false;
        private String reason;
        private AgentListener agentListener;

        private Builder() {}

        /**
         * Sets the scope key where the human input will be stored.
         * Defaults to {@code "humanInput"}.
         *
         * @param outputKey the scope key
         * @return this builder
         */
        public Builder outputKey(String outputKey) {
            this.outputKey = outputKey;
            return this;
        }

        /**
         * Sets the description for this agent.
         *
         * @param description the description
         * @return this builder
         */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /**
         * Sets whether this agent runs asynchronously.
         *
         * @param async true for async execution
         * @return this builder
         */
        public Builder async(boolean async) {
            this.async = async;
            return this;
        }

        /**
         * Sets the human-readable reason for pausing. Included in the
         * {@link TaskPausedException} message.
         *
         * @param reason the reason for pausing
         * @return this builder
         */
        public Builder reason(String reason) {
            this.reason = reason;
            return this;
        }

        /**
         * Sets an agent listener.
         *
         * @param listener the listener
         * @return this builder
         */
        public Builder listener(AgentListener listener) {
            this.agentListener = listener;
            return this;
        }

        /**
         * Builds the {@link DurableHumanInTheLoop}.
         *
         * @return a new durable human-in-the-loop agent
         */
        public DurableHumanInTheLoop build() {
            return new DurableHumanInTheLoop(outputKey, description, async, reason, agentListener);
        }
    }
}
