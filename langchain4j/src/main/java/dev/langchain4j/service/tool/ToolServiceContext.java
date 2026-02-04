package dev.langchain4j.service.tool;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolSpecification;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import dev.langchain4j.model.chat.request.ChatRequest;

import static dev.langchain4j.internal.Utils.copy;

@Internal
public class ToolServiceContext {

    private final List<ToolSpecification> toolSpecifications;
    private final List<ToolSpecification> availableToolSpecifications;
    private final Map<String, ToolExecutor> toolExecutors;
    private final Set<String> immediateReturnTools;

    public ToolServiceContext(Builder builder) {
        this.toolSpecifications = copy(builder.toolSpecifications);
        this.availableToolSpecifications = copy(builder.availableToolSpecifications);
        this.toolExecutors = copy(builder.toolExecutors);
        this.immediateReturnTools = copy(builder.immediateReturnTools);
    }

    public ToolServiceContext(List<ToolSpecification> toolSpecifications, Map<String, ToolExecutor> toolExecutors) {
        this.toolSpecifications = copy(toolSpecifications);
        this.availableToolSpecifications = copy(toolSpecifications);
        this.toolExecutors = copy(toolExecutors);
        this.immediateReturnTools = Set.of();
    }

    /**
     * Returns <b>effective</b> tool specifications that should be included in the next {@link ChatRequest}.
     *
     * @see #availableToolSpecifications()
     */
    public List<ToolSpecification> toolSpecifications() { // TODO deprecate and rename into effectiveToolSpecifications?
        return toolSpecifications;
    }

    /**
     * Returns <b>all available</b> tool specifications configured for AI service.
     * These tool specifications can be discovered/found by the LLM (see {@link ToolSearchStrategy})
     * and included in the next {@link ChatRequest}.
     *
     * @see #toolSpecifications()
     * @since 1.12.0
     */
    public List<ToolSpecification> availableToolSpecifications() { // TODO name
        return availableToolSpecifications;
    }

    public Map<String, ToolExecutor> toolExecutors() {
        return toolExecutors;
    }

    public Set<String> immediateReturnTools() {
        return immediateReturnTools;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ToolServiceContext that = (ToolServiceContext) o;
        return Objects.equals(toolSpecifications, that.toolSpecifications)
                && Objects.equals(availableToolSpecifications, that.availableToolSpecifications)
                && Objects.equals(toolExecutors, that.toolExecutors)
                && Objects.equals(immediateReturnTools, that.immediateReturnTools);
    }

    @Override
    public int hashCode() {
        return Objects.hash(toolSpecifications, availableToolSpecifications, toolExecutors, immediateReturnTools);
    }

    @Override
    public String toString() {
        return "ToolServiceContext{" +
                "toolSpecifications=" + toolSpecifications +
                ", availableToolSpecifications=" + availableToolSpecifications +
                ", toolExecutors=" + toolExecutors +
                ", immediateReturnTools=" + immediateReturnTools +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private List<ToolSpecification> toolSpecifications;
        private List<ToolSpecification> availableToolSpecifications;
        private Map<String, ToolExecutor> toolExecutors;
        private Set<String> immediateReturnTools;

        /**
         * Sets <b>effective</b> tool specifications that should be included in the next {@link ChatRequest}.
         *
         * @see #availableToolSpecifications()
         */
        public Builder toolSpecifications(List<ToolSpecification> toolSpecifications) {
            this.toolSpecifications = toolSpecifications;
            return this;
        }

        /**
         * Sets <b>all available</b> tool specifications configured for AI service.
         * These tool specifications can be discovered/found by the LLM (see {@link ToolSearchStrategy})
         * and included in the next {@link ChatRequest}.
         *
         * @see #toolSpecifications()
         * @since 1.12.0
         */
        public Builder availableToolSpecifications(List<ToolSpecification> availableToolSpecifications) { // TODO name
            this.availableToolSpecifications = availableToolSpecifications;
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
