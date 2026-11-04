package dev.langchain4j.agentic.internal;

import dev.langchain4j.Internal;
import dev.langchain4j.agentic.declarative.DeclarativeUtil;
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
 * configuration error. When the static helpers have already been applied to a builder, as by the constructor of a
 * workflow builder, the instance given by {@link #instanceOnly} still detects the conflicts but only returns the
 * instance helpers.
 */
@Internal
public final class AgentHelpers {

    private final Class<?> agentType;
    private final List<Object> helperObjects;
    private final boolean staticsApplied;

    private AgentHelpers(Class<?> agentType, List<Object> helperObjects, boolean staticsApplied) {
        this.agentType = agentType;
        this.helperObjects = distinct(agentType, helperObjects);
        this.staticsApplied = staticsApplied;
    }

    /**
     * The helpers of an agent without helper objects: its static methods only.
     */
    public static AgentHelpers staticOnly(Class<?> agentType) {
        return new AgentHelpers(agentType, List.of(), false);
    }

    /**
     * The helpers of an agent: its static methods and the instance methods of the given helper objects.
     */
    public static AgentHelpers of(Class<?> agentType, List<Object> helperObjects) {
        return new AgentHelpers(agentType, helperObjects, false);
    }

    /**
     * The helpers of an agent whose static methods have already been applied: the lookups return the instance
     * methods of the given helper objects only, but a conflict with a static method is still an error.
     */
    public static AgentHelpers instanceOnly(Class<?> agentType, List<Object> helperObjects) {
        return new AgentHelpers(agentType, helperObjects, true);
    }

    public Class<?> agentType() {
        return agentType;
    }

    /**
     * @return whether the given helper has to be applied: a static helper is not, when the static helpers have
     *         already been applied
     */
    public boolean isApplicable(HelperMethod helper) {
        return !staticsApplied || !helper.isStatic();
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
        return staticsApplied ? Optional.empty() : staticMethod.map(HelperMethod::ofStatic);
    }

    /**
     * Selects all the helper methods annotated with the given annotation, for the annotations allowed several times
     * on one agent. The static methods are included also when they have already been applied, so that the caller
     * can check them against the instance ones: {@link #isApplicable} tells which ones have to be applied.
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
     */
    private static List<Method> instanceMethodsAnnotatedWith(Class<?> type, Class<? extends Annotation> annotation) {
        List<Method> methods = new ArrayList<>();
        for (Method method : methodsAnnotatedWith(type, List.of(annotation))) {
            if (!Modifier.isStatic(method.getModifiers())) {
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
