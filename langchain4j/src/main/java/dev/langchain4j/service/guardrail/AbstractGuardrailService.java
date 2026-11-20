package dev.langchain4j.service.guardrail;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Internal;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailExecutor;
import dev.langchain4j.guardrail.InputGuardrailRequest;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailExecutor;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Responsible for managing and applying input and output guardrails to methods
 * of a specified AI service class. Guardrails are defined through annotations at either the
 * class or method level and are used to enforce constraints, validation, or transformation
 * logic on inputs and outputs of AI service methods.
 *
 * This class handles the initialization, configuration, and execution of input and output
 * guardrail logic for the methods of the associated AI service class, ensuring both input
 * and output constraints are applied automatically when methods are invoked.
 * <p>
 *     The executors are registered under the method keys of the
 *     {@link dev.langchain4j.spi.classloading.ClassMetadataProviderFactory ClassMetadataProviderFactory} that scanned
 *     the AI service class. A method may be identified by one of those keys, or by its {@link Method}, which the
 *     service translates into the key with
 *     {@link dev.langchain4j.spi.classloading.ClassMetadataProviderFactory#resolveMethodKey(Method)}. The translation
 *     applies to every lookup, blocking and non-blocking alike.
 * </p>
 */
@Internal
public abstract class AbstractGuardrailService implements GuardrailService {
    private final Class<?> aiServiceClass;
    private final KeyedGuardrailExecutors<InputGuardrailRequest, InputGuardrailResult, InputGuardrailExecutor>
            inputGuardrails;
    private final KeyedGuardrailExecutors<OutputGuardrailRequest, OutputGuardrailResult, OutputGuardrailExecutor>
            outputGuardrails;

    protected AbstractGuardrailService(
            Class<?> aiServiceClass,
            Map<Object, InputGuardrailExecutor> inputGuardrails,
            Map<Object, OutputGuardrailExecutor> outputGuardrails) {
        this(aiServiceClass, inputGuardrails, outputGuardrails, method -> Optional.empty());
    }

    /**
     * @param methodKeyResolver finds the key a {@link Method} is registered under, when the method is not registered
     *                          as is. It is never called with {@code null}, and is called once per method.
     */
    AbstractGuardrailService(
            Class<?> aiServiceClass,
            Map<Object, InputGuardrailExecutor> inputGuardrails,
            Map<Object, OutputGuardrailExecutor> outputGuardrails,
            Function<Method, Optional<?>> methodKeyResolver) {
        this.aiServiceClass = ensureNotNull(aiServiceClass, "aiServiceClass");
        this.inputGuardrails =
                new KeyedGuardrailExecutors<>(inputGuardrails, InputGuardrailResult::success, methodKeyResolver);
        this.outputGuardrails =
                new KeyedGuardrailExecutors<>(outputGuardrails, OutputGuardrailResult::success, methodKeyResolver);
    }

    @Override
    public Class<?> aiServiceClass() {
        return this.aiServiceClass;
    }

    @Override
    public <MethodKey> InputGuardrailResult executeInputGuardrails(MethodKey method, InputGuardrailRequest request) {
        return this.inputGuardrails.execute(method, request);
    }

    @Override
    public <MethodKey> CompletableFuture<InputGuardrailResult> executeInputGuardrailsAsync(
            MethodKey method, InputGuardrailRequest request) {
        return this.inputGuardrails.executeAsync(method, request);
    }

    @Override
    public <MethodKey> OutputGuardrailResult executeOutputGuardrails(MethodKey method, OutputGuardrailRequest request) {
        return this.outputGuardrails.execute(method, request);
    }

    @Override
    public <MethodKey> CompletableFuture<OutputGuardrailResult> executeOutputGuardrailsAsync(
            MethodKey method, OutputGuardrailRequest request) {
        return this.outputGuardrails.executeAsync(method, request);
    }

    @Override
    public <MethodKey> boolean hasInputGuardrails(MethodKey method) {
        return this.inputGuardrails.hasGuardrails(method);
    }

    @Override
    public <MethodKey> boolean hasOutputGuardrails(MethodKey method) {
        return this.outputGuardrails.hasGuardrails(method);
    }

    // These methods below really only exist for testing purposes
    // That's why they are package-scoped
    int getInputGuardrailMethodCount() {
        return this.inputGuardrails.registeredCount();
    }

    int getOutputGuardrailMethodCount() {
        return this.outputGuardrails.registeredCount();
    }

    <MethodKey> Optional<dev.langchain4j.guardrail.config.InputGuardrailsConfig> getInputConfig(MethodKey method) {
        return this.inputGuardrails.executor(method).map(InputGuardrailExecutor::config);
    }

    <MethodKey> Optional<dev.langchain4j.guardrail.config.OutputGuardrailsConfig> getOutputConfig(MethodKey method) {
        return this.outputGuardrails.executor(method).map(OutputGuardrailExecutor::config);
    }

    <MethodKey> List<InputGuardrail> getInputGuardrails(MethodKey method) {
        return this.inputGuardrails
                .executor(method)
                .map(InputGuardrailExecutor::guardrails)
                .orElseGet(List::of);
    }

    <MethodKey> List<OutputGuardrail> getOutputGuardrails(MethodKey method) {
        return this.outputGuardrails
                .executor(method)
                .map(OutputGuardrailExecutor::guardrails)
                .orElseGet(List::of);
    }
}
