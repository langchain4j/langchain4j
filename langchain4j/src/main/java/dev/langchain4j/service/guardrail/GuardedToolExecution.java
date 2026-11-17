package dev.langchain4j.service.guardrail;

import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.service.tool.ToolExecutionResult;

/**
 * The outcome of a tool call executed through its tool guardrails by
 * {@link ToolGuardrailService#executeGuardrails(ToolGuardrailService.ToolKey, ToolExecutionRequest,
 * dev.langchain4j.agent.tool.ToolSpecification, dev.langchain4j.agent.tool.ToolSource,
 * dev.langchain4j.guardrail.GuardrailRequestParams, java.util.function.Function)}.
 * <p>
 *     An input guardrail can rewrite the arguments of the call. The rewritten request replaces the one of the LLM for
 *     everything that follows, the way a rewritten user message does for the AI service guardrails: report the call
 *     with {@link #request()}, not with the request the LLM sent.
 * </p>
 *
 * @since 1.24.0
 */
@Experimental
public sealed interface GuardedToolExecution permits DefaultGuardedToolExecution {

    /**
     * Creates a builder.
     *
     * @return A new builder.
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a builder initialized with the values of this execution.
     *
     * @return A new builder.
     */
    default Builder toBuilder() {
        return new Builder(this);
    }

    /**
     * The tool call as the input guardrails left it: the request of the LLM, with the arguments rewritten by the input
     * guardrails if they rewrote them. For a call an input guardrail rejected, the arguments as the guardrails that
     * ran before the rejection left them.
     *
     * @return The request, never {@code null}.
     */
    ToolExecutionRequest request();

    /**
     * The result to send to the LLM: the result of the tool as the output guardrails left it, or the error result of
     * a guardrail failure (whose text is the failure message, verbatim).
     *
     * @return The result, never {@code null}.
     */
    ToolExecutionResult result();

    /**
     * Builds a {@link GuardedToolExecution}.
     *
     * @since 1.24.0
     */
    @Experimental
    final class Builder {
        ToolExecutionRequest request;
        ToolExecutionResult result;

        private Builder() {}

        private Builder(GuardedToolExecution execution) {
            this.request = execution.request();
            this.result = execution.result();
        }

        /**
         * @param request The tool call as the input guardrails left it. Must not be null.
         * @return This builder.
         */
        public Builder request(ToolExecutionRequest request) {
            this.request = request;
            return this;
        }

        /**
         * @param result The result to send to the LLM. Must not be null.
         * @return This builder.
         */
        public Builder result(ToolExecutionResult result) {
            this.result = result;
            return this;
        }

        /**
         * @return The execution.
         * @throws IllegalArgumentException if the request or the result is missing
         */
        public GuardedToolExecution build() {
            return new DefaultGuardedToolExecution(this);
        }
    }
}
