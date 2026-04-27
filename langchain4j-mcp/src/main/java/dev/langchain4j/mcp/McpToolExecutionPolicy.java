package dev.langchain4j.mcp;

import static dev.langchain4j.internal.ValidationUtils.ensureNonNegative;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Policy for controlling tool call execution lifecycle.
 * <p>
 * This policy enables:
 * <ul>
 *   <li>Limiting the number of tool calls per LLM turn</li>
 *   <li>Detecting and preventing duplicate tool calls (same name + args)</li>
 *   <li>Pre-execution hooks for validation/transformation</li>
 * </ul>
 *
 * @since 1.14.0
 */
public class McpToolExecutionPolicy {

    /**
     * Maximum number of tool calls allowed per LLM turn.
     * A value of 0 or negative means unlimited.
     */
    private final int maxCallsPerTurn;

    /**
     * Whether to prevent duplicate tool calls.
     * A duplicate is defined as a call with the same tool name and arguments.
     */
    private final boolean duplicatePrevention;

    /**
     * Optional hook executed before each tool call execution.
     * Can be used for validation, transformation, logging, etc.
     */
    private final Consumer<ToolExecutionRequest> preExecutionHook;

    /**
     * Optional transformer for tool execution results.
     */
    private final Function<String, String> resultTransformer;

    private McpToolExecutionPolicy(Builder builder) {
        this.maxCallsPerTurn = ensureNonNegative(builder.maxCallsPerTurn, "maxCallsPerTurn");
        this.duplicatePrevention = builder.duplicatePrevention;
        this.preExecutionHook = builder.preExecutionHook;
        this.resultTransformer = builder.resultTransformer;
    }

    /**
     * Returns the maximum number of tool calls allowed per turn.
     *
     * @return max calls per turn, or 0 if unlimited
     */
    public int maxCallsPerTurn() {
        return maxCallsPerTurn;
    }

    /**
     * Returns whether duplicate prevention is enabled.
     *
     * @return true if duplicate prevention is enabled
     */
    public boolean isDuplicatePreventionEnabled() {
        return duplicatePrevention;
    }

    /**
     * Returns the pre-execution hook, if configured.
     *
     * @return the hook or null if not configured
     */
    public Consumer<ToolExecutionRequest> preExecutionHook() {
        return preExecutionHook;
    }

    /**
     * Returns the result transformer, if configured.
     *
     * @return the transformer or null if not configured
     */
    public Function<String, String> resultTransformer() {
        return resultTransformer;
    }

    /**
     * Creates a new builder for {@link McpToolExecutionPolicy}.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a builder pre-configured with default values that provide
     * sensible defaults for most use cases:
     * <ul>
     *   <li>Unlimited calls per turn (maxCallsPerTurn = 0)</li>
     *   <li>Duplicate prevention enabled</li>
     *   <li>No pre-execution hook</li>
     *   <li>No result transformer</li>
     * </ul>
     *
     * @return a pre-configured builder instance
     */
    public static Builder builderWithDefaults() {
        return builder()
                .maxCallsPerTurn(0) // unlimited
                .duplicatePrevention(true);
    }

    /**
     * {@code McpToolExecutionPolicy} builder static inner class.
     */
    public static final class Builder {

        private int maxCallsPerTurn = 0; // unlimited by default
        private boolean duplicatePrevention = false;
        private Consumer<ToolExecutionRequest> preExecutionHook;
        private Function<String, String> resultTransformer;

        /**
         * Sets the maximum number of tool calls allowed per LLM turn.
         * Set to 0 (default) for unlimited.
         *
         * @param maxCallsPerTurn maximum calls per turn, must be non-negative
         * @return this builder
         */
        public Builder maxCallsPerTurn(int maxCallsPerTurn) {
            this.maxCallsPerTurn = maxCallsPerTurn;
            return this;
        }

        /**
         * Enables or disables duplicate prevention.
         * When enabled, repeated calls with identical tool name and arguments
         * will be skipped.
         *
         * @param duplicatePrevention true to enable duplicate prevention
         * @return this builder
         */
        public Builder duplicatePrevention(boolean duplicatePrevention) {
            this.duplicatePrevention = duplicatePrevention;
            return this;
        }

        /**
         * Sets a hook that runs before each tool call execution.
         * Can be used for validation, logging, argument transformation, etc.
         * <p>
         * If the hook throws an exception, the tool execution will be aborted
         * and an error result will be returned.
         *
         * @param preExecutionHook the hook to execute before tool execution
         * @return this builder
         */
        public Builder preExecutionHook(Consumer<ToolExecutionRequest> preExecutionHook) {
            this.preExecutionHook = preExecutionHook;
            return this;
        }

        /**
         * Sets a transformer for tool execution results.
         * Can be used for logging, result modification, etc.
         *
         * @param resultTransformer the transformer to apply to results
         * @return this builder
         */
        public Builder resultTransformer(Function<String, String> resultTransformer) {
            this.resultTransformer = resultTransformer;
            return this;
        }

        /**
         * Returns a {@code McpToolExecutionPolicy} built from the parameters previously set.
         *
         * @return a new {@code McpToolExecutionPolicy} instance
         */
        public McpToolExecutionPolicy build() {
            return new McpToolExecutionPolicy(this);
        }
    }
}
