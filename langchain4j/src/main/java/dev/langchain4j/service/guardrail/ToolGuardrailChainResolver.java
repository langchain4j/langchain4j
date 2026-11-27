package dev.langchain4j.service.guardrail;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.util.function.Predicate.not;

import dev.langchain4j.guardrail.ToolInputGuardrail;
import dev.langchain4j.guardrail.ToolInputGuardrailExecutor;
import dev.langchain4j.guardrail.ToolOutputGuardrail;
import dev.langchain4j.guardrail.ToolOutputGuardrailExecutor;
import dev.langchain4j.guardrail.config.ToolInputGuardrailsConfig;
import dev.langchain4j.guardrail.config.ToolOutputGuardrailsConfig;
import dev.langchain4j.service.tool.AiServiceTool;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.spi.classloading.ClassMetadataProviderFactory;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import dev.langchain4j.service.tool.ToolProvider;
import java.util.Arrays;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves the tool guardrail chains of the tools of an AI service: what {@link GuardrailServiceBuilder} does at build
 * time for the methods of an AI service, this does whenever a tool is bound (see {@link DefaultToolGuardrailService}
 * for why). It is created by {@link ToolGuardrailServiceBuilder#build()} and is immutable.
 * <p>
 *     The chain of a tool is, in this order: the global guardrails (given to the builder), the guardrails of the
 *     nearest {@link ToolInputGuardrails} / {@link ToolOutputGuardrails} annotation of the class hierarchy of the tool,
 *     those of the nearest annotation of the tool method in its override chain, then the {@link AiServiceTool}
 *     guardrails.
 * </p>
 * <p>
 *     Duplicates are removed from a chain, keeping the first occurrence (so the global position wins). A guardrail
 *     created from a class, by the builder or from an annotation, is identified by its class: these guardrails are
 *     created without arguments and the configuration is the same for the whole chain, so they are identical. A
 *     guardrail instance supplied by the user is only identified by itself.
 * </p>
 * <p>
 *     The executors of the global guardrails alone are created once, and shared by every tool that has no guardrails
 *     of its own (no annotations and no {@link AiServiceTool} guardrails), such as the tools of an MCP server, which
 *     are bound anew for every invocation. Executors are immutable, so sharing them is safe.
 * </p>
 */
final class ToolGuardrailChainResolver {

    private static final Logger log = LoggerFactory.getLogger(ToolGuardrailChainResolver.class);

    private final List<GuardrailEntry<ToolInputGuardrail>> globalInputGuardrails;
    private final List<GuardrailEntry<ToolOutputGuardrail>> globalOutputGuardrails;
    private final ToolInputGuardrailsConfig inputConfig;
    private final ToolOutputGuardrailsConfig outputConfig;
    private final ClassMetadataProviderFactory<Object> classMetadataProviderFactory;
    private final Optional<ToolInputGuardrailExecutor> globalInputExecutor;
    private final Optional<ToolOutputGuardrailExecutor> globalOutputExecutor;
    private final Map<AnnotationSite, List<? extends GuardrailEntry<?>>> annotationGuardrails =
            new ConcurrentHashMap<>();

    /**
     * @param globalInputGuardrails        the input guardrails that apply to every tool, in the order they run
     * @param globalOutputGuardrails       the output guardrails that apply to every tool, in the order they run
     * @param inputConfig                  the configuration of the input executors. {@code null} means the default.
     * @param outputConfig                 the configuration of the output executors. {@code null} means the default.
     * @param classMetadataProviderFactory finds the annotations of the classes of the tools
     */
    ToolGuardrailChainResolver(
            List<GuardrailEntry<ToolInputGuardrail>> globalInputGuardrails,
            List<GuardrailEntry<ToolOutputGuardrail>> globalOutputGuardrails,
            ToolInputGuardrailsConfig inputConfig,
            ToolOutputGuardrailsConfig outputConfig,
            ClassMetadataProviderFactory<Object> classMetadataProviderFactory) {
        this.globalInputGuardrails = copy(globalInputGuardrails);
        this.globalOutputGuardrails = copy(globalOutputGuardrails);
        this.inputConfig = inputConfig;
        this.outputConfig = outputConfig;
        this.classMetadataProviderFactory = ensureNotNull(classMetadataProviderFactory, "classMetadataProviderFactory");
        this.globalInputExecutor = executor(
                chain(this.globalInputGuardrails, List.of()), guardrails -> newInputExecutor(inputConfig, guardrails));
        this.globalOutputExecutor = executor(
                chain(this.globalOutputGuardrails, List.of()),
                guardrails -> newOutputExecutor(outputConfig, guardrails));
    }

    /**
     * The configuration of the input guardrails, the default one when none was given.
     */
    ToolInputGuardrailsConfig inputConfig() {
        return Optional.ofNullable(this.inputConfig)
                .orElseGet(() -> ToolInputGuardrailsConfig.builder().build());
    }

    /**
     * Resolves the chains of the tool, and returns the key that holds their executors.
     *
     * @param owner     the service the tool is bound to
     * @param toolClass the class whose annotations apply to the tool. {@code null} means the runtime class of the
     *                  object of its {@link DefaultToolExecutor}, if the tool has one (see {@link #annotatedClass}).
     * @param tool      the tool
     */
    DefaultToolKey resolve(DefaultToolGuardrailService owner, Class<?> toolClass, AiServiceTool tool) {
        ensureNotNull(tool, "tool");
        var defaultToolExecutor = Optional.of(tool.toolExecutor())
                .filter(DefaultToolExecutor.class::isInstance)
                .map(DefaultToolExecutor.class::cast);
        var method = defaultToolExecutor.map(DefaultToolExecutor::originalMethod).orElse(null);

        Class<?> annotatedClass = Optional.<Class<?>>ofNullable(toolClass)
                .or(() -> defaultToolExecutor.<Class<?>>map(DefaultToolExecutor::targetClass))
                .<Class<?>>map(clazz -> (method != null) ? annotatedClass(clazz, method) : clazz)
                .orElse(null);

        var ownInputGuardrails = ownGuardrails(
                annotationGuardrails(annotatedClass, method, ToolInputGuardrails.class, ToolInputGuardrails::value),
                tool.toolInputGuardrails());

        var ownOutputGuardrails = ownGuardrails(
                annotationGuardrails(annotatedClass, method, ToolOutputGuardrails.class, ToolOutputGuardrails::value),
                tool.toolOutputGuardrails());

        return new DefaultToolKey(
                owner,
                tool.toolSpecification(),
                tool.toolSource(),
                chainExecutor(
                        this.globalInputGuardrails,
                        ownInputGuardrails,
                        this.globalInputExecutor,
                        guardrails -> newInputExecutor(this.inputConfig, guardrails)),
                chainExecutor(
                        this.globalOutputGuardrails,
                        ownOutputGuardrails,
                        this.globalOutputExecutor,
                        guardrails -> newOutputExecutor(this.outputConfig, guardrails)));
    }

    /**
     * The executor of the chain of a tool: the shared executor of the global guardrails when the tool has no
     * guardrails of its own, otherwise a new executor of the whole chain.
     */
    private static <G, E> Optional<E> chainExecutor(
            List<GuardrailEntry<G>> global,
            List<GuardrailEntry<G>> own,
            Optional<E> globalExecutor,
            Function<List<G>, E> newExecutor) {
        return own.isEmpty() ? globalExecutor : executor(chain(global, own), newExecutor);
    }

    /**
     * An executor of the guardrails, or none when there are no guardrails.
     */
    private static <G, E> Optional<E> executor(List<G> guardrails, Function<List<G>, E> newExecutor) {
        return Optional.of(guardrails).filter(not(List::isEmpty)).map(newExecutor);
    }

    static ToolInputGuardrailExecutor newInputExecutor(
            ToolInputGuardrailsConfig config, List<ToolInputGuardrail> guardrails) {

        return ToolInputGuardrailExecutor.builder()
                .config(config)
                .guardrails(guardrails)
                .build();
    }

    static ToolOutputGuardrailExecutor newOutputExecutor(
            ToolOutputGuardrailsConfig config, List<ToolOutputGuardrail> guardrails) {

        return ToolOutputGuardrailExecutor.builder()
                .config(config)
                .guardrails(guardrails)
                .build();
    }

    /**
     * The class whose hierarchy holds the annotations of a tool method: the class of the object the method is invoked
     * on when it is the declaring class of the method or a subclass of it (a proxy that subclasses the class of the
     * application, such as a CGLIB proxy, reaches the annotations of that class through its hierarchy). Otherwise
     * (a JDK dynamic proxy, which only implements the interfaces) the declaring class of the method, or the
     * annotations of the class of the application would be lost.
     */
    private static Class<?> annotatedClass(Class<?> runtimeClass, Method method) {
        return method.getDeclaringClass().isAssignableFrom(runtimeClass) ? runtimeClass : method.getDeclaringClass();
    }

    /**
     * The annotations of the tool: the nearest one of its class hierarchy, then the nearest one of its method in the
     * override chain. The two levels add up; within a level, the nearest declaration wins.
     *
     * @param toolClass the class of the tool, {@code null} if it has none
     * @param method    the method of the tool, {@code null} if it has none
     */
    private <A extends Annotation> List<A> annotations(Class<?> toolClass, Method method, Class<A> annotationType) {
        return Stream.concat(
                        Optional.ofNullable(toolClass)
                                .flatMap(clazz -> findClassAnnotation(clazz, annotationType))
                                .stream(),
                        Optional.ofNullable(method)
                                .flatMap(toolMethod -> findMethodAnnotation(
                                        Optional.<Class<?>>ofNullable(toolClass)
                                                .orElseGet(toolMethod::getDeclaringClass),
                                        toolMethod,
                                        annotationType))
                                .stream())
                .toList();
    }

    /**
     * The guardrails created from the annotations of the tool (class, then method). They are created once per class and
     * method, so a tool that is bound again (for example a tool of a {@link ToolProvider}, bound for every invocation)
     * gets the same guardrails.
     */
    @SuppressWarnings("unchecked")
    private <A extends Annotation, G> List<GuardrailEntry<G>> annotationGuardrails(
            Class<?> toolClass,
            Method method,
            Class<A> annotationType,
            Function<A, Class<? extends G>[]> guardrailClasses) {
        return (List<GuardrailEntry<G>>) (List<?>) this.annotationGuardrails.computeIfAbsent(
                new AnnotationSite(toolClass, method, annotationType),
                site -> annotations(toolClass, method, annotationType).stream()
                        .flatMap(annotation -> Stream.of(guardrailClasses.apply(annotation)))
                        .map(guardrailClass -> GuardrailEntry.<G>fromClass(
                                guardrailClass, GuardrailBuilderSettings.instantiate(guardrailClass)))
                        .toList());
    }

    /**
     * The guardrails that belong to the tool itself: those of its annotations (class, then method), then the
     * {@link AiServiceTool} guardrails.
     */
    private static <G> List<GuardrailEntry<G>> ownGuardrails(
            List<GuardrailEntry<G>> fromAnnotations, List<? extends G> guardrailInstances) {
        return Stream.concat(
                        fromAnnotations.stream(), guardrailInstances.stream().map(GuardrailEntry::<G>fromInstance))
                .toList();
    }

    /**
     * Finds the annotation on the class, then on its superclasses, then on its interfaces, through the
     * {@link ClassMetadataProviderFactory}: the nearest one wins, so an annotation on a subclass replaces the one of its
     * parents (and an annotation with no guardrails removes them). The answer of the factory is final: a class it
     * returns no annotation for has none.
     */
    private <A extends Annotation> Optional<A> findClassAnnotation(Class<?> clazz, Class<A> annotationType) {
        return nearest(
                hierarchy(clazz)
                        .flatMap(c -> this.classMetadataProviderFactory.getAnnotation(c, annotationType).stream()
                                .map(annotation -> new Declared<>(c.getName(), annotation)))
                        .toList(),
                clazz.getName(),
                annotationType);
    }

    /**
     * Finds the annotation of a tool method in its override chain, by reflection: on the method of {@code clazz} (the
     * class the tool runs on) that has the signature of the tool method, then on the methods it overrides in the
     * superclasses, then in the interfaces, whichever of them carries {@code @Tool}. The nearest one wins, so an
     * annotation on an implementation applies when {@code @Tool} is on an interface, and the reverse.
     */
    private static <A extends Annotation> Optional<A> findMethodAnnotation(
            Class<?> clazz, Method method, Class<A> annotationType) {
        // Read by reflection for now: the ClassMetadataProviderFactory identifies methods by its own keys, and has no
        // way yet to find the key of a Method. Going through the factory for methods too comes with that.
        return nearest(
                hierarchy(clazz)
                        .flatMap(c -> sameMethodIn(c, method).stream())
                        .flatMap(m -> Optional.ofNullable(m.getAnnotation(annotationType)).stream()
                                .map(annotation -> new Declared<>(
                                        "%s#%s".formatted(m.getDeclaringClass().getName(), m.getName()), annotation)))
                        .toList(),
                "%s#%s".formatted(clazz.getName(), method.getName()),
                annotationType);
    }

    /**
     * The method of {@code clazz} that is {@code method} or overrides it, or that it overrides: the same name and
     * parameter types, or, for a method of a generic type, parameter types that are assignable either way (the
     * parameters of a generic interface method are erased). Bridge methods are skipped.
     */
    private static Optional<Method> sameMethodIn(Class<?> clazz, Method method) {
        var candidates = Stream.of(clazz.getDeclaredMethods())
                .filter(not(Method::isBridge))
                .filter(candidate -> candidate.getName().equals(method.getName()))
                .filter(candidate -> candidate.getParameterCount() == method.getParameterCount())
                .toList();
        return candidates.stream()
                .filter(candidate -> Arrays.equals(candidate.getParameterTypes(), method.getParameterTypes()))
                .findFirst()
                .or(() -> candidates.stream()
                        .filter(candidate -> hasAssignableParameters(candidate, method))
                        .findFirst());
    }

    private static boolean hasAssignableParameters(Method candidate, Method method) {
        var candidateTypes = candidate.getParameterTypes();
        var methodTypes = method.getParameterTypes();
        return IntStream.range(0, methodTypes.length)
                .allMatch(i -> candidateTypes[i].isAssignableFrom(methodTypes[i])
                        || methodTypes[i].isAssignableFrom(candidateTypes[i]));
    }

    /**
     * The nearest of the declarations of an annotation, found nearest first. When a nearer one hides others, this is
     * logged at DEBUG, since the guardrails of the hidden ones do not run.
     */
    private static <A extends Annotation> Optional<A> nearest(
            List<Declared<A>> declarations, String site, Class<A> annotationType) {
        if ((declarations.size() > 1) && log.isDebugEnabled()) {
            log.debug(
                    "@{} of {} applies to {}, and hides the one(s) of {}",
                    annotationType.getSimpleName(),
                    declarations.get(0).site(),
                    site,
                    declarations.stream().skip(1).map(Declared::site).toList());
        }
        return declarations.stream().findFirst().map(Declared::annotation);
    }

    /**
     * An annotation and where it is declared (a class, or a method), for the log.
     */
    private record Declared<A extends Annotation>(String site, A annotation) {}

    private static Stream<Class<?>> hierarchy(Class<?> clazz) {
        var classes = new ArrayList<Class<?>>();

        for (Class<?> current = clazz; current != null; current = current.getSuperclass()) {
            classes.add(current);
        }

        var interfaces = classes.stream()
                .flatMap(c -> Stream.of(c.getInterfaces()))
                .flatMap(ToolGuardrailChainResolver::withSuperInterfaces);

        return Stream.concat(classes.stream(), interfaces).distinct();
    }

    private static Stream<Class<?>> withSuperInterfaces(Class<?> anInterface) {
        return Stream.concat(
                Stream.of(anInterface),
                Stream.of(anInterface.getInterfaces()).flatMap(ToolGuardrailChainResolver::withSuperInterfaces));
    }

    /**
     * Concatenates the global guardrails and the ones of the tool, removing duplicates and keeping the first
     * occurrence. This is the one place that decides what a duplicate is: a guardrail created from a class is
     * identified by its class, a user-supplied instance by its identity.
     */
    static <G> List<G> chain(List<GuardrailEntry<G>> global, List<GuardrailEntry<G>> own) {
        return Stream.concat(global.stream(), own.stream())
                .collect(Collectors.toMap(
                        ToolGuardrailChainResolver::identity,
                        GuardrailEntry::guardrail,
                        (first, duplicate) -> first,
                        LinkedHashMap::new))
                .values()
                .stream()
                .toList();
    }

    /**
     * What identifies a guardrail of a chain: its class for a guardrail created from a class, the instance itself for
     * a user-supplied one.
     */
    private static Object identity(GuardrailEntry<?> entry) {
        return entry.fromClass() ? entry.guardrailClass() : new SameInstance(entry.guardrail());
    }

    /**
     * Where the guardrails of a tool are declared: its class and method (each may be {@code null}), for one annotation.
     */
    private record AnnotationSite(Class<?> toolClass, Method method, Class<? extends Annotation> annotationType) {}

    /**
     * Identifies a guardrail instance by reference, not by {@code equals}.
     */
    private record SameInstance(Object guardrail) {

        @Override
        public boolean equals(Object o) {
            return (o instanceof SameInstance other) && (guardrail == other.guardrail);
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(guardrail);
        }
    }
}
