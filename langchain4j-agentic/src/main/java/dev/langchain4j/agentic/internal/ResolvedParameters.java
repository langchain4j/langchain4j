package dev.langchain4j.agentic.internal;

import static dev.langchain4j.agentic.internal.AgentUtil.agentInvocationArguments;
import static dev.langchain4j.agentic.internal.AgentUtil.argumentFromParameter;

import dev.langchain4j.Internal;
import dev.langchain4j.agentic.Agent;
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
 * The parameters of a helper method of an agent, a static method declared on the agent type or an instance method of
 * one of its helper objects, split once, when the agent is created, between the parameters provided by a registered
 * {@link SupplierParameterResolver} and the parameters provided by the agentic framework, like the
 * {@link AgenticScope}, its state, the memory id or a value passed by the caller.
 * <p>
 * Only the parameters provided by the framework are agent arguments: the resolved ones are never read from the
 * {@link AgenticScope}, nor reported to listeners and planners.
 */
@Internal
public final class ResolvedParameters {

    private final HelperMethod helper;
    private final SupplierParameterResolver[] resolvers;
    private final SupplierParameterResolver.Context[] contexts;
    private final int[] frameworkIndexes;
    // The values of the resolved parameters when they are resolved once, at the creation of the agent, else null
    private final Object[] resolvedValues;
    private volatile List<AgentArgument> frameworkArguments;

    private ResolvedParameters(
            Class<?> agentType, HelperMethod helper, List<SupplierParameterResolver> availableResolvers) {
        this.helper = helper;
        Method method = helper.method();
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
        this.resolvedValues = null;
    }

    private ResolvedParameters(ResolvedParameters parameters, Object[] resolvedValues) {
        this.helper = parameters.helper;
        this.resolvers = parameters.resolvers;
        this.contexts = parameters.contexts;
        this.frameworkIndexes = parameters.frameworkIndexes;
        this.resolvedValues = resolvedValues;
    }

    /**
     * Splits the parameters of the given method using the {@link SupplierParameterResolver}s registered in
     * {@link DeclarativeUtil}. The method is invoked without a target: it is a static method, or an instance method
     * whose target is given at each invocation, like the {@link Agent} method of a non-AI
     * agent.
     *
     * @param agentType the agent class or interface declaring the method, or inheriting it
     * @param method the method to invoke
     */
    public static ResolvedParameters of(Class<?> agentType, Method method) {
        return of(agentType, new HelperMethod(method, null));
    }

    /**
     * Splits the parameters of the given helper method using the {@link SupplierParameterResolver}s registered in
     * {@link DeclarativeUtil}.
     *
     * @param agentType the agent class or interface the method is a helper of
     * @param helper the method to invoke, with its target
     */
    public static ResolvedParameters of(Class<?> agentType, HelperMethod helper) {
        return new ResolvedParameters(agentType, helper, DeclarativeUtil.getSupplierParameterResolvers());
    }

    public Method method() {
        return helper.method();
    }

    /**
     * @return the parameters provided by the framework, as agent arguments, in the order they are declared
     */
    public List<AgentArgument> frameworkArguments() {
        // computed lazily, because only the parameters read from the AgenticScope need a name
        List<AgentArgument> arguments = frameworkArguments;
        if (arguments == null) {
            Parameter[] parameters = method().getParameters();
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
        return method().getParameters()[frameworkIndexes[position]];
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
            throw new IllegalArgumentException("Method " + method() + " must have " + expected.length + " arguments"
                    + resolvedHint + ": " + Arrays.toString(expected));
        }
        for (int i = 0; i < expected.length; i++) {
            if (!expected[i].isAssignableFrom(frameworkParameter(i).getType())) {
                throw new IllegalArgumentException("Method " + method() + " argument " + (frameworkIndexes[i] + 1)
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
                    + frameworkParameter(0) + " of " + annotationName() + "method " + method() + ".");
        }
        return this;
    }

    /**
     * @return the name of the declarative annotation of the method, like "@ToolsSupplier ", or an empty string
     */
    private String annotationName() {
        for (Annotation annotation : method().getAnnotations()) {
            Class<? extends Annotation> type = annotation.annotationType();
            if (type.getPackageName().equals(SupplierParameterResolver.class.getPackageName())) {
                return "@" + type.getSimpleName() + " ";
            }
        }
        return "";
    }

    /**
     * Returns a copy of these parameters whose resolved parameters are resolved now, once, instead of at each
     * invocation of the method. For the methods invoked later than the creation of the agent, like the one annotated
     * with {@code @A2AClientCustomizer}, which runs when the A2A client is created on the first invocation of the
     * agent: a value resolved per request, like a token, is then the one of the thread creating the agent, as for
     * the other supplier methods, and not the one of the first caller.
     */
    public ResolvedParameters resolvedNow() {
        return new ResolvedParameters(this, arguments());
    }

    /**
     * Builds the positional arguments of the method, resolving its resolved parameters, unless already resolved by
     * {@link #resolvedNow()}, and taking the parameters provided by the framework from the given values, in their
     * order. The parameters provided by the framework without a corresponding value are {@code null}.
     */
    public Object[] arguments(Object... frameworkValues) {
        Object[] args = resolvedValues != null ? resolvedValues.clone() : new Object[resolvers.length];
        if (resolvedValues == null) {
            for (int i = 0; i < resolvers.length; i++) {
                if (resolvers[i] != null) {
                    args[i] = resolvers[i].resolve(contexts[i]);
                }
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
     * Invokes the method on its target, with the parameters provided by the framework taken from the given values.
     * Failures are reported as by {@link HelperMethod#invoke(Object...)}.
     */
    public <T> T invoke(Object... frameworkValues) {
        return helper.invoke(arguments(frameworkValues));
    }

    private record DefaultSupplierParameterResolverContext(
            Class<?> declaringAgentClass, Method supplierMethod, Parameter parameter)
            implements SupplierParameterResolver.Context {}
}
