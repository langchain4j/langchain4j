package dev.langchain4j.service.tool;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolSpecification;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.service.tool.search.ToolSearchStrategy;

import static dev.langchain4j.internal.Utils.copy;

@Internal
public class ToolServiceContext {

    private final List<ToolSpecification> effectiveTools;
    private final List<ToolSpecification> availableTools;
    private final Map<String, ToolExecutor> toolExecutors;
    private final Set<String> immediateReturnTools;

    public ToolServiceContext(Builder builder) {
        this.effectiveTools = copy(builder.effectiveTools);
        this.availableTools = copy(builder.availableTools);
        this.toolExecutors = copy(builder.toolExecutors);
        this.immediateReturnTools = copy(builder.immediateReturnTools);
    }

    /**
     * @deprecated use {@link #ToolServiceContext(Builder)} instead
     */
    @Deprecated(since = "1.12.0")
    public ToolServiceContext(List<ToolSpecification> toolSpecifications, Map<String, ToolExecutor> toolExecutors) {
        this.effectiveTools = copy(toolSpecifications);
        this.availableTools = copy(toolSpecifications);
        this.toolExecutors = copy(toolExecutors);
        this.immediateReturnTools = Set.of();
    }

    /**
     * Returns <b>effective</b> tool specifications that should be included in the next {@link ChatRequest}.
     *
     * @see #availableTools()
     */
    public List<ToolSpecification> effectiveTools() {
        return effectiveTools;
    }

    /**
     * Returns <b>effective</b> tool specifications that should be included in the next {@link ChatRequest}.
     *
     * @see #availableTools()
     * @deprecated use {@link #effectiveTools()} instead
     */
    @Deprecated(since = "1.12.0")
    public List<ToolSpecification> toolSpecifications() {
        return effectiveTools;
    }

    /**
     * Returns <b>all available</b> tool specifications configured for AI service.
     * These tool specifications can be discovered/found by the LLM (see {@link ToolSearchStrategy})
     * and included in the next {@link ChatRequest}.
     *
     * @see #effectiveTools()
     * @since 1.12.0
     */
    public List<ToolSpecification> availableTools() {
        return availableTools;
    }

    public Map<String, ToolExecutor> toolExecutors() {
        return toolExecutors;
    }

    public Set<String> immediateReturnTools() {
        return immediateReturnTools;
    }

    public Builder toBuilder() {
        return builder()
                .effectiveTools(effectiveTools)
                .availableTools(availableTools)
                .toolExecutors(toolExecutors)
                .immediateReturnTools(immediateReturnTools);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ToolServiceContext that = (ToolServiceContext) o;
        return Objects.equals(effectiveTools, that.effectiveTools)
                && Objects.equals(availableTools, that.availableTools)
                && Objects.equals(toolExecutors, that.toolExecutors)
                && Objects.equals(immediateReturnTools, that.immediateReturnTools);
    }

    @Override
    public int hashCode() {
        return Objects.hash(effectiveTools, availableTools, toolExecutors, immediateReturnTools);
    }

    @Override
    public String toString() {
        return "ToolServiceContext{" +
                "effectiveTools=" + effectiveTools +
                ", availableTools=" + availableTools +
                ", toolExecutors=" + toolExecutors +
                ", immediateReturnTools=" + immediateReturnTools +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private List<ToolSpecification> effectiveTools;
        private List<ToolSpecification> availableTools;
        private Map<String, ToolExecutor> toolExecutors;
        private Set<String> immediateReturnTools;

        /**
         * Sets <b>effective</b> tool specifications that should be included in the next {@link ChatRequest}.
         *
         * @see #availableTools()
         */
        public Builder effectiveTools(List<ToolSpecification> effectiveTools) {
            this.effectiveTools = effectiveTools;
            return this;
        }

        /**
         * Sets <b>effective</b> tool specifications that should be included in the next {@link ChatRequest}.
         *
         * @see #availableTools()
         * @deprecated use {@link #effectiveTools(List)} instead
         */
        @Deprecated(since = "1.12.0")
        public Builder toolSpecifications(List<ToolSpecification> toolSpecifications) {
            this.effectiveTools = toolSpecifications;
            return this;
        }

        /**
         * Sets <b>all available</b> tool specifications configured for AI service.
         * These tool specifications can be discovered/found by the LLM (see {@link ToolSearchStrategy})
         * and included in the next {@link ChatRequest}.
         *
         * @see #effectiveTools(List)
         * @since 1.12.0
         */
        public Builder availableTools(List<ToolSpecification> availableTools) {
            this.availableTools = availableTools;
            return this;
        }

        public Builder toolExecutors(Map<String, ToolExecutor> toolExecutors) {
            this.toolExecutors = toolExecutors;
            return this;
        }

        public Builder immediateReturnTools(Set<String> immediateReturnTools) {
            this.immediateReturnTools = immediateReturnTools;
            return this;
        }

        public ToolServiceContext build() {
            return new ToolServiceContext(this);
        }
    }

    public static class Empty extends ToolServiceContext {

        public static final Empty INSTANCE = new Empty();

        private Empty() {
            super(List.of(), Map.of());
        }
    }
}
