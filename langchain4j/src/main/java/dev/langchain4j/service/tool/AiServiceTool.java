package dev.langchain4j.service.tool;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ReturnBehavior;
import dev.langchain4j.agent.tool.ToolSource;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.guardrail.ToolInputGuardrail;
import dev.langchain4j.guardrail.ToolOutputGuardrail;
import java.util.List;
import java.util.Objects;

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
public class AiServiceTool {

    private final ToolSpecification toolSpecification;
    private final ToolExecutor toolExecutor;
    private final ReturnBehavior returnBehavior;
    private final ToolSource toolSource;
    private final List<ToolInputGuardrail> toolInputGuardrails;
    private final List<ToolOutputGuardrail> toolOutputGuardrails;

    private AiServiceTool(Builder builder) {
        this.toolSpecification = ensureNotNull(builder.toolSpecification, "toolSpecification");
        this.toolExecutor = ensureNotNull(builder.toolExecutor, "toolExecutor");
        this.returnBehavior = getOrDefault(builder.returnBehavior, ReturnBehavior.TO_LLM);
        this.toolSource = getOrDefault(builder.toolSource, ToolSource.programmatic());
        this.toolInputGuardrails = copy(builder.toolInputGuardrails);
        this.toolOutputGuardrails = copy(builder.toolOutputGuardrails);
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

    /**
     * Returns where this tool comes from. It is trusted information, set by the framework or by the application,
     * and exposed to tool guardrails. Defaults to {@link ToolSource#programmatic()}.
     *
     * @since 1.24.0
     */
    @Experimental
    public ToolSource toolSource() {
        return toolSource;
    }

    /**
     * Returns the {@link ToolInputGuardrail}s of this tool. They run after the guardrails set on the
     * {@link dev.langchain4j.service.AiServices AiServices} builder, after the guardrails of the class annotation and
     * after the ones of the method annotation, if any.
     *
     * @since 1.24.0
     */
    @Experimental
    public List<ToolInputGuardrail> toolInputGuardrails() {
        return toolInputGuardrails;
    }

    /**
     * Returns the {@link ToolOutputGuardrail}s of this tool. They run after the guardrails set on the
     * {@link dev.langchain4j.service.AiServices AiServices} builder, after the guardrails of the class annotation and
     * after the ones of the method annotation, if any.
     *
     * @since 1.24.0
     */
    @Experimental
    public List<ToolOutputGuardrail> toolOutputGuardrails() {
        return toolOutputGuardrails;
    }

    /**
     * @deprecated use {@link #returnBehavior()} instead
     */
    @Deprecated(since = "1.14.0")
    public boolean immediateReturn() {
        return returnBehavior == ReturnBehavior.IMMEDIATE;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        AiServiceTool that = (AiServiceTool) o;
        return Objects.equals(toolSpecification, that.toolSpecification)
                && Objects.equals(toolExecutor, that.toolExecutor)
                && returnBehavior == that.returnBehavior
                && Objects.equals(toolSource, that.toolSource)
                && Objects.equals(toolInputGuardrails, that.toolInputGuardrails)
                && Objects.equals(toolOutputGuardrails, that.toolOutputGuardrails);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                toolSpecification, toolExecutor, returnBehavior, toolSource, toolInputGuardrails, toolOutputGuardrails);
    }

    @Override
    public String toString() {
        return "AiServiceTool{" + "toolSpecification="
                + toolSpecification + ", toolExecutor="
                + toolExecutor + ", returnBehavior="
                + returnBehavior + ", toolSource="
                + toolSource + ", toolInputGuardrails="
                + toolInputGuardrails + ", toolOutputGuardrails="
                + toolOutputGuardrails + '}';
    }

    public Builder toBuilder() {
        return builder()
                .toolSpecification(toolSpecification)
                .toolExecutor(toolExecutor)
                .returnBehavior(returnBehavior)
                .toolSource(toolSource)
                .toolInputGuardrails(toolInputGuardrails)
                .toolOutputGuardrails(toolOutputGuardrails);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private ToolSpecification toolSpecification;
        private ToolExecutor toolExecutor;
        private ReturnBehavior returnBehavior;
        private ToolSource toolSource;
        private List<ToolInputGuardrail> toolInputGuardrails;
        private List<ToolOutputGuardrail> toolOutputGuardrails;

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
         * Sets where this tool comes from. It is exposed to tool guardrails as trusted information, so it must be set
         * by the application, never derived from remote data. Defaults to {@link ToolSource#programmatic()}.
         *
         * @since 1.24.0
         */
        @Experimental
        public Builder toolSource(ToolSource toolSource) {
            this.toolSource = toolSource;
            return this;
        }

        /**
         * Sets the {@link ToolInputGuardrail}s of this tool, which run in the order given, after the guardrails set
         * on the {@link dev.langchain4j.service.AiServices AiServices} builder, after the ones of the class
         * annotation and after the ones of the method annotation, if any. {@code null} means none.
         *
         * @since 1.24.0
         */
        @Experimental
        public Builder toolInputGuardrails(List<? extends ToolInputGuardrail> toolInputGuardrails) {
            this.toolInputGuardrails = (toolInputGuardrails != null) ? List.copyOf(toolInputGuardrails) : null;
            return this;
        }

        /**
         * Sets the {@link ToolInputGuardrail}s of this tool.
         *
         * @see #toolInputGuardrails(List)
         * @since 1.24.0
         */
        @Experimental
        public Builder toolInputGuardrails(ToolInputGuardrail... toolInputGuardrails) {
            return toolInputGuardrails((toolInputGuardrails != null) ? List.of(toolInputGuardrails) : null);
        }

        /**
         * Sets the {@link ToolOutputGuardrail}s of this tool, which run in the order given, after the guardrails set
         * on the {@link dev.langchain4j.service.AiServices AiServices} builder, after the ones of the class
         * annotation and after the ones of the method annotation, if any. {@code null} means none.
         *
         * @since 1.24.0
         */
        @Experimental
        public Builder toolOutputGuardrails(List<? extends ToolOutputGuardrail> toolOutputGuardrails) {
            this.toolOutputGuardrails = (toolOutputGuardrails != null) ? List.copyOf(toolOutputGuardrails) : null;
            return this;
        }

        /**
         * Sets the {@link ToolOutputGuardrail}s of this tool.
         *
         * @see #toolOutputGuardrails(List)
         * @since 1.24.0
         */
        @Experimental
        public Builder toolOutputGuardrails(ToolOutputGuardrail... toolOutputGuardrails) {
            return toolOutputGuardrails((toolOutputGuardrails != null) ? List.of(toolOutputGuardrails) : null);
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
