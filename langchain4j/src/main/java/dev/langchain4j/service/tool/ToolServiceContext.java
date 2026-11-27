package dev.langchain4j.service.tool;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ReturnBehavior;
import dev.langchain4j.agent.tool.ToolSpecification;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import dev.langchain4j.guardrail.GuardrailRequestParams;
import dev.langchain4j.service.guardrail.ToolGuardrailService;
import dev.langchain4j.service.guardrail.ToolGuardrailService.ToolKey;
import java.util.Optional;

import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.service.tool.search.ToolSearchStrategy;

import static dev.langchain4j.internal.Utils.copy;
import static java.util.stream.Collectors.toSet;

@Internal
public class ToolServiceContext {

    private final List<ToolSpecification> effectiveTools;
    private final List<ToolSpecification> availableTools;
    private final Map<String, ToolExecutor> toolExecutors;
    private final Map<String, ReturnBehavior> returnBehaviors;
    private final List<ToolProvider> dynamicToolProviders;
    private final Map<String, ToolKey> toolGuardrailKeys;
    private final ToolGuardrailService toolGuardrailService;
    private final GuardrailRequestParams guardrailRequestParams;

    public ToolServiceContext(Builder builder) {
        this.effectiveTools = copy(builder.effectiveTools);
        this.availableTools = copy(builder.availableTools);
        this.toolExecutors = copy(builder.toolExecutors);
        this.returnBehaviors = copy(builder.returnBehaviors);
        this.dynamicToolProviders = copy(builder.dynamicToolProviders);
        this.toolGuardrailKeys = copy(builder.toolGuardrailKeys);
        this.toolGuardrailService = builder.toolGuardrailService;
        this.guardrailRequestParams = builder.guardrailRequestParams;
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
        this.toolGuardrailKeys = Map.of();
        this.toolGuardrailService = null;
        this.guardrailRequestParams = null;
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
     * Returns the key that {@link ToolGuardrailService#bind(Class, AiServiceTool)} of the
     * {@linkplain #toolGuardrailService() tool guardrail service} returned for each tool, by tool name. The key carries
     * the specification and the source of the tool, and the guardrails that apply to it.
     * <p>
     *     A tool without a key is bound on demand by the tool guardrail service when it is called, with its
     *     specification and executor from this context, so it gets at least the guardrails that apply to every tool:
     *     no tool skips the tool guardrails because it was not bound beforehand. Without a tool guardrail service, no
     *     tool guardrails run. A key from another tool guardrail service fails the call with an
     *     {@link IllegalArgumentException}.
     * </p>
     *
     * @since 1.25.0
     */
    public Map<String, ToolKey> toolGuardrailKeys() {
        return toolGuardrailKeys;
    }

    /**
     * Returns the service that executes the tool guardrails, and binds the tools that have no
     * {@linkplain #toolGuardrailKeys() key} (the tools that are provided while the invocation runs, or a framework
     * did not bind), if one is set.
     *
     * @since 1.25.0
     */
    public Optional<ToolGuardrailService> toolGuardrailService() {
        return Optional.ofNullable(toolGuardrailService);
    }

    /**
     * Returns the {@link GuardrailRequestParams} of the AI service invocation, which the tool guardrails receive in
     * their requests (so they see the same context as the AI service guardrails, and their events reach the listeners
     * registered for the invocation), if the invocation has any.
     * <p>
     *     It is absent when the context is not created by an AI service invocation. The tool guardrails then
     *     run with minimal parameters (an empty user message template and no variables).
     * </p>
     *
     * @since 1.25.0
     */
    public Optional<GuardrailRequestParams> guardrailRequestParams() {
        return Optional.ofNullable(guardrailRequestParams);
    }

    public Builder toBuilder() {
        return builder()
                .effectiveTools(effectiveTools)
                .availableTools(availableTools)
                .toolExecutors(toolExecutors)
                .returnBehaviors(returnBehaviors)
                .dynamicToolProviders(dynamicToolProviders)
                .toolGuardrailKeys(toolGuardrailKeys)
                .toolGuardrailService(toolGuardrailService)
                .guardrailRequestParams(guardrailRequestParams);
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
                && Objects.equals(toolGuardrailKeys, that.toolGuardrailKeys)
                && (toolGuardrailService == that.toolGuardrailService)
                && Objects.equals(guardrailRequestParams, that.guardrailRequestParams);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                effectiveTools,
                availableTools,
                toolExecutors,
                returnBehaviors,
                dynamicToolProviders,
                toolGuardrailKeys);
    }

    @Override
    public String toString() {
        return "ToolServiceContext{" + "effectiveTools="
                + effectiveTools + ", availableTools="
                + availableTools + ", toolExecutors="
                + toolExecutors + ", returnBehaviorByName="
                + returnBehaviors + ", dynamicToolProviders="
                + dynamicToolProviders + ", toolGuardrailKeys="
                + toolGuardrailKeys + ", toolGuardrailService="
                + toolGuardrailService + ", guardrailRequestParams="
                + guardrailRequestParams + '}';
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
        private Map<String, ToolKey> toolGuardrailKeys;
        private ToolGuardrailService toolGuardrailService;
        private GuardrailRequestParams guardrailRequestParams;

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
         * Sets the key that {@link ToolGuardrailService#bind(Class, AiServiceTool)} of the
         * {@linkplain #toolGuardrailService(ToolGuardrailService) tool guardrail service} returned for each tool, by
         * tool name. A tool without a key is bound on demand when it is called.
         *
         * @param toolGuardrailKeys the keys of the tools, by tool name
         * @return this builder
         * @since 1.25.0
         */
        public Builder toolGuardrailKeys(Map<String, ToolKey> toolGuardrailKeys) {
            this.toolGuardrailKeys = toolGuardrailKeys;
            return this;
        }

        /**
         * @since 1.25.0
         */
        public Builder toolGuardrailService(ToolGuardrailService toolGuardrailService) {
            this.toolGuardrailService = toolGuardrailService;
            return this;
        }

        /**
         * @since 1.25.0
         */
        public Builder guardrailRequestParams(GuardrailRequestParams guardrailRequestParams) {
            this.guardrailRequestParams = guardrailRequestParams;
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
