package dev.langchain4j.agentic.agent;

import dev.langchain4j.agentic.internal.UserMessageRecorder;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.agentic.internal.AgentSpecification;
import dev.langchain4j.agentic.internal.AgenticScopeOwner;
import dev.langchain4j.service.AiServiceContext;
import dev.langchain4j.service.memory.ChatMemoryAccess;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

public class AgentInvocationHandler implements InvocationHandler {

    private final AiServiceContext context;
    private final AgentBuilder<?> builder;
    private final Object agent;
    private final UserMessageRecorder messageRecorder;
    private final boolean agenticScopeDependent;

    AgentInvocationHandler(AiServiceContext context, Object agent, AgentBuilder<?> builder, UserMessageRecorder messageRecorder, boolean agenticScopeDependent) {
        this.context = context;
        this.agent = agent;
        this.builder = builder;
        this.messageRecorder = messageRecorder;
        this.agenticScopeDependent = agenticScopeDependent;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Exception {
        if (method.getDeclaringClass() == ChatMessagesAccess.class) {
            return switch (method.getName()) {
                case "lastUserMessage" -> messageRecorder.lastUserMessage();
                default -> throw new UnsupportedOperationException(
                        "Unknown method on AgenticScopeOwner class : " + method.getName());
            };
        }

        if (method.getDeclaringClass() == AgenticScopeOwner.class) {
            return switch (method.getName()) {
                case "withAgenticScope" -> agenticScopeDependent ?
                        ((DefaultAgenticScope) args[0]).getOrCreateAgent(builder.agentId(), builder::build) :
                        proxy;
                case "registry" -> throw new UnsupportedOperationException(
                        "AgenticScopeOwner's registry method can be used only on the root agent of an agentic system.");
                default -> throw new UnsupportedOperationException(
                        "Unknown method on AgenticScopeOwner class : " + method.getName());
            };
        }

        if (method.getDeclaringClass() == ChatMemoryAccess.class) {
            return switch (method.getName()) {
                case "getChatMemory" -> context.hasChatMemory() ? context.chatMemoryService.getChatMemory(args[0]) : null;
                case "evictChatMemory" -> context.hasChatMemory() && context.chatMemoryService.evictChatMemory(args[0]) != null;
                default ->
                        throw new UnsupportedOperationException(
                                "Unknown method on ChatMemoryAccess class : " + method.getName());
            };
        }

        if (method.getDeclaringClass() == AgentSpecification.class) {
            return switch (method.getName()) {
                case "name" -> builder.name;
                case "description" -> builder.description;
                case "outputName" -> builder.outputName;
                case "async" -> builder.async;
                default ->
                        throw new UnsupportedOperationException(
                                "Unknown method on ChatMemoryAccess class : " + method.getName());
            };
        }

        return method.invoke(agent, args);
    }
}
