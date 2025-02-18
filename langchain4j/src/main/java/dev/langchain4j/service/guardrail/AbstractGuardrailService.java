package dev.langchain4j.service.guardrail;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.classloading.ClassInstanceLoader;
import dev.langchain4j.guardrail.Guardrail;
import dev.langchain4j.guardrail.GuardrailParams;
import dev.langchain4j.guardrail.GuardrailResult;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailExecutor;
import dev.langchain4j.guardrail.InputGuardrailParams;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailExecutor;
import dev.langchain4j.guardrail.OutputGuardrailParams;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.guardrail.config.InputGuardrailsConfigBuilder;
import dev.langchain4j.guardrail.config.OutputGuardrailsConfigBuilder;
import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * Responsible for managing and applying input and output guardrails to methods
 * of a specified AI service class. Guardrails are defined through annotations at either the
 * class or method level and are used to enforce constraints, validation, or transformation
 * logic on inputs and outputs of AI service methods.
 *
 * This class handles the initialization, configuration, and execution of input and output
 * guardrail logic for the methods of the associated AI service class, ensuring both input
 * and output constraints are applied automatically when methods are invoked.
 *
 * @param <MK> The type representing a method key, which can be used to identify and manage
 *             methods of the AI service class for guardrail application purposes.
 */
public abstract class AbstractGuardrailService<MK> implements GuardrailService<MK> {
    private final Class<?> aiServiceClass;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final ConcurrentMap<MK, @Nullable InputGuardrailExecutor> inputGuardrails = new ConcurrentHashMap<>(2);
    private final ConcurrentMap<MK, @Nullable OutputGuardrailExecutor> outputGuardrails = new ConcurrentHashMap<>(2);

    public AbstractGuardrailService(Class<?> aiServiceClass) {
        this.aiServiceClass = ensureNotNull(aiServiceClass, "aiServiceClass");
        initialize();
    }

    /**
     * Retrieves an annotation of the specified type from the given method.
     *
     * @param <T> The type of the annotation to locate, which must extend {@link Annotation}.
     * @param method The method from which the annotation is to be retrieved.
     * @param annotationClass The class object corresponding to the annotation type to find.
     * @return An {@code Optional} containing the located annotation, or an empty {@code Optional} if the annotation
     *         is not present on the specified method.
     */
    protected abstract <T extends Annotation> Optional<T> getAnnotation(MK method, Class<T> annotationClass);

    /**
     * Retrieves an annotation of the specified type from the given class.
     *
     * @param <T> The type of the annotation to locate, which must extend {@link Annotation}.
     * @param clazz The class from which the annotation is to be retrieved.
     * @param annotationClass The class object corresponding to the annotation type to find.
     * @return An {@code Optional} containing the located annotation, or an empty {@code Optional} if the annotation
     *         is not present on the specified class.
     */
    protected abstract <T extends Annotation> Optional<T> getAnnotation(Class<?> clazz, Class<T> annotationClass);

    /**
     * Retrieves an iterable containing method keys (MK) for all methods
     * defined in the specified class.
     *
     * @param clazz The class from which to retrieve methods.
     * @return An iterable of method keys corresponding to the methods of the specified class.
     */
    protected abstract Iterable<MK> getMethodsOnClass(Class<?> clazz);

    @Override
    public Class<?> aiServiceClass() {
        return this.aiServiceClass;
    }

    /**
     * Initializes the guardrail service by setting up input and output guardrails for each
     * method in the associated AI service class. This ensures that the appropriate guardrails
     * (input and output) are applied to all methods of the AI service.
     *
     * The initialization process is performed only once.
     */
    private void initialize() {
        if (this.initialized.compareAndSet(false, true)) {
            for (var method : getMethodsOnClass(this.aiServiceClass)) {
                this.inputGuardrails.computeIfAbsent(method, this::computeInputGuardrailsForAiServiceMethod);
                this.outputGuardrails.computeIfAbsent(method, this::computeOutputGuardrailsForAiServiceMethod);
            }
        }
    }

