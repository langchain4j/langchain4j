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
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import dev.langchain4j.service.AiServices;
import org.jspecify.annotations.Nullable;

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
    // the source given to the builder, null for the default: a copy with another executor gets the default of that one
    private final @Nullable ToolSource explicitToolSource;
    private final List<ToolInputGuardrail> toolInputGuardrails;
    private final List<ToolOutputGuardrail> toolOutputGuardrails;

    private AiServiceTool(Builder builder) {
        this.toolSpecification = ensureNotNull(builder.toolSpecification, "toolSpecification");
        this.toolExecutor = ensureNotNull(builder.toolExecutor, "toolExecutor");
        this.returnBehavior = getOrDefault(builder.returnBehavior, ReturnBehavior.TO_LLM);
        this.toolSource = Optional.ofNullable(builder.toolSource).orElseGet(() -> defaultToolSource(builder.toolExecutor));
        this.explicitToolSource = builder.toolSource;
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
     * The source of a tool registered without one: a tool backed by a {@link DefaultToolExecutor} runs a local
     * method, whatever the way it was registered (a framework can register a proxied {@code @Tool} bean through
     * {@code tools(Map)}), so it is {@link ToolSource#local(java.lang.reflect.Method) local}, with the original method
     * of the executor, which is declared by the class of the application, not by a proxy. Any other tool is
     * {@link ToolSource#programmatic() programmatic}.
     */
    private static ToolSource defaultToolSource(ToolExecutor toolExecutor) {
        return (toolExecutor instanceof DefaultToolExecutor defaultToolExecutor)
                ? ToolSource.local(defaultToolExecutor.originalMethod())
                : ToolSource.programmatic();
    }

    /**
     * Returns where this tool comes from. It is trusted information, set by the framework or by the application,
     * and exposed to tool guardrails. Without an explicit source, a tool backed by a {@link DefaultToolExecutor} is
     * {@link ToolSource#local(java.lang.reflect.Method) local} (the original method of the executor), and any other
     * tool is {@link ToolSource#programmatic() programmatic}.
     *
     * @return where this tool comes from
     * @since 1.25.0
     */
    @Experimental
    public ToolSource toolSource() {
        return toolSource;
    }

    /**
     * Returns the {@link ToolInputGuardrail}s of this tool. They run after the guardrails set on the
     * {@link AiServices} builder, after the guardrails of the class annotation and
     * after the ones of the method annotation, if any.
     *
     * @return the tool input guardrails of this tool, empty if it has none
     * @since 1.25.0
     */
    @Experimental
    public List<ToolInputGuardrail> toolInputGuardrails() {
        return toolInputGuardrails;
    }

    /**
     * Returns the {@link ToolOutputGuardrail}s of this tool. They run after the guardrails set on the
     * {@link AiServices} builder, after the guardrails of the class annotation and
     * after the ones of the method annotation, if any.
     *
     * @return the tool output guardrails of this tool, empty if it has none
     * @since 1.25.0
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
                .toolSource(explicitToolSource)
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
         * by the application, never derived from remote data. When it is not set, a tool backed by a
         * {@link DefaultToolExecutor} is {@link ToolSource#local(java.lang.reflect.Method) local} (the original method
         * of the executor), and any other tool is {@link ToolSource#programmatic() programmatic}.
         *
         * @param toolSource where this tool comes from; {@code null} means the default above
         * @return this builder
         * @since 1.25.0
         */
        @Experimental
        public Builder toolSource(@Nullable ToolSource toolSource) {
            this.toolSource = toolSource;
            return this;
        }

        /**
         * Sets the {@link ToolInputGuardrail}s of this tool, which run in the order given, after the guardrails set
         * on the {@link AiServices} builder, after the ones of the class
         * annotation and after the ones of the method annotation, if any. {@code null} means none.
         *
         * @param toolInputGuardrails the tool input guardrails of this tool, or {@code null} for none
         * @return this builder
         * @since 1.25.0
         */
        @Experimental
        public Builder toolInputGuardrails(@Nullable List<? extends ToolInputGuardrail> toolInputGuardrails) {
            this.toolInputGuardrails = withoutNullEntries(toolInputGuardrails, "toolInputGuardrails");
            return this;
        }

        /**
         * Sets the {@link ToolInputGuardrail}s of this tool.
         *
         * @see #toolInputGuardrails(List)
         * @param toolInputGuardrails the tool input guardrails of this tool
         * @return this builder
         * @since 1.25.0
         */
        @Experimental
        public Builder toolInputGuardrails(ToolInputGuardrail... toolInputGuardrails) {
            return toolInputGuardrails((toolInputGuardrails != null) ? Arrays.asList(toolInputGuardrails) : null);
        }

        /**
         * Sets the {@link ToolOutputGuardrail}s of this tool, which run in the order given, after the guardrails set
         * on the {@link AiServices} builder, after the ones of the class
         * annotation and after the ones of the method annotation, if any. {@code null} means none.
         *
         * @param toolOutputGuardrails the tool output guardrails of this tool, or {@code null} for none
         * @return this builder
         * @since 1.25.0
         */
        @Experimental
        public Builder toolOutputGuardrails(@Nullable List<? extends ToolOutputGuardrail> toolOutputGuardrails) {
            this.toolOutputGuardrails = withoutNullEntries(toolOutputGuardrails, "toolOutputGuardrails");
            return this;
        }

        /**
         * Sets the {@link ToolOutputGuardrail}s of this tool.
         *
         * @see #toolOutputGuardrails(List)
         * @param toolOutputGuardrails the tool output guardrails of this tool
         * @return this builder
         * @since 1.25.0
         */
        @Experimental
        public Builder toolOutputGuardrails(ToolOutputGuardrail... toolOutputGuardrails) {
            return toolOutputGuardrails((toolOutputGuardrails != null) ? Arrays.asList(toolOutputGuardrails) : null);
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

        private static <T> @Nullable List<T> withoutNullEntries(@Nullable List<? extends T> entries, String setting) {
            if (entries == null) {
                return null;
            }
            for (int i = 0; i < entries.size(); i++) {
                ensureNotNull(entries.get(i), "%s[%s] cannot be null", setting, i);
            }
            return List.copyOf(entries);
        }
    }
}
