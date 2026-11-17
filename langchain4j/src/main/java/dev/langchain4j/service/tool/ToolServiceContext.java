package dev.langchain4j.service.tool;

import static dev.langchain4j.internal.Utils.copy;
import static java.util.stream.Collectors.toSet;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ReturnBehavior;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.service.guardrail.ToolGuardrailService;
import dev.langchain4j.service.tool.search.ToolSearchStrategy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Internal
public class ToolServiceContext {

    private final List<ToolSpecification> effectiveTools;
    private final List<ToolSpecification> availableTools;
    private final Map<String, ToolExecutor> toolExecutors;
    private final Map<String, ReturnBehavior> returnBehaviors;
    private final List<ToolProvider> dynamicToolProviders;
    private final Map<String, ToolGuardrailBinding> toolGuardrailBindings;
    private final ToolGuardrailService toolGuardrailService;

    public ToolServiceContext(Builder builder) {
        this.effectiveTools = copy(builder.effectiveTools);
        this.availableTools = copy(builder.availableTools);
        this.toolExecutors = copy(builder.toolExecutors);
        this.returnBehaviors = copy(builder.returnBehaviors);
        this.dynamicToolProviders = copy(builder.dynamicToolProviders);
        this.toolGuardrailBindings = copy(builder.toolGuardrailBindings);
        this.toolGuardrailService = builder.toolGuardrailService;
    }

    /**
     * @deprecated use {@link #ToolServiceContext(Builder)} instead
     */
    @Deprecated(since = "1.12.0")
    public ToolServiceContext(List<ToolSpecification> toolSpecifications, Map<String, ToolExecutor> toolExecutors) {
        this.effectiveTools = copy(toolSpecifications);
        this.availableTools = copy(toolSpecifications);
        this.toolExecutors = copy(toolExecutors);
        this.returnBehaviors = Map.of();
        this.dynamicToolProviders = List.of();
        this.toolGuardrailBindings = Map.of();
        this.toolGuardrailService = null;
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

    /**
     * @since 1.14.0
     */
    public Map<String, ReturnBehavior> returnBehaviors() {
        return returnBehaviors;
    }

    /**
     * Returns the effective {@link ReturnBehavior} for the given tool. If the tool is unknown
     * or has no explicitly configured behavior, {@link ReturnBehavior#TO_LLM} is returned.
     *
     * @since 1.14.0
     */
    public ReturnBehavior returnBehavior(String toolName) {
        return returnBehaviors.getOrDefault(toolName, ReturnBehavior.TO_LLM);
    }

    /**
     * @deprecated use {@link #returnBehavior(String)} instead
     */
    @Deprecated(since = "1.14.0")
    public Set<String> immediateReturnTools() {
        return returnBehaviors.entrySet().stream()
                .filter(entry -> entry.getValue() == ReturnBehavior.IMMEDIATE)
                .map(Map.Entry::getKey)
                .collect(toSet());
    }

    /**
     * Returns dynamic tool providers that are re-evaluated before each LLM call.
     *
     * @since 1.13.0
     */
    public List<ToolProvider> dynamicToolProviders() {
        return dynamicToolProviders;
    }

    /**
     * Returns the guardrail binding (tool source, and the key to execute the tool guardrails of the tool with) of each
     * tool, by tool name. A tool without an entry has no tool guardrails executed, which is the case of every tool
     * when no {@link ToolGuardrailService} is set. A tool with an entry can have no tool guardrails either (an AI
     * service always sets a {@link ToolGuardrailService}, so every one of its tools is bound): check
     * {@link ToolGuardrailBinding#hasInputGuardrails()} / {@link ToolGuardrailBinding#hasOutputGuardrails()} before
     * building a guardrail request.
     *
     * @since 1.24.0
     */
    public Map<String, ToolGuardrailBinding> toolGuardrailBindings() {
        return toolGuardrailBindings;
    }

    /**
     * Returns the service that executes the tool guardrails, used to bind the tools that are provided while the
     * invocation runs (dynamic {@link ToolProvider}s), if one is set.
     *
     * @since 1.24.0
     */
    public Optional<ToolGuardrailService> toolGuardrailService() {
        return Optional.ofNullable(toolGuardrailService);
    }

    public Builder toBuilder() {
        return builder()
                .effectiveTools(effectiveTools)
                .availableTools(availableTools)
                .toolExecutors(toolExecutors)
                .returnBehaviors(returnBehaviors)
                .dynamicToolProviders(dynamicToolProviders)
                .toolGuardrailBindings(toolGuardrailBindings)
                .toolGuardrailService(toolGuardrailService);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ToolServiceContext that = (ToolServiceContext) o;
        return Objects.equals(effectiveTools, that.effectiveTools)
                && Objects.equals(availableTools, that.availableTools)
                && Objects.equals(toolExecutors, that.toolExecutors)
                && Objects.equals(returnBehaviors, that.returnBehaviors)
                && Objects.equals(dynamicToolProviders, that.dynamicToolProviders)
                && Objects.equals(toolGuardrailBindings, that.toolGuardrailBindings)
                && (toolGuardrailService == that.toolGuardrailService);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                effectiveTools,
                availableTools,
                toolExecutors,
                returnBehaviors,
                dynamicToolProviders,
                toolGuardrailBindings);
    }

    @Override
    public String toString() {
        return "ToolServiceContext{" + "effectiveTools="
                + effectiveTools + ", availableTools="
                + availableTools + ", toolExecutors="
                + toolExecutors + ", returnBehaviorByName="
                + returnBehaviors + ", dynamicToolProviders="
                + dynamicToolProviders + ", toolGuardrailBindings="
                + toolGuardrailBindings + ", toolGuardrailService="
                + toolGuardrailService + '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private List<ToolSpecification> effectiveTools;
        private List<ToolSpecification> availableTools;
        private Map<String, ToolExecutor> toolExecutors;
        private Map<String, ReturnBehavior> returnBehaviors = new HashMap<>();
        private List<ToolProvider> dynamicToolProviders;
        private Map<String, ToolGuardrailBinding> toolGuardrailBindings;
        private ToolGuardrailService toolGuardrailService;

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

        /**
         * @deprecated use {@link #returnBehaviors(Map)} instead
         */
        @Deprecated(since = "1.14.0")
        public Builder immediateReturnTools(Set<String> immediateReturnTools) {
            if (immediateReturnTools != null) {
                immediateReturnTools.forEach(name -> this.returnBehaviors.put(name, ReturnBehavior.IMMEDIATE));
            }
            return this;
        }

        /**
         * @since 1.14.0
         */
        public Builder returnBehaviors(Map<String, ReturnBehavior> returnBehaviorByName) {
            if (returnBehaviorByName != null) {
                this.returnBehaviors.putAll(returnBehaviorByName);
            }
            return this;
        }

        /**
         * @since 1.13.0
         */
        public Builder dynamicToolProviders(List<ToolProvider> dynamicToolProviders) {
            this.dynamicToolProviders = dynamicToolProviders;
            return this;
        }

        /**
         * @since 1.24.0
         */
        public Builder toolGuardrailBindings(Map<String, ToolGuardrailBinding> toolGuardrailBindings) {
            this.toolGuardrailBindings = toolGuardrailBindings;
            return this;
        }

        /**
         * @since 1.24.0
         */
        public Builder toolGuardrailService(ToolGuardrailService toolGuardrailService) {
            this.toolGuardrailService = toolGuardrailService;
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