    private InputGuardrailExecutor computeInputGuardrailsForAiServiceMethod(MK method) {
        // For both input & output guardrails, first check the method
        // If nothing on the method, then fall back to the class
        return getAnnotation(method, InputGuardrails.class)
                .or(() -> getAnnotation(this.aiServiceClass, InputGuardrails.class))
                .map(this::computeInputGuardrails)
                .orElse(null);
    }

    private OutputGuardrailExecutor computeOutputGuardrailsForAiServiceMethod(MK method) {
        // For both input & output guardrails, first check the method
        // If nothing on the method, then fall back to the class
        return getAnnotation(method, OutputGuardrails.class)
                .or(() -> getAnnotation(this.aiServiceClass, OutputGuardrails.class))
                .map(this::computeOutputGuardrails)
                .orElse(null);
    }

    protected <P extends GuardrailParams, R extends GuardrailResult<R>, G extends Guardrail<P, R>>
            G getGuardrailClassInstance(Class<G> guardrailClass) {
        ensureNotNull(guardrailClass, "guardrailClass");
        return ClassInstanceLoader.getClassInstance(guardrailClass);
    }

    private InputGuardrailExecutor computeInputGuardrails(InputGuardrails inputGuardrails) {
        return new InputGuardrailExecutor(computeConfig(inputGuardrails.config()), getGuardrails(inputGuardrails));
    }

    private dev.langchain4j.guardrail.config.InputGuardrailsConfig computeConfig(
            dev.langchain4j.service.guardrail.InputGuardrailsConfig config) {
        return InputGuardrailsConfigBuilder.newBuilder().build();
    }

    private <I extends InputGuardrail> List<I> getGuardrails(InputGuardrails inputGuardrails) {
        return Stream.of(inputGuardrails.value())
                .map(guardrailClass -> (I) getGuardrailClassInstance(guardrailClass))
                .toList();
    }

    private OutputGuardrailExecutor computeOutputGuardrails(OutputGuardrails outputGuardrails) {
        return new OutputGuardrailExecutor(computeConfig(outputGuardrails.config()), getGuardrails(outputGuardrails));
    }

    private dev.langchain4j.guardrail.config.OutputGuardrailsConfig computeConfig(
            dev.langchain4j.service.guardrail.OutputGuardrailsConfig config) {
        return OutputGuardrailsConfigBuilder.newBuilder()
                .maxRetries(config.maxRetries())
                .build();
    }

    private <O extends OutputGuardrail> List<O> getGuardrails(OutputGuardrails outputGuardrails) {
        return Stream.of(outputGuardrails.value())
                .map(guardrailClass -> (O) getGuardrailClassInstance(guardrailClass))
                .toList();
    }

    @Override
    public InputGuardrailResult executeGuardrails(@Nullable MK method, InputGuardrailParams params) {
        return Optional.ofNullable(method)
                .map(this.inputGuardrails::get)
                .map(executor -> executor.execute(params))
                .orElseGet(InputGuardrailResult::success);
    }

    @Override
    public OutputGuardrailResult executeGuardrails(@Nullable MK method, OutputGuardrailParams params) {
        return Optional.ofNullable(method)
                .map(this.outputGuardrails::get)
                .map(executor -> executor.execute(params))
                .orElseGet(OutputGuardrailResult::success);
    }

    // These methods below really only exist for testing purposes
    // Thats why they are package-scoped
    Optional<dev.langchain4j.guardrail.config.InputGuardrailsConfig> getInputConfig(MK method) {
        return Optional.ofNullable(this.inputGuardrails.get(method)).map(InputGuardrailExecutor::config);
    }

    Optional<dev.langchain4j.guardrail.config.OutputGuardrailsConfig> getOutputConfig(MK method) {
        return Optional.ofNullable(this.outputGuardrails.get(method)).map(OutputGuardrailExecutor::config);
    }

    List<InputGuardrail> getInputGuardrails(MK method) {
        return Optional.ofNullable(this.inputGuardrails.get(method))
                .map(InputGuardrailExecutor::guardrails)
                .orElseGet(List::of);
    }

    List<OutputGuardrail> getOutputGuardrails(MK method) {
        return Optional.ofNullable(this.outputGuardrails.get(method))
                .map(OutputGuardrailExecutor::guardrails)
                .orElseGet(List::of);
    }
}
