package dev.langchain4j.agentic.internal;

import dev.langchain4j.Internal;
import dev.langchain4j.agentic.declarative.A2AClientCustomizer;
import dev.langchain4j.agentic.declarative.A2AServerUrlSupplier;
import dev.langchain4j.agentic.declarative.ActivationCondition;
import dev.langchain4j.agentic.declarative.AgentListenerSupplier;
import dev.langchain4j.agentic.declarative.BeforeCall;
import dev.langchain4j.agentic.declarative.ChatMemoryProviderSupplier;
import dev.langchain4j.agentic.declarative.ChatMemorySupplier;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.declarative.ContentRetrieverSupplier;
import dev.langchain4j.agentic.declarative.DecisionModelSupplier;
import dev.langchain4j.agentic.declarative.DecisionThresholdSupplier;
import dev.langchain4j.agentic.declarative.DeclarativeUtil;
import dev.langchain4j.agentic.declarative.ErrorHandler;
import dev.langchain4j.agentic.declarative.ExitCondition;
import dev.langchain4j.agentic.declarative.McpClientSupplier;
import dev.langchain4j.agentic.declarative.Output;
import dev.langchain4j.agentic.declarative.ParallelExecutor;
import dev.langchain4j.agentic.declarative.PlannerSupplier;
import dev.langchain4j.agentic.declarative.RetrievalAugmentorSupplier;
import dev.langchain4j.agentic.declarative.StreamingChatModelSupplier;
import dev.langchain4j.agentic.declarative.SupervisorRequest;
import dev.langchain4j.agentic.declarative.SystemMessageProviderSupplier;
import dev.langchain4j.agentic.declarative.ToolProviderSupplier;
import dev.langchain4j.agentic.declarative.ToolSearchStrategySupplier;
import dev.langchain4j.agentic.declarative.ToolsSupplier;
import dev.langchain4j.agentic.declarative.UserMessageProviderSupplier;
import dev.langchain4j.agentic.planner.AgenticSystemConfigurationException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Locates the helper methods of a declarative agent: the static methods of the agent type, as declared on it or
 * inherited, and the instance methods of its helper objects, searched in the class hierarchy of each object.
 * <p>
 * For one annotation an agent can have a single helper, unless the annotation is allowed several times: a static
 * method on the type together with an instance method on a helper object, or two instance methods, are a
 * configuration error.
 * <p>
 * A helper declared by the agent type itself that is not static needs one of the helper objects to be invoked on,
 * an instance of its declaring class, typically the agent bean of a container: {@link #checkNonStaticHelpers}
 * rejects the agent otherwise, instead of skipping the helper silently. An instance helper can have any visibility
 * but private, and cannot be final: a container invokes it through the proxy of its bean, which cannot override such
 * a method, so it would run on the proxy itself, without the injected fields of the bean.
 */
@Internal
public final class AgentHelpers {

    /**
     * The annotations marking the helper methods of a declarative agent, as opposed to the ones marking its agent
     * method and the parameters.
     */
    public static final List<Class<? extends Annotation>> HELPER_ANNOTATIONS = List.of(
            ChatModelSupplier.class,
            StreamingChatModelSupplier.class,
            DecisionModelSupplier.class,
            DecisionThresholdSupplier.class,
            ToolsSupplier.class,
            ToolProviderSupplier.class,
            ToolSearchStrategySupplier.class,
            ContentRetrieverSupplier.class,
            RetrievalAugmentorSupplier.class,
            ChatMemorySupplier.class,
            ChatMemoryProviderSupplier.class,
            AgentListenerSupplier.class,
            SystemMessageProviderSupplier.class,
            UserMessageProviderSupplier.class,
            McpClientSupplier.class,
            PlannerSupplier.class,
            A2AServerUrlSupplier.class,
            A2AClientCustomizer.class,
            ParallelExecutor.class,
            ExitCondition.class,
            ActivationCondition.class,
            Output.class,
            BeforeCall.class,
            ErrorHandler.class,
            SupervisorRequest.class);

    private final Class<?> agentType;
    private final List<Object> helperObjects;

    private AgentHelpers(Class<?> agentType, List<Object> helperObjects) {
        this.agentType = agentType;
        this.helperObjects = distinct(agentType, helperObjects);
    }

    /**
     * The helpers of an agent without helper objects: its static methods only.
     */
    public static AgentHelpers staticOnly(Class<?> agentType) {
        return new AgentHelpers(agentType, List.of());
    }

    /**
     * The helpers of an agent: its static methods and the instance methods of the given helper objects, which are
     * all the ones of the agent, so a non-static helper of the type without an object to be invoked on is rejected.
     */
    public static AgentHelpers of(Class<?> agentType, List<Object> helperObjects) {
        AgentHelpers helpers = new AgentHelpers(agentType, helperObjects);
        checkNonStaticHelpers(agentType, helpers.helperObjects);
        return helpers;
    }

    /**
     * Checks that every non-static method of the agent type annotated with a helper annotation has an object to be
     * invoked on: one of the helper objects must be an instance of its declaring class, typically the agent itself
     * when it is the bean of a container.
     *
     * @throws AgenticSystemConfigurationException if such a method has none, instead of skipping it silently
     */
    public static void checkNonStaticHelpers(Class<?> agentType, List<Object> helperObjects) {
        for (Method method : methodsAnnotatedWith(agentType, HELPER_ANNOTATIONS)) {
            if (Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            Class<? extends Annotation> annotation = helperAnnotationOf(method);
            checkOverridable(method, annotation);
            if (helperObjects.stream().noneMatch(method.getDeclaringClass()::isInstance)) {
                throw new AgenticSystemConfigurationException("The method " + method + " of the agent "
                        + agentType.getName() + " is annotated with @" + annotation.getSimpleName()
                        + " but it is not static: make it static, or provide the agent as one of its helper objects.");
            }
        }
    }

    /**
     * Checks that the given instance helper method can be overridden by the proxy a container puts in front of its
     * bean: a private or final method cannot, so it would run on the proxy itself, without the injected fields of the
     * bean, failing or using wrong values at its first call.
     *
     * @throws AgenticSystemConfigurationException if the method is private or final
     */
    private static void checkOverridable(Method method, Class<? extends Annotation> annotation) {
        int modifiers = method.getModifiers();
        if (Modifier.isPrivate(modifiers) || Modifier.isFinal(modifiers)) {
            throw new AgenticSystemConfigurationException("The method " + method + " annotated with @"
                    + annotation.getSimpleName() + " is " + (Modifier.isPrivate(modifiers) ? "private" : "final")
                    + ": an instance helper cannot be private or final, because the proxy of a container cannot"
                    + " override it, so it would run on the proxy, without the injected fields of the bean. Remove"
                    + " the modifier, or make the method static.");
        }
    }

    private static Class<? extends Annotation> helperAnnotationOf(Method method) {
        for (Class<? extends Annotation> annotation : HELPER_ANNOTATIONS) {
            if (method.isAnnotationPresent(annotation)) {
                return annotation;
            }
        }
        throw new IllegalStateException("No helper annotation on " + method);
    }

    public Class<?> agentType() {
        return agentType;
    }

    /**
     * Selects the single helper method annotated with the given annotation.
     *
     * @throws AgenticSystemConfigurationException if there is more than one such method
     */
    public Optional<HelperMethod> select(Class<? extends Annotation> annotation) {
        return select(annotation, method -> true);
    }

    /**
     * Selects the single helper method annotated with the given annotation and matching the given filter.
     *
     * @throws AgenticSystemConfigurationException if there is more than one such method
     */
    public Optional<HelperMethod> select(Class<? extends Annotation> annotation, Predicate<Method> filter) {
        Optional<Method> staticMethod = DeclarativeUtil.selectMethod(
                agentType, method -> method.isAnnotationPresent(annotation) && filter.test(method));
        List<HelperMethod> instanceMethods = instanceMethods(annotation, filter);
        if (instanceMethods.size() > 1) {
            throw conflict(annotation, instanceMethods.get(0), instanceMethods.get(1));
        }
        if (staticMethod.isPresent() && !instanceMethods.isEmpty()) {
            throw conflict(annotation, HelperMethod.ofStatic(staticMethod.get()), instanceMethods.get(0));
        }
        if (!instanceMethods.isEmpty()) {
            return Optional.of(instanceMethods.get(0));
        }
        return staticMethod.map(HelperMethod::ofStatic);
    }

    /**
     * Selects all the helper methods annotated with the given annotation, for the annotations allowed several times
     * on one agent.
     */
    public List<HelperMethod> selectAll(Class<? extends Annotation> annotation) {
        List<HelperMethod> helpers = new ArrayList<>();
        for (Method method : DeclarativeUtil.staticMethodsAnnotatedWith(agentType, annotation)) {
            helpers.add(HelperMethod.ofStatic(method));
        }
        helpers.addAll(instanceMethods(annotation, method -> true));
        return helpers;
    }

    private List<HelperMethod> instanceMethods(Class<? extends Annotation> annotation, Predicate<Method> filter) {
        List<HelperMethod> helpers = new ArrayList<>();
        for (Object helperObject : helperObjects) {
            for (Method method : instanceMethodsAnnotatedWith(helperObject.getClass(), annotation)) {
                if (filter.test(method)) {
                    helpers.add(new HelperMethod(method, helperObject));
                }
            }
        }
        return helpers;
    }

    /**
     * The non-static methods annotated with the given annotation declared by the given type, its superclasses and
     * its interfaces, made accessible. When an annotated method is overridden by another annotated method, only the
     * most specific one is returned; an override without the annotation is not considered, so that the method is
     * invoked through it anyway, as when the helper object is a proxy generated by a container.
     *
     * @throws AgenticSystemConfigurationException if one of the methods is private or final
     */
    private static List<Method> instanceMethodsAnnotatedWith(Class<?> type, Class<? extends Annotation> annotation) {
        List<Method> methods = new ArrayList<>();
        for (Method method : methodsAnnotatedWith(type, List.of(annotation))) {
            if (!Modifier.isStatic(method.getModifiers())) {
                checkOverridable(method, annotation);
                methods.add(method);
            }
        }
        return methods;
    }

    /**
     * The methods, static or not, annotated with any of the given annotations declared by the given type, its
     * superclasses and its interfaces, made accessible, with the same rule as
     * {@link #instanceMethodsAnnotatedWith} for the overridden methods: an override without the annotation does not
     * hide the annotated method it overrides, so that the method of a class is found on a proxy of the class too.
     */
    public static List<Method> methodsAnnotatedWith(
            Class<?> type, Collection<Class<? extends Annotation>> annotations) {
        List<Method> methods = new ArrayList<>();
        collectMethodsAnnotatedWith(type, annotations, methods, new HashSet<>(), new HashSet<>());
        return methods;
    }

    private static void collectMethodsAnnotatedWith(
            Class<?> type,
            Collection<Class<? extends Annotation>> annotations,
            List<Method> methods,
            Set<String> seenSignatures,
            Set<Class<?>> visitedTypes) {
        if (type == null || type == Object.class || !visitedTypes.add(type)) {
            return;
        }
        for (Method method : type.getDeclaredMethods()) {
            if (isAnnotatedWithAny(method, annotations)
                    && !method.isBridge()
                    && !method.isSynthetic()
                    && seenSignatures.add(signature(method))) {
                method.setAccessible(true);
                methods.add(method);
            }
        }
        collectMethodsAnnotatedWith(type.getSuperclass(), annotations, methods, seenSignatures, visitedTypes);
        for (Class<?> interf : type.getInterfaces()) {
            collectMethodsAnnotatedWith(interf, annotations, methods, seenSignatures, visitedTypes);
        }
    }

    private static boolean isAnnotatedWithAny(Method method, Collection<Class<? extends Annotation>> annotations) {
        for (Class<? extends Annotation> annotation : annotations) {
            if (method.isAnnotationPresent(annotation)) {
                return true;
            }
        }
        return false;
    }

    private static String signature(Method method) {
        StringBuilder signature = new StringBuilder(method.getName()).append('(');
        for (Class<?> parameterType : method.getParameterTypes()) {
            signature.append(parameterType.getName()).append(',');
        }
        return signature.append(')').toString();
    }

    private AgenticSystemConfigurationException conflict(
            Class<? extends Annotation> annotation, HelperMethod first, HelperMethod second) {
        return new AgenticSystemConfigurationException("The agent " + agentType.getName()
                + " has two @" + annotation.getSimpleName() + " helpers, " + first.describe() + " and "
                + second.describe() + ": provide only one of them, either as a static method of the agent type or as"
                + " an instance method of one helper object.");
    }

    private static List<Object> distinct(Class<?> agentType, List<Object> helperObjects) {
        List<Object> distinct = new ArrayList<>(helperObjects.size());
        for (Object helperObject : helperObjects) {
            if (helperObject == null) {
                throw new IllegalArgumentException(
                        "The helperObjectsResolver returned a null helper object for the agent " + agentType.getName());
            }
            if (distinct.stream().noneMatch(other -> other == helperObject)) {
                distinct.add(helperObject);
            }
        }
        return List.copyOf(distinct);
    }
}
