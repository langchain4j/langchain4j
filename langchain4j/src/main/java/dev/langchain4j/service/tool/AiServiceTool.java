package dev.langchain4j.service.tool;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ReturnBehavior;
import dev.langchain4j.agent.tool.ToolSpecification;

import java.util.Objects;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * Represents a tool managed by an AI Service, combining:
 * <ul>
 *     <li>{@link ToolSpecification} — what is sent to the LLM</li>
 *     <li>{@link ToolExecutor} — what is called when the LLM invokes the tool</li>
 *     <li>Metadata that controls how the AI Service handles this tool (e.g., {@link ReturnBehavior})</li>
 * </ul>
 *
 * @since 1.13.0
 */
@Internal
public class AiServiceTool {

    private final ToolSpecification toolSpecification;
    private final ToolExecutor toolExecutor;
    private final ReturnBehavior returnBehavior;

    private AiServiceTool(Builder builder) {
        this.toolSpecification = ensureNotNull(builder.toolSpecification, "toolSpecification");
        this.toolExecutor = ensureNotNull(builder.toolExecutor, "toolExecutor");
        this.returnBehavior = getOrDefault(builder.returnBehavior, ReturnBehavior.TO_LLM);
    }

    public String name() {
        return toolSpecification.name();
    }

    public ToolSpecification toolSpecification() {
        return toolSpecification;
    }

    public ToolExecutor toolExecutor() {
        return toolExecutor;
    }

    /**
     * @since 1.14.0
     */
    public ReturnBehavior returnBehavior() {
        return returnBehavior;
    }

    public boolean immediateReturn() {
        return returnBehavior == ReturnBehavior.IMMEDIATE;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        AiServiceTool that = (AiServiceTool) o;
        return Objects.equals(toolSpecification, that.toolSpecification)
                && Objects.equals(toolExecutor, that.toolExecutor)
                && returnBehavior == that.returnBehavior;
    }

    @Override
    public int hashCode() {
        return Objects.hash(toolSpecification, toolExecutor, returnBehavior);
    }

    @Override
    public String toString() {
        return "AiServiceTool{" +
                "toolSpecification=" + toolSpecification +
                ", toolExecutor=" + toolExecutor +
                ", returnBehavior=" + returnBehavior +
                '}';
    }

    public Builder toBuilder() {
        return builder()
                .toolSpecification(toolSpecification)
                .toolExecutor(toolExecutor)
                .returnBehavior(returnBehavior);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private ToolSpecification toolSpecification;
        private ToolExecutor toolExecutor;
        private ReturnBehavior returnBehavior;

        public Builder toolSpecification(ToolSpecification toolSpecification) {
            this.toolSpecification = toolSpecification;
            return this;
        }

        public Builder toolExecutor(ToolExecutor toolExecutor) {
            this.toolExecutor = toolExecutor;
            return this;
        }

        /**
         * @since 1.14.0
         */
        public Builder returnBehavior(ReturnBehavior returnBehavior) {
            this.returnBehavior = returnBehavior;
            return this;
        }

        /**
         * @deprecated use {@link #returnBehavior(ReturnBehavior)} instead
         */
        @Deprecated(since = "1.14.0")
        public Builder immediateReturn(boolean immediateReturn) {
            if (immediateReturn) {
                this.returnBehavior = ReturnBehavior.IMMEDIATE;
            }
            return this;
        }

        public AiServiceTool build() {
            return new AiServiceTool(this);
        }
    }
}
