package dev.langchain4j.agentic.supervisor;

import static dev.langchain4j.agentic.internal.AgentUtil.validateAgentClass;

import dev.langchain4j.agentic.internal.AbstractServiceBuilder;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import java.lang.reflect.Method;
import java.util.function.Function;

public class SupervisorAgentServiceImpl<T> extends AbstractServiceBuilder<T, SupervisorAgentServiceImpl<T>>
        implements SupervisorAgentService<T> {

    private ChatModel chatModel;

    private ChatMemoryProvider chatMemoryProvider;

    private int maxAgentsInvocations = 10;

    private SupervisorContextStrategy contextStrategy = SupervisorContextStrategy.CHAT_MEMORY;
    private SupervisorResponseStrategy responseStrategy = SupervisorResponseStrategy.LAST;

    private Function<AgenticScope, String> requestGenerator;
    private String supervisorContext;

    public SupervisorAgentServiceImpl(Class<T> agentServiceClass, Method agenticMethod) {
        super(agentServiceClass, agenticMethod);
    }

    public T build() {
        if (supervisorContext != null) {
            this.beforeCall(agenticScope -> {
                if (!agenticScope.hasState(SupervisorPlanner.SUPERVISOR_CONTEXT_KEY)) {
                    agenticScope.writeState(SupervisorPlanner.SUPERVISOR_CONTEXT_KEY, supervisorContext);
                }
            });
        }

        return build(() -> new SupervisorPlanner(chatModel, chatMemoryProvider, maxAgentsInvocations,
                contextStrategy, responseStrategy, requestGenerator,
                outputKey, output));
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
}
