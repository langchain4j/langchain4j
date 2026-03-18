package dev.langchain4j.service.tool;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolSpecification;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * Represents a tool as managed by an AI Service, combining:
 * <ul>
 *     <li>{@link ToolSpecification} — what is sent to the LLM</li>
 *     <li>{@link ToolExecutor} — what is called when the LLM invokes the tool</li>
 *     <li>Metadata that controls how the AI Service handles this tool (e.g., immediate return)</li>
 * </ul>
 *
 * @since 1.13.0
 */
@Internal
public class AiServiceTool {

    private final ToolSpecification toolSpecification;
    private final ToolExecutor toolExecutor;
    private final boolean immediateReturn;

    private AiServiceTool(Builder builder) {
        this.toolSpecification = ensureNotNull(builder.toolSpecification, "toolSpecification");
        this.toolExecutor = ensureNotNull(builder.toolExecutor, "toolExecutor");
        this.immediateReturn = builder.immediateReturn;
    }

    public ToolSpecification toolSpecification() {
        return toolSpecification;
    }

    public ToolExecutor toolExecutor() {
        return toolExecutor;
    }

    public boolean immediateReturn() {
        return immediateReturn;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private ToolSpecification toolSpecification;
        private ToolExecutor toolExecutor;
        private boolean immediateReturn;

        public Builder toolSpecification(ToolSpecification toolSpecification) {
            this.toolSpecification = toolSpecification;
            return this;
        }

        public Builder toolExecutor(ToolExecutor toolExecutor) {
            this.toolExecutor = toolExecutor;
            return this;
        }

        public Builder immediateReturn(boolean immediateReturn) {
            this.immediateReturn = immediateReturn;
            return this;
        }

        public AiServiceTool build() {
            return new AiServiceTool(this);
        }
    }
}
