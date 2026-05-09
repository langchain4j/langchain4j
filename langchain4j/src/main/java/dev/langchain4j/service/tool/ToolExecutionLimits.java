package dev.langchain4j.service.tool;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNegative;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Configures per-tool execution limits and the behavior applied when a limit is exceeded.
 * <p>
 * Supplied to
 * {@link dev.langchain4j.service.AiServices#toolExecutionLimits(ToolExecutionLimits)} to constrain
 * how many times any individual tool may be executed within one AI service call.
 * <p>
 * Unspecified tools fall back to {@link #defaultLimit()} (default: unlimited).
 * Unspecified behaviors fall back to {@link #defaultBehavior()}
 * (default: {@link ToolLimitExceededBehavior#CONTINUE}).
 *
 * @since 1.14.0
 */
public class ToolExecutionLimits {

    private final int defaultLimit;
    private final Map<String, Integer> perToolLimits;
    private final ToolLimitExceededBehavior defaultBehavior;
    private final Map<String, ToolLimitExceededBehavior> perToolBehaviors;

    private ToolExecutionLimits(Builder builder) {
        this.defaultLimit = builder.defaultLimit;
        this.perToolLimits = Map.copyOf(builder.perToolLimits);
        this.defaultBehavior = builder.defaultBehavior;
        this.perToolBehaviors = Map.copyOf(builder.perToolBehaviors);
    }

    public int defaultLimit() {
        return defaultLimit;
    }

    public Map<String, Integer> perToolLimits() {
        return perToolLimits;
    }

    public ToolLimitExceededBehavior defaultBehavior() {
        return defaultBehavior;
    }

    public Map<String, ToolLimitExceededBehavior> perToolBehaviors() {
        return perToolBehaviors;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private int defaultLimit = Integer.MAX_VALUE;
        private final Map<String, Integer> perToolLimits = new HashMap<>();
        private ToolLimitExceededBehavior defaultBehavior = ToolLimitExceededBehavior.CONTINUE;
        private final Map<String, ToolLimitExceededBehavior> perToolBehaviors = new HashMap<>();

        /**
         * Sets the default maximum number of executions applied to any tool that does not have
         * an explicit override via {@link #maxExecutions(String, int)}.
         * <p>
         * If left unset, tools without an explicit limit are unlimited.
         *
         * @param defaultLimit the default per-tool execution limit (non-negative)
         */
        public Builder defaultLimit(int defaultLimit) {
            ensureNotNegative(defaultLimit, "defaultLimit");
            this.defaultLimit = defaultLimit;
            return this;
        }

        /**
         * Sets the maximum number of executions for a specific tool.
         * Overrides {@link #defaultLimit(int)} for that tool.
         */
        public Builder maxExecutions(String toolName, int limit) {
            ensureNotNull(toolName, "toolName");
            ensureNotNegative(limit, "limit");
            this.perToolLimits.put(toolName, limit);
            return this;
        }

        /**
         * Sets the maximum number of executions for a specific tool together with the behavior
         * to apply when its limit is exceeded. Overrides both the default limit and default
         * behavior for that tool.
         */
        public Builder maxExecutions(String toolName, int limit, ToolLimitExceededBehavior behavior) {
            ensureNotNull(toolName, "toolName");
            ensureNotNegative(limit, "limit");
            ensureNotNull(behavior, "behavior");
            this.perToolLimits.put(toolName, limit);
            this.perToolBehaviors.put(toolName, behavior);
            return this;
        }

        /**
         * Sets the default behavior applied to any tool that does not have an explicit per-tool
         * behavior. Defaults to {@link ToolLimitExceededBehavior#CONTINUE}.
         */
        public Builder defaultBehavior(ToolLimitExceededBehavior behavior) {
            ensureNotNull(behavior, "behavior");
            this.defaultBehavior = behavior;
            return this;
        }

        public ToolExecutionLimits build() {
            return new ToolExecutionLimits(this);
        }
    }
}
