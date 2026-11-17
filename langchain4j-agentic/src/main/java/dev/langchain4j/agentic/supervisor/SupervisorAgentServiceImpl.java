package dev.langchain4j.agentic.supervisor;

import static dev.langchain4j.agentic.declarative.DeclarativeUtil.agenticScopeFunction;
import static dev.langchain4j.agentic.declarative.DeclarativeUtil.buildAgentFeatures;
import static dev.langchain4j.agentic.declarative.DeclarativeUtil.checkReturnType;
import static dev.langchain4j.agentic.declarative.DeclarativeUtil.configureOutput;
import static dev.langchain4j.agentic.declarative.DeclarativeUtil.invokeSupplierWithResolvers;
import static dev.langchain4j.agentic.internal.AgentUtil.validateAgentClass;

import dev.langchain4j.agentic.declarative.ChatMemoryProviderSupplier;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.declarative.SupervisorRequest;
import dev.langchain4j.agentic.internal.AbstractServiceBuilder;
import dev.langchain4j.agentic.internal.AgentHelpers;
import dev.langchain4j.agentic.internal.Context;
import dev.langchain4j.agentic.internal.CurrentPlanningContext;
import dev.langchain4j.agentic.internal.ResolvedParameters;
import dev.langchain4j.agentic.planner.AgenticService;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.IllegalConfigurationException;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.List;
import java.util.function.Function;

public class SupervisorAgentServiceImpl<T> extends AbstractServiceBuilder<T, SupervisorAgentServiceImpl<T>>
        implements SupervisorAgentService<T>, AgenticService<SupervisorAgentService<T>, T> {

    private ChatModel chatModel;

    private ChatMemoryProvider chatMemoryProvider;

    private int maxAgentsInvocations = 10;

    private SupervisorContextStrategy contextStrategy = SupervisorContextStrategy.CHAT_MEMORY;
    private SupervisorResponseStrategy responseStrategy = SupervisorResponseStrategy.LAST;

    private Function<AgenticScope, String> requestGenerator;
    private String supervisorContext;
    private ChatModel contextSummarizerModel;
    private Context.ContextSummarizer contextSummarizer;

    public SupervisorAgentServiceImpl(Class<T> agentServiceClass, Method agenticMethod) {
        this(agentServiceClass, agenticMethod, null);
    }

    public SupervisorAgentServiceImpl(Class<T> agentServiceClass, Method agenticMethod, ChatModel chatModel) {
        this(agentServiceClass, agenticMethod, chatModel, List.of());
    }

    /**
     * @param chatModel the chat model of the supervisor, unless a {@link ChatModelSupplier} helper provides one
     * @param helperObjects the helper objects of the agent, whose instance helper methods configure it together
     *        with the static methods of its type
     */
    public SupervisorAgentServiceImpl(
            Class<T> agentServiceClass, Method agenticMethod, ChatModel chatModel, List<Object> helperObjects) {
        super(agentServiceClass, agenticMethod);
        if (!configureSupervisor(AgentHelpers.of(agentServiceClass, helperObjects))) {
            this.chatModel(chatModel);
        }
    }

    public T build() {
        if (supervisorContext != null) {
            this.beforeCall(this.beforeCall.andThen(agenticScope ->
                    agenticScope.writeStateIfAbsent(SupervisorPlanner.SUPERVISOR_CONTEXT_KEY, supervisorContext)));
        }

        Context.ContextSummarizer summarizer = contextSummarizer();
        AtomicBoolean subagentsDescriptionsChecked = new AtomicBoolean();
        return build(() -> new SupervisorPlanner(
                chatModel,
                chatMemoryProvider,
                maxAgentsInvocations,
                contextStrategy,
                responseStrategy,
                requestGenerator,
                outputKey,
                output,
                summarizer,
                subagentsDescriptionsChecked));
    }

    public static SupervisorAgentService<SupervisorAgent> builder() {
        try {
            Method supervisorMethod = SupervisorAgent.class.getMethod("invoke", String.class);
            return new SupervisorAgentServiceImpl<>(SupervisorAgent.class, supervisorMethod);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> SupervisorAgentService<T> builder(Class<T> agentServiceClass) {
        return new SupervisorAgentServiceImpl<>(agentServiceClass, validateAgentClass(agentServiceClass, false));
    }

    @Override
    public SupervisorAgentServiceImpl<T> chatModel(ChatModel chatModel) {
        this.chatModel = chatModel;
        return this;
    }

    @Override
    public SupervisorAgentServiceImpl<T> chatMemoryProvider(ChatMemoryProvider chatMemoryProvider) {
        this.chatMemoryProvider = chatMemoryProvider;
        return this;
    }

    @Override
    public SupervisorAgentServiceImpl<T> requestGenerator(Function<AgenticScope, String> requestGenerator) {
        this.requestGenerator = requestGenerator;
        return this;
    }

    @Override
    public SupervisorAgentServiceImpl<T> contextGenerationStrategy(SupervisorContextStrategy contextStrategy) {
        this.contextStrategy = contextStrategy;
        return this;
    }

    @Override
    public SupervisorAgentServiceImpl<T> responseStrategy(SupervisorResponseStrategy responseStrategy) {
        this.responseStrategy = responseStrategy;
        return this;
    }

    @Override
    public SupervisorAgentServiceImpl<T> supervisorContext(String supervisorContext) {
        this.supervisorContext = supervisorContext;
        return this;
    }

    @Override
    public SupervisorAgentServiceImpl<T> maxAgentsInvocations(int maxAgentsInvocations) {
        this.maxAgentsInvocations = maxAgentsInvocations;
        return this;
    }

    @Override
    public String serviceType() {
        return "Supervisor";
    }

    private synchronized Context.ContextSummarizer contextSummarizer() {
        if (contextStrategy == SupervisorContextStrategy.CHAT_MEMORY) {
            return null;
        }
        if (contextSummarizer == null || contextSummarizerModel != chatModel) {
            if (chatModel == null) {
                throw new IllegalConfigurationException(
                        "A ChatModel is required to summarize context for a supervisor agent.");
            }
            contextSummarizerModel = chatModel;
            contextSummarizer = Context.createSummarizer(chatModel, CurrentPlanningContext::reportChatModelResponse);
        }
        return contextSummarizer;
    }

    /**
     * Applies the helpers of the supervisor to this builder.
     *
     * @return whether a chat model supplier has been applied
     */
    private boolean configureSupervisor(AgentHelpers helpers) {
        Class<?> agentServiceClass = helpers.agentType();
        helpers.select(SupervisorRequest.class)
                .map(helper -> {
                    checkReturnType(helper.method(), String.class);
                    return agenticScopeFunction(agentServiceClass, helper, String.class);
                })
                .ifPresent(this::requestGenerator);

        boolean chatModelSupplied = helpers.select(ChatModelSupplier.class)
                .map(helper -> {
                    checkReturnType(helper.method(), ChatModel.class);
                    return invokeSupplierWithResolvers(agentServiceClass, helper, ChatModel.class);
                })
                .map(this::chatModel)
                .isPresent();

        helpers.select(ChatMemoryProviderSupplier.class)
                .map(helper -> {
                    checkReturnType(helper.method(), ChatMemory.class);
                    ResolvedParameters parameters =
                            ResolvedParameters.of(agentServiceClass, helper).checkFrameworkParameters(Object.class);
                    return (ChatMemoryProvider) memoryId -> parameters.invoke(memoryId);
                })
                .ifPresent(this::chatMemoryProvider);

        configureOutput(helpers, this);
        buildAgentFeatures(helpers, this);
        return chatModelSupplied;
    }
}
