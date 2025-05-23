package dev.langchain4j.agentic;

import dev.langchain4j.agentic.declarative.ActivationCondition;
import dev.langchain4j.agentic.declarative.ConditionalAgent;
import dev.langchain4j.agentic.declarative.ExitCondition;
import dev.langchain4j.agentic.declarative.LoopAgent;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.declarative.Subagent;
import dev.langchain4j.agentic.workflow.ConditionialAgentService;
import dev.langchain4j.agentic.workflow.LoopAgentService;
import dev.langchain4j.agentic.workflow.SequentialAgentService;
import dev.langchain4j.model.chat.ChatModel;
import io.a2a.spec.A2A;
import io.a2a.spec.A2AServerException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static dev.langchain4j.agentic.internal.AgentUtil.methodInvocationArguments;

public class AgentServices {

    private AgentServices() { }

    public static <T> AgentBuilder<T> agentBuilder(Class<T> agentServiceClass) {
        return new AgentBuilder<>(agentServiceClass);
    }

    public static SequentialAgentService<UntypedAgent> sequenceBuilder() {
        return SequentialAgentService.builder();
    }

    public static <T> SequentialAgentService<T> sequenceBuilder(Class<T> agentServiceClass) {
        return SequentialAgentService.builder(agentServiceClass);
    }

    public static LoopAgentService<UntypedAgent> loopBuilder() {
        return LoopAgentService.builder();
    }

    public static <T> LoopAgentService<T> loopBuilder(Class<T> agentServiceClass) {
        return LoopAgentService.builder(agentServiceClass);
    }

    public static ConditionialAgentService<UntypedAgent> conditionalBuilder() {
        return ConditionialAgentService.builder();
    }

    public static <T> ConditionialAgentService<T> conditionalBuilder(Class<T> agentServiceClass) {
        return ConditionialAgentService.builder(agentServiceClass);
    }

    public static A2AClientBuilder<UntypedAgent> a2aBuilder(String a2aServerUrl) {
        return a2aBuilder(a2aServerUrl, UntypedAgent.class);
    }

    public static <T> A2AClientBuilder<T> a2aBuilder(String a2aServerUrl, Class<T> agentServiceClass) {
        try {
            return new A2AClientBuilder(A2A.getAgentCard(a2aServerUrl), agentServiceClass);
        } catch (A2AServerException e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T createAgent(Class<T> agentServiceClass, ChatModel chatModel) {
        T agent = createComposedAgent(agentServiceClass, chatModel);

        if (agent == null) {
            throw new IllegalArgumentException("Provided class " + agentServiceClass.getName() + " is not an agent.");
        }

        return agent;
    }

    public static <T> T createComposedAgent(Class<T> agentServiceClass, ChatModel chatModel) {
        SequenceAgent sequenceAgent = agentServiceClass.getAnnotation(SequenceAgent.class);
        if (sequenceAgent != null) {
            return buildSequentialAgent(sequenceAgent, agentServiceClass, chatModel);
        }

        LoopAgent loopAgent = agentServiceClass.getAnnotation(LoopAgent.class);
        if (loopAgent != null) {
            return buildLoopAgent(loopAgent, agentServiceClass, chatModel);
        }

        ConditionalAgent conditionalAgent = agentServiceClass.getAnnotation(ConditionalAgent.class);
        if (conditionalAgent != null) {
            return buildConditionalAgent(conditionalAgent, agentServiceClass, chatModel);
        }

        return null;
    }

    private static <T> T buildSequentialAgent(SequenceAgent sequenceAgent, Class<T> agentServiceClass, ChatModel chatModel) {
        var builder = sequenceBuilder(agentServiceClass)
                .subAgents(createSubagents(sequenceAgent.subagents(), chatModel));
        if (!sequenceAgent.outputName().isBlank()) {
            builder.outputName(sequenceAgent.outputName());
        }
        return builder.build();
    }

    private static <T> T buildLoopAgent(LoopAgent loopAgent, Class<T> agentServiceClass, ChatModel chatModel) {
        var builder = loopBuilder(agentServiceClass)
                .subAgents(createSubagents(loopAgent.subagents(), chatModel))
                .maxIterations(loopAgent.maxIterations());

        if (!loopAgent.outputName().isBlank()) {
            builder.outputName(loopAgent.outputName());
        }

        predicateMethod(agentServiceClass, method -> method.isAnnotationPresent(ExitCondition.class))
                .map(AgentServices::cognispherePredicate)
                .ifPresent(builder::exitCondition);

        return builder.build();
    }

    private static <T> T buildConditionalAgent(ConditionalAgent conditionalAgent, Class<T> agentServiceClass, ChatModel chatModel) {
        var builder = conditionalBuilder(agentServiceClass);

        if (!conditionalAgent.outputName().isBlank()) {
            builder.outputName(conditionalAgent.outputName());
        }

        for (Subagent subagent : conditionalAgent.subagents()) {
            predicateMethod(agentServiceClass, method -> {
                ActivationCondition activationCondition = method.getAnnotation(ActivationCondition.class);
                return activationCondition != null && Arrays.asList(activationCondition.value()).contains(subagent.agentName());
            })
                    .map(AgentServices::cognispherePredicate)
                    .ifPresent(condition -> builder.subAgents(condition, createSubagent(subagent, chatModel)));
        }

        return builder.build();
    }

    private static Optional<Method> predicateMethod(Class<?> agentServiceClass, Predicate<Method> methodSelector) {
        for (Method method : agentServiceClass.getDeclaredMethods()) {
            if (methodSelector.test(method) &&
                    Modifier.isStatic(method.getModifiers()) &&
                    (method.getReturnType() == boolean.class || method.getReturnType() == Boolean.class)) {
                return Optional.of(method);
            }
        }
        return Optional.empty();
    }

    private static Predicate<Cognisphere> cognispherePredicate(Method exitCondition) {
        boolean isCognisphereArg = exitCondition.getParameterCount() == 1 && exitCondition.getParameterTypes()[0] == Cognisphere.class;
        return cognisphere -> {
            try {
                Object[] args = isCognisphereArg ? new Object[] {cognisphere} : methodInvocationArguments(cognisphere, exitCondition);
                return (boolean) exitCondition.invoke(null, args);
            } catch (Exception e) {
                throw new RuntimeException("Error invoking exit condition method: " + exitCondition.getName(), e);
            }
        };
    }

    private static Object[] createSubagents(Subagent[] loopAgent, ChatModel chatModel) {
        return Stream.of(loopAgent)
                .map(subagent -> createSubagent(subagent, chatModel))
                .toArray(Object[]::new);
    }

    private static Object createSubagent(Subagent subagent, ChatModel chatModel) {
        Object agent = createComposedAgent(subagent.agentClass(), chatModel);
        if (agent != null) {
            return agent;
        }

        return AgentServices.agentBuilder(subagent.agentClass())
                .chatModel(chatModel)
                .outputName(subagent.outputName())
                .build();
    }
}
