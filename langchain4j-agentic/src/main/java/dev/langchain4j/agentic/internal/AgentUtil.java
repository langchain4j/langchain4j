package dev.langchain4j.agentic.internal;

import static dev.langchain4j.internal.Utils.getAnnotatedMethod;
import static dev.langchain4j.internal.Utils.isNullOrBlank;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.agent.MissingArgumentException;
import dev.langchain4j.agentic.declarative.K;
import dev.langchain4j.agentic.declarative.TypedKey;
import dev.langchain4j.agentic.declarative.LoopCounter;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemConfigurationException;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.AgenticScopeAccess;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.TokenStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class AgentUtil {

    public static final String MEMORY_ID_ARG_NAME = "@MemoryId";
    public static final String AGENTIC_SCOPE_ARG_NAME = "@AgenticScope";
    public static final String LOOP_COUNTER_ARG_NAME = "@LoopCounter";

    private static final Map<Class<? extends TypedKey<?>>, TypedKey<?>> STATE_INSTANCES = new ConcurrentHashMap<>();

    private AgentUtil() {}

    private static <T> TypedKey<T> stateInstance(Class<? extends TypedKey<? extends T>> key) {
        return (TypedKey<T>) STATE_INSTANCES.computeIfAbsent(key, k -> {
            try {
                return key.getDeclaredConstructor().newInstance();
            }  catch (NoSuchMethodException e) {
                throw new AgenticSystemConfigurationException("TypedKey '" + key.getName() + "' doesn't have a no-args constructor", e);
            }  catch (IllegalAccessException e) {
                throw new AgenticSystemConfigurationException("TypedKey '" + key.getName() + "' is not accessible", e);
            } catch (InstantiationException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public static String outputKey(String outputKey, Class<? extends TypedKey<?>> typedOutputKey) {
        if (isNullOrBlank(outputKey)) {
            return typedOutputKey != Agent.NoTypedKey.class ? keyName(typedOutputKey) : null;
        }
        if (typedOutputKey != Agent.NoTypedKey.class) {
            throw new AgenticSystemConfigurationException("Both outputKey and typedOutputKey are set. Please set only one of them.");
        }
        return outputKey;
    }

    public static <T> T keyDefaultValue(Class<? extends TypedKey<T>> key) {
        return stateInstance(key).defaultValue();
    }

    public static String keyName(Class<? extends TypedKey<?>> key) {
        return stateInstance(key).name();
    }

    public static List<AgentExecutor> agentsToExecutors(Object... agents) {
        return Stream.of(agents).map(AgentUtil::agentToExecutor).toList();
    }

    public static AgentExecutor agentToExecutor(Object agent) {
        if (agent instanceof Class c) {
            agent = AgenticServices.agentBuilder(c).build();
        }
        return agent instanceof InternalAgent internalAgent
                ? agentToExecutor(internalAgent)
                : nonAiAgentToExecutor(agent);
    }

    private static AgentExecutor nonAiAgentToExecutor(Object agent) {
        Method agenticMethod = validateAgentClass(agent.getClass());
        Agent annotation = agenticMethod.getAnnotation(Agent.class);
        String name = isNullOrBlank(annotation.name()) ? agenticMethod.getName() : annotation.name();
        String description = isNullOrBlank(annotation.description()) ? annotation.value() : annotation.description();
        return new AgentExecutor(nonAiAgentInvoker(agent, agenticMethod, name, description, annotation), agent);
    }

    private static AgentInvoker nonAiAgentInvoker(Object agent, Method agenticMethod, String name, String description, Agent annotation) {
        return agent instanceof AgentSpecsProvider spec
                ? AgentInvoker.fromSpec(spec, agenticMethod, name)
                : AgentInvoker.fromMethod(
                        new NonAiAgentInstance(agenticMethod.getDeclaringClass(),
                                name, description, agenticMethod.getGenericReturnType(), annotation.outputKey(), annotation.async(),
                                argumentsFromMethod(agenticMethod), null),
                agenticMethod);
    }

    public static AgentExecutor agentToExecutor(InternalAgent agent) {
        for (Method method : agent.getClass().getMethods()) {
            Optional<AgentExecutor> executor = A2AService.get().isPresent()
                    ? A2AService.get().methodToAgentExecutor(agent, method)
                    : methodToAgentExecutor(agent, method);
            if (executor.isPresent()) {
                return executor.get();
            }
        }
        throw new IllegalArgumentException("Agent executor not found");
    }

    public static Optional<Method> getAnnotatedMethodOnClass(Class<?> clazz, Class<? extends Annotation> annotation) {
        return Arrays.stream(clazz.getMethods())
                .filter(m -> m.isAnnotationPresent(annotation))
                .findFirst();
    }

    private static Optional<AgentExecutor> methodToAgentExecutor(InternalAgent agent, Method method) {
        return getAnnotatedMethod(method, Agent.class)
                .map(agentMethod -> new AgentExecutor(AgentInvoker.fromMethod(agent, agentMethod), agent));
    }

    public static List<AgentArgument> argumentsFromMethod(Method method) {
        return argumentsFromMethod(method, Map.of());
    }

    public static List<AgentArgument> argumentsFromMethod(Method method, Map<String, Object> defaultValues) {
        return Stream.of(method.getParameters())
                .map(p -> {
                    String argName = parameterName(p);
                    Object defaultValue = defaultValues.getOrDefault(argName, parameterDefaultValue(p));
                    return new AgentArgument(p.getParameterizedType(), argName, defaultValue);
                })
                .toList();
    }

    private static String parameterName(Parameter p) {
        if (p.getAnnotation(MemoryId.class) != null) {
            return MEMORY_ID_ARG_NAME;
        }
        if (p.getAnnotation(LoopCounter.class) != null) {
            return LOOP_COUNTER_ARG_NAME;
        }
        if (AgenticScope.class.isAssignableFrom(p.getType())) {
            return AGENTIC_SCOPE_ARG_NAME;
        }
        return AgentInvoker.parameterName(p);
    }

    private static Object parameterDefaultValue(Parameter p) {
        K k = p.getAnnotation(K.class);
        return k != null ? stateInstance(k.value()).defaultValue() : null;
    }

    public static AgentInvocationArguments agentInvocationArguments(
            AgenticScope agenticScope, List<AgentArgument> agentArguments) throws MissingArgumentException {
        return agentInvocationArguments(agenticScope, agentArguments, Map.of());
    }

    public static AgentInvocationArguments agentInvocationArguments(
            AgenticScope agenticScope, List<AgentArgument> agentArguments, Map<String, Object> additionalArgs)
            throws MissingArgumentException {
        Map<String, Object> namedArgs = new HashMap<>();
        Object[] positionalArgs = new Object[agentArguments.size()];

        int i = 0;
        for (AgentArgument arg : agentArguments) {
            String argName = arg.name();
            if (argName.equals(MEMORY_ID_ARG_NAME)) {
                positionalArgs[i++] = agenticScope.memoryId();
                continue;
            }
            if (argName.equals(AGENTIC_SCOPE_ARG_NAME)) {
                positionalArgs[i++] = agenticScope;
                continue;
            }
            if (additionalArgs.containsKey(argName)) {
                positionalArgs[i++] = additionalArgs.get(argName);
                continue;
            }

            Object argValue = argumentFromAgenticScope(agenticScope, arg);
            positionalArgs[i++] = argValue;
            namedArgs.put(argName, argValue);
        }
        return new AgentInvocationArguments(namedArgs, positionalArgs);
    }

    private static Object argumentFromAgenticScope(AgenticScope agenticScope, AgentArgument arg) {
        Object argValue = agenticScope.readState(arg.name());
        if (argValue == null) {
            argValue = arg.defaultValue();
            if (argValue == null) {
                throw new MissingArgumentException(arg.name());
            }
        }
        Object parsedArgument = adaptValueToType(argValue, arg.rawType());
        if (argValue != parsedArgument) {
            agenticScope.writeState(arg.name(), parsedArgument);
        }
        return parsedArgument;
    }

    private static Object adaptValueToType(Object value, Class<?> type) {
        if (type.isInstance(value)) {
            return value;
        }
        if (value instanceof String s) {
            return switch (type.getName()) {
                case "java.lang.String", "java.lang.Object" -> s;
                case "int", "java.lang.Integer" -> Integer.parseInt(s);
                case "long", "java.lang.Long" -> Long.parseLong(s);
                case "double", "java.lang.Double" -> Double.parseDouble(s);
                case "float", "java.lang.Float" -> Float.parseFloat(s);
                case "boolean", "java.lang.Boolean" -> Boolean.parseBoolean(s);
                default -> throw new IllegalArgumentException("Unsupported type: " + type);
            };
        }
        if (value instanceof Number n) {
            return switch (type.getName()) {
                case "java.lang.String" -> "" + n;
                case "int", "java.lang.Integer" -> n.intValue();
                case "long", "java.lang.Long" -> n.longValue();
                case "double", "java.lang.Double" -> n.doubleValue();
                case "float", "java.lang.Float" -> n.floatValue();
                default -> value;
            };
        }
        return value;
    }

    public static Method validateAgentClass(Class<?> agentServiceClass) {
        return validateAgentClass(agentServiceClass, true);
    }

    public static Method validateAgentClass(Class<?> agentServiceClass, boolean failOnMissingAnnotation) {
        Method agentMethod = null;
        for (Method method : agentServiceClass.getMethods()) {
            if (method.isAnnotationPresent(Agent.class)) {
                if (agentMethod != null) {
                    throw new IllegalArgumentException(
                            "Multiple agent methods found in class: " + agentServiceClass.getName());
                }
                agentMethod = method;
            }
        }
        if (agentMethod == null && failOnMissingAnnotation) {
            throw new IllegalArgumentException("No agent method found in class: " + agentServiceClass.getName());
        }
        return agentMethod;
    }

    public static <T> T buildAgent(Class<T> agentServiceClass, InvocationHandler invocationHandler) {
        return (T) Proxy.newProxyInstance(
                agentServiceClass.getClassLoader(),
                new Class<?>[] { agentServiceClass, InternalAgent.class, AgenticScopeOwner.class, AgenticScopeAccess.class },
                invocationHandler);
    }

    public static Map<String, Class<?>> agenticSystemDataTypes(AgentInstance rootAgent) {
        Map<String, Class<?>> dataTypes = new HashMap<>();
        collectAgenticSystemDataTypes(rootAgent, dataTypes);
        return dataTypes;
    }

    private static void collectAgenticSystemDataTypes(AgentInstance rootAgent, Map<String, Class<?>> dataTypes) {
        for (AgentArgument arg : rootAgent.arguments()) {
            recordType(dataTypes, arg.name(), arg.type());
        }
        if (rootAgent.outputKey() != null) {
            recordType(dataTypes, rootAgent.outputKey(), rootAgent.outputType());
        }
        for (AgentInstance subagent : rootAgent.subagents()) {
            collectAgenticSystemDataTypes(subagent, dataTypes);
        }
    }

    private static void recordType(Map<String, Class<?>> dataTypes, String name, Type type) {
        Class<?> keyClass = rawType(type);
        if (TokenStream.class.isAssignableFrom(keyClass)) {
            keyClass = String.class;
        }
        if (!dataTypes.containsKey(name)) {
            dataTypes.put(name, keyClass);
        } else {
            Class<?> existingType = dataTypes.get(name);
            if (existingType.isAssignableFrom(keyClass)) {
                // do nothing, keep the existing type
            } else if (keyClass.isAssignableFrom(existingType)) {
                dataTypes.put(name, keyClass);
            } else {
                throw new AgenticSystemConfigurationException(
                        "Conflicting types for key '" + name + "': " +
                                existingType.getName() + " and " + keyClass.getName());
            }
        }
    }

    public static Class<?> rawType(Type type) {
        if (type instanceof Class<?> clazz) {
            return clazz;
        }
        if (type instanceof ParameterizedType parameterizedType) {
            Class<?> clazz = (Class<?>) parameterizedType.getRawType();
            if (clazz == ResultWithAgenticScope.class) {
                return rawType(parameterizedType.getActualTypeArguments()[0]);
            }
            return clazz;
        }
        throw new IllegalArgumentException("Unsupported type: " + type);
    }
}
