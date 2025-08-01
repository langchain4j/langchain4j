package dev.langchain4j.agentic.internal;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.cognisphere.Cognisphere;
import dev.langchain4j.service.MemoryId;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static dev.langchain4j.internal.Utils.getAnnotatedMethod;
import static dev.langchain4j.internal.Utils.isNullOrBlank;

public class AgentUtil {

    private AgentUtil() { }

    public record AgentArgument(Class<?> type, String name) { }

    public static List<AgentExecutor> agentsToExecutors(Object... agents) {
        return Stream.of(agents).map(AgentUtil::agentToExecutor).toList();
    }

    public static AgentExecutor agentToExecutor(Object agent) {
        if (agent instanceof AgentInstance agentInstance ) {
            return agentToExecutor(agentInstance);
        }
        Method agenticMethod = validateAgentClass(agent.getClass());
        Agent annotation = agenticMethod.getAnnotation(Agent.class);
        String name = isNullOrBlank(annotation.name()) ? agenticMethod.getName() : annotation.name();
        String description = isNullOrBlank(annotation.description()) ? annotation.value() : annotation.description();
        AgentSpecification agentSpecification = agent instanceof AgentSpecsProvider spec ?
                new MethodAgentSpecification(agenticMethod, name, spec.description(), spec.outputName(),
                        List.of(new AgentArgument(agenticMethod.getParameterTypes()[0], spec.inputName()))) :
                AgentSpecification.fromMethodAndSpec(agenticMethod, name, description, annotation.outputName());
        return new AgentExecutor(agentSpecification, agent);
    }

    public static AgentExecutor agentToExecutor(AgentInstance agent) {
       for (Method method : agent.getClass().getDeclaredMethods()) {
           Optional<AgentExecutor> executor = agent instanceof A2AClientInstance a2a ?
                   methodToA2AExecutor(a2a, method) :
                   methodToAgentExecutor(agent, method);
           if (executor.isPresent()) {
                return executor.get();
           }
        }
        throw new IllegalArgumentException("Agent not found");
    }

    public static Optional<Method> getAnnotatedMethodOnClass(Class<?> clazz, Class<? extends Annotation> annotation) {
        return Arrays.stream(clazz.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(annotation))
                .findFirst();
    }

    private static Optional<AgentExecutor> methodToA2AExecutor(A2AClientInstance a2aClient, Method method) {
        return getAnnotatedMethod(method, Agent.class)
                .map(agentMethod -> new AgentExecutor(new A2AClientAgentSpecification(a2aClient, agentMethod), a2aClient));
    }

    private static Optional<AgentExecutor> methodToAgentExecutor(AgentInstance agent, Method method) {
        return getAnnotatedMethod(method, Agent.class)
                .map(agentMethod -> new AgentExecutor(AgentSpecification.fromMethod(agent, agentMethod), agent));
    }

    public static Object[] methodInvocationArguments(Cognisphere cognisphere, Method method) {
        return methodInvocationArguments(cognisphere, argumentsFromMethod(method));
    }

    static List<AgentArgument> argumentsFromMethod(Method method) {
        return Stream.of(method.getParameters())
                .map(p -> new AgentArgument(p.getType(), parameterName(p)))
                .toList();
    }

    private static String parameterName(Parameter p) {
        if (p.getAnnotation(MemoryId.class) != null) {
            return "@MemoryId";
        }
        if (p.getType() == Cognisphere.class) {
            return "@Cognisphere";
        }
        return AgentSpecification.parameterName(p);
    }

    public static Object[] methodInvocationArguments(Cognisphere cognisphere, List<AgentArgument> agentArguments) {
        Object[] invocationArgs = new Object[agentArguments.size()];
        int i = 0;
        for (AgentArgument arg : agentArguments) {
            String argName = arg.name();
            if (argName.equals("@MemoryId")) {
                invocationArgs[i++] = cognisphere.id();
                continue;
            }
            if (arg.type() == Cognisphere.class) {
                invocationArgs[i++] = cognisphere;
                continue;
            }
            Object argValue = cognisphere.readState(argName);
            if (argValue == null) {
                throw new IllegalArgumentException("Missing argument: " + argName);
            }
            invocationArgs[i++] = parseArgument(argValue, arg.type());
        }
        return invocationArgs;
    }

    static Object parseArgument(Object argValue, Class<?> type) {
        if (argValue instanceof String s) {
            return switch (type.getName()) {
                case "java.lang.String" -> s;
                case "int", "java.lang.Integer" -> Integer.parseInt(s);
                case "long", "java.lang.Long" -> Long.parseLong(s);
                case "double", "java.lang.Double" -> Double.parseDouble(s);
                case "float", "java.lang.Float" -> Float.parseFloat(s);
                case "boolean", "java.lang.Boolean" -> Boolean.parseBoolean(s);
                default -> throw new IllegalArgumentException("Unsupported type: " + type);
            };
        }
        return argValue;
    }

    public static Method validateAgentClass(Class<?> agentServiceClass) {
        Method agentMethod = null;
        for (Method method : agentServiceClass.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Agent.class)) {
                if (agentMethod != null) {
                    throw new IllegalArgumentException("Multiple agent methods found in class: " + agentServiceClass.getName());
                }
                agentMethod = method;
            }
        }
        if (agentMethod == null) {
            throw new IllegalArgumentException("No agent method found in class: " + agentServiceClass.getName());
        }
        return agentMethod;
    }
}
