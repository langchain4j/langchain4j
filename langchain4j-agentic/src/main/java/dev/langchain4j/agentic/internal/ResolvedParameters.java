package dev.langchain4j.agentic.internal;

import static dev.langchain4j.agentic.internal.AgentUtil.agentInvocationArguments;
import static dev.langchain4j.agentic.internal.AgentUtil.argumentFromParameter;

import dev.langchain4j.Internal;
import dev.langchain4j.agentic.declarative.DeclarativeUtil;
import dev.langchain4j.agentic.declarative.SupplierParameterResolver;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgenticSystemConfigurationException;
import dev.langchain4j.agentic.scope.AgenticScope;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * The parameters of a static method declared on an agent type, split once, when the agent is created, between the
 * parameters provided by a registered {@link SupplierParameterResolver} and the parameters provided by the agentic
 * framework, like the {@link AgenticScope}, its state, the memory id or a value passed by the caller.
 * <p>
 * Only the parameters provided by the framework are agent arguments: the resolved ones are never read from the
 * {@link AgenticScope}, nor reported to listeners and planners.
 */
@Internal
public final class ResolvedParameters {

    private final Method method;
    private final SupplierParameterResolver[] resolvers;
    private final SupplierParameterResolver.Context[] contexts;
    private final int[] frameworkIndexes;
    private volatile List<AgentArgument> frameworkArguments;

    private ResolvedParameters(Class<?> agentType, Method method, List<SupplierParameterResolver> availableResolvers) {
        this.method = method;
        Parameter[] parameters = method.getParameters();
        this.resolvers = new SupplierParameterResolver[parameters.length];
        this.contexts = new SupplierParameterResolver.Context[parameters.length];
        List<Integer> indexes = new ArrayList<>(parameters.length);
        for (int i = 0; i < parameters.length; i++) {
            SupplierParameterResolver.Context context =
                    new DefaultSupplierParameterResolverContext(agentType, method, parameters[i]);
            for (SupplierParameterResolver resolver : availableResolvers) {
                if (resolver.supports(context)) {
                    resolvers[i] = resolver;
                    contexts[i] = context;
                    break;
                }
            }
            if (resolvers[i] == null) {
                indexes.add(i);
            }
        }
        this.frameworkIndexes = indexes.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * Splits the parameters of the given method using the {@link SupplierParameterResolver}s registered in
     * {@link DeclarativeUtil}.
     *
     * @param agentType the agent class or interface declaring the method, or inheriting it
     * @param method the static method to invoke
     */
    public static ResolvedParameters of(Class<?> agentType, Method method) {
        return new ResolvedParameters(agentType, method, DeclarativeUtil.getSupplierParameterResolvers());
    }

    public Method method() {
        return method;
    }

    /**
     * @return the parameters provided by the framework, as agent arguments, in the order they are declared
     */
    public List<AgentArgument> frameworkArguments() {
        // computed lazily, because only the parameters read from the AgenticScope need a name
        List<AgentArgument> arguments = frameworkArguments;
        if (arguments == null) {
            Parameter[] parameters = method.getParameters();
            arguments = new ArrayList<>(frameworkIndexes.length);
            for (int index : frameworkIndexes) {
                arguments.add(argumentFromParameter(parameters[index]));
            }
            arguments = List.copyOf(arguments);
            frameworkArguments = arguments;
        }
        return arguments;
    }

    /**
     * @return the number of parameters provided by the framework
     */
    public int frameworkParameterCount() {
        return frameworkIndexes.length;
    }

    /**
     * @return the parameter at the given position among the ones provided by the framework
     */
    public Parameter frameworkParameter(int position) {
        return method.getParameters()[frameworkIndexes[position]];
    }

    /**
     * Checks that the parameters provided by the framework are exactly the expected ones, in this order.
     *
     * @throws IllegalArgumentException if they are not
     */
    public ResolvedParameters checkFrameworkParameters(Class<?>... expected) {
        if (frameworkIndexes.length != expected.length) {
            String resolvedHint =
                    frameworkIndexes.length == resolvers.length ? "" : " not provided by a SupplierParameterResolver";
            throw new IllegalArgumentException("Method " + method + " must have " + expected.length + " arguments"
                    + resolvedHint + ": " + Arrays.toString(expected));
        }
        for (int i = 0; i < expected.length; i++) {
            if (!expected[i].isAssignableFrom(frameworkParameter(i).getType())) {
                throw new IllegalArgumentException("Method " + method + " argument " + (frameworkIndexes[i] + 1)
                        + " must be of type " + expected[i].getName());
            }
        }
        return this;
    }

    /**
     * Checks that all the parameters are provided by a resolver, as required for the methods, like the suppliers of
     * tools or of the planner, that are invoked without any value from the framework.
     *
     * @throws AgenticSystemConfigurationException if a parameter is not supported by any resolver
     */
    public ResolvedParameters checkAllResolved() {
        if (frameworkIndexes.length > 0) {
            throw new AgenticSystemConfigurationException("No SupplierParameterResolver is registered for parameter "
                    + frameworkParameter(0) + " of " + annotationName() + "method " + method + ".");
        }
        return this;
    }

    /**
     * @return the name of the declarative annotation of the method, like "@ToolsSupplier ", or an empty string
     */
    private String annotationName() {
        for (Annotation annotation : method.getAnnotations()) {
            Class<? extends Annotation> type = annotation.annotationType();
            if (type.getPackageName().equals(SupplierParameterResolver.class.getPackageName())) {
                return "@" + type.getSimpleName() + " ";
            }
        }
        return "";
    }

    /**
     * Builds the positional arguments of the method, resolving its resolved parameters and taking the parameters
     * provided by the framework from the given values, in their order. The parameters provided by the framework
     * without a corresponding value are {@code null}.
     */
    public Object[] arguments(Object... frameworkValues) {
        Object[] args = new Object[resolvers.length];
        for (int i = 0; i < resolvers.length; i++) {
            if (resolvers[i] != null) {
                args[i] = resolvers[i].resolve(contexts[i]);
            }
        }
        for (int i = 0; i < frameworkIndexes.length && i < frameworkValues.length; i++) {
            args[frameworkIndexes[i]] = frameworkValues[i];
        }
        return args;
    }

    /**
     * Builds the invocation arguments of the method: the parameters provided by the framework are read from the
     * {@link AgenticScope}, or taken from the additional arguments, and they are the only named arguments. Without an
     * {@link AgenticScope} the parameters provided by the framework are {@code null}.
     */
    public AgentInvocationArguments invocationArguments(
            AgenticScope agenticScope, Map<String, Object> additionalArgs, boolean writeConvertedValues) {
        if (agenticScope == null) {
            return new AgentInvocationArguments(Map.of(), arguments());
        }
        AgentInvocationArguments frameworkArgs =
                agentInvocationArguments(agenticScope, frameworkArguments(), additionalArgs, writeConvertedValues);
        return new AgentInvocationArguments(frameworkArgs.namedArgs(), arguments(frameworkArgs.positionalArgs()));
    }

    /**
     * Invokes the static method, with the parameters provided by the framework taken from the given values. Failures
     * are reported as by {@link DeclarativeUtil#invokeStatic(Method, Object...)}.
     */
    public <T> T invoke(Object... frameworkValues) {
        return DeclarativeUtil.invokeStatic(method, arguments(frameworkValues));
    }

    private record DefaultSupplierParameterResolverContext(
            Class<?> declaringAgentClass, Method supplierMethod, Parameter parameter)
            implements SupplierParameterResolver.Context {}
}
