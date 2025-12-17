package dev.langchain4j.service.tool;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolSpecification;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static dev.langchain4j.internal.Utils.copy;

@Internal
public class ToolServiceContext {

    private final List<ToolSpecification> toolSpecifications;
    private final List<ToolSpecification> allToolSpecifications;
    private final Map<String, ToolExecutor> toolExecutors;
    private final Set<String> immediateReturnTools;

    public ToolServiceContext(Builder builder) {
        this.toolSpecifications = copy(builder.toolSpecifications);
        this.allToolSpecifications = copy(builder.allToolSpecifications);
        this.toolExecutors = copy(builder.toolExecutors);
        this.immediateReturnTools = copy(builder.immediateReturnTools);
    }

    public ToolServiceContext(List<ToolSpecification> toolSpecifications, Map<String, ToolExecutor> toolExecutors) {
        this.toolSpecifications = copy(toolSpecifications);
        this.allToolSpecifications = copy(toolSpecifications);
        this.toolExecutors = copy(toolExecutors);
        this.immediateReturnTools = Set.of();
    }

    public List<ToolSpecification> toolSpecifications() {
        return toolSpecifications;
    }

    /**
     * @since 1.10.0
     */
    public List<ToolSpecification> allToolSpecifications() { // TODO name
        return allToolSpecifications;
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
                && Objects.equals(allToolSpecifications, that.allToolSpecifications)
                && Objects.equals(toolExecutors, that.toolExecutors)
                && Objects.equals(immediateReturnTools, that.immediateReturnTools);
    }

    @Override
    public int hashCode() {
        return Objects.hash(toolSpecifications, allToolSpecifications, toolExecutors, immediateReturnTools);
    }

    @Override
    public String toString() {
        return "ToolServiceContext{" +
                "toolSpecifications=" + toolSpecifications +
                ", allToolSpecifications=" + allToolSpecifications +
                ", toolExecutors=" + toolExecutors +
                ", immediateReturnTools=" + immediateReturnTools +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private List<ToolSpecification> toolSpecifications;
        private List<ToolSpecification> allToolSpecifications;
        private Map<String, ToolExecutor> toolExecutors;
        private Set<String> immediateReturnTools;

        public Builder toolSpecifications(List<ToolSpecification> toolSpecifications) {
            this.toolSpecifications = toolSpecifications;
            return this;
        }

        /**
         * @since 1.10.0
         */
        public Builder allToolSpecifications(List<ToolSpecification> allToolSpecifications) { // TODO name
            this.allToolSpecifications = allToolSpecifications;
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
