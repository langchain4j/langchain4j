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
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Resolves the tool guardrail chains of the tools of an AI service: what {@link GuardrailServiceBuilder} does at build
 * time for the methods of an AI service, this does whenever a tool is bound (see {@link DefaultToolGuardrailService}
 * for why). It is created by {@link ToolGuardrailServiceBuilder#build()} and is immutable.
 * <p>
 *     The chain of a tool is, in this order: the global guardrails (given to the builder), the guardrails of the
 *     {@link ToolInputGuardrails} / {@link ToolOutputGuardrails} annotation of the class of the tool, those of the
 *     annotation of the tool method, then the {@link AiServiceTool} guardrails.
 * </p>
 * <p>
 *     Duplicates are removed from a chain, keeping the first occurrence (so the global position wins). A guardrail
 *     created from a class, by the builder or from an annotation, is identified by its class: these guardrails are
 *     created without arguments and the configuration is the same for the whole chain, so they are identical. A
 *     guardrail instance supplied by the user is only identified by itself. If the configuration ever becomes specific
 *     to a level (for example attributes on the annotations), it has to become part of the identity of a guardrail
 *     created from a class, in {@link #chain(List, List)}.
 * </p>
 */
final class ToolGuardrailChainResolver {

    private final List<GuardrailEntry<ToolInputGuardrail>> globalInputGuardrails;
    private final List<GuardrailEntry<ToolOutputGuardrail>> globalOutputGuardrails;
    private final ToolInputGuardrailsConfig inputConfig;
    private final ToolOutputGuardrailsConfig outputConfig;
    private final ClassMetadataProviderFactory<Object> classMetadataProviderFactory;

    /**
     * @param globalInputGuardrails        the input guardrails that apply to every tool, in the order they run
     * @param globalOutputGuardrails       the output guardrails that apply to every tool, in the order they run
     * @param inputConfig                  the configuration of the input executors. {@code null} means the default.
     * @param outputConfig                 the configuration of the output executors. {@code null} means the default.
     * @param classMetadataProviderFactory finds the annotations of the tools
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
    }

    /**
     * Resolves the chains of the tool, and returns the key that holds their executors.
     *
     * @param toolClass the class whose annotations apply to the tool. {@code null} means the declaring class of the
     *                  tool method, if the tool has one.
     * @param tool      the tool
     */
    DefaultToolKey resolve(Class<?> toolClass, AiServiceTool tool) {
        ensureNotNull(tool, "tool");
        var method = (tool.toolExecutor() instanceof DefaultToolExecutor defaultToolExecutor)
                ? defaultToolExecutor.originalMethod()
                : null;

        var annotatedClass = Optional.<Class<?>>ofNullable(toolClass)
                .or(() -> Optional.ofNullable(method).map(Method::getDeclaringClass))
                .orElse(null);

        var inputGuardrails = chain(
                this.globalInputGuardrails,
                ownGuardrails(
                        annotations(annotatedClass, method, ToolInputGuardrails.class),
                        ToolInputGuardrails::value,
                        tool.toolInputGuardrails()));

        var outputGuardrails = chain(
                this.globalOutputGuardrails,
                ownGuardrails(
                        annotations(annotatedClass, method, ToolOutputGuardrails.class),
                        ToolOutputGuardrails::value,
                        tool.toolOutputGuardrails()));

        return new DefaultToolKey(
                tool.name(),
                Optional.of(inputGuardrails)
                        .filter(not(List::isEmpty))
                        .map(guardrails -> newInputExecutor(this.inputConfig, guardrails)),
                Optional.of(outputGuardrails)
                        .filter(not(List::isEmpty))
                        .map(guardrails -> newOutputExecutor(this.outputConfig, guardrails)));
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
     * The annotations of the tool: the one of its class, then the one of its method.
     *
     * @param toolClass the class of the tool, {@code null} if it has none
     * @param method    the method of the tool, {@code null} if it has none
     */
    private <A extends Annotation> List<A> annotations(Class<?> toolClass, Method method, Class<A> annotationType) {
        return Stream.concat(
                        Optional.ofNullable(toolClass)
                                .flatMap(clazz -> findClassAnnotation(clazz, annotationType))
                                .stream(),
                        Optional.ofNullable(method).flatMap(m -> findMethodAnnotation(m, annotationType)).stream())
                .toList();
    }

    /**
     * The guardrails that belong to the tool itself: those of its annotations (class, then method), then the
     * {@link AiServiceTool} guardrails.
     */
    private static <A extends Annotation, G> List<GuardrailEntry<G>> ownGuardrails(
            List<A> annotations,
            Function<A, Class<? extends G>[]> guardrailClasses,
            List<? extends G> guardrailInstances) {
        var fromAnnotations = annotations.stream()
                .flatMap(annotation -> Stream.of(guardrailClasses.apply(annotation)))
                .map(guardrailClass ->
                        GuardrailEntry.<G>fromClass(GuardrailBuilderSettings.instantiate(guardrailClass)));

        return Stream.concat(fromAnnotations, guardrailInstances.stream().map(GuardrailEntry::<G>fromInstance))
                .toList();
    }

    /**
     * Finds the annotation on the class, then on its superclasses, then on its interfaces: the first one found wins,
     * so an annotation on a subclass hides the one of its parents.
     */
    private <A extends Annotation> Optional<A> findClassAnnotation(Class<?> clazz, Class<A> annotationType) {
        return hierarchy(clazz)
                .map(c -> this.classMetadataProviderFactory.getAnnotation(c, annotationType))
                .flatMap(Optional::stream)
                .findFirst();
    }

    /**
     * Finds the annotation of a method through the key the {@link ClassMetadataProviderFactory} uses for it. A method
     * the factory has no key for (a factory may only know the public methods, and tool methods may have any
     * visibility) is looked up directly on the {@link Method}, so that its guardrails are never silently ignored.
     */
    private <A extends Annotation> Optional<A> findMethodAnnotation(Method method, Class<A> annotationType) {
        return Optional.ofNullable(this.classMetadataProviderFactory.resolveMethodKey(method))
                .map(key -> this.classMetadataProviderFactory.getAnnotation(key, annotationType))
                .orElseGet(() -> Optional.ofNullable(method.getAnnotation(annotationType)));
    }

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
        var seenClasses = new HashSet<Class<?>>();
        var seenInstances = Collections.newSetFromMap(new IdentityHashMap<>());

        return Stream.concat(global.stream(), own.stream())
                .filter(entry -> entry.fromClass()
                        ? seenClasses.add(entry.guardrail().getClass())
                        : seenInstances.add(entry.guardrail()))
                .map(GuardrailEntry::guardrail)
                .toList();
    }
}
