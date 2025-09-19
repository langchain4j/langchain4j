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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Responsible for managing and applying input and output guardrails to methods
 * of a specified AI service class. Guardrails are defined through annotations at either the
 * class or method level and are used to enforce constraints, validation, or transformation
 * logic on inputs and outputs of AI service methods.
 *
 * This class handles the initialization, configuration, and execution of input and output
 * guardrail logic for the methods of the associated AI service class, ensuring both input
 * and output constraints are applied automatically when methods are invoked.
 */
@Internal
public abstract class AbstractGuardrailService implements GuardrailService {
    private static final Object NULL_KEY = new Object();

    private final Class<?> aiServiceClass;
    private final Map<Object, InputGuardrailExecutor> inputGuardrails = new ConcurrentHashMap<>();
    private final Map<Object, OutputGuardrailExecutor> outputGuardrails = new ConcurrentHashMap<>();

    // Caches for whether or not a method has input or output guardrails
    private final Map<Object, Boolean> inputGuardrailMethods = new ConcurrentHashMap<>();
    private final Map<Object, Boolean> outputGuardrailMethods = new ConcurrentHashMap<>();

    protected AbstractGuardrailService(
            Class<?> aiServiceClass,
            Map<Object, InputGuardrailExecutor> inputGuardrails,
            Map<Object, OutputGuardrailExecutor> outputGuardrails) {
        this.aiServiceClass = ensureNotNull(aiServiceClass, "aiServiceClass");
        Optional.ofNullable(inputGuardrails).ifPresent(this.inputGuardrails::putAll);
        Optional.ofNullable(outputGuardrails).ifPresent(this.outputGuardrails::putAll);
    }

    @Override
    public Class<?> aiServiceClass() {
        return this.aiServiceClass;
    }

    @Override
    public <MethodKey> InputGuardrailResult executeInputGuardrails(MethodKey method, InputGuardrailRequest request) {
        return Optional.ofNullable(method)
                .map(this.inputGuardrails::get)
                .map(executor -> executor.execute(request))
                .orElseGet(InputGuardrailResult::success);
    }

    @Override
    public <MethodKey> OutputGuardrailResult executeOutputGuardrails(MethodKey method, OutputGuardrailRequest request) {
        return Optional.ofNullable(method)
                .map(this.outputGuardrails::get)
                .map(executor -> executor.execute(request))
                .orElseGet(OutputGuardrailResult::success);
    }

    @Override
    public <MethodKey> boolean hasInputGuardrails(MethodKey method) {
        return this.inputGuardrailMethods.computeIfAbsent(
                checkMethodKey(method), m -> !getInputGuardrails(m).isEmpty());
    }

    @Override
    public <MethodKey> boolean hasOutputGuardrails(MethodKey method) {
        return this.outputGuardrailMethods.computeIfAbsent(
                checkMethodKey(method), m -> !getOutputGuardrails(m).isEmpty());
    }

    private static <MethodKey> MethodKey checkMethodKey(MethodKey method) {
        return (method != null) ? method : (MethodKey) NULL_KEY;
    }

    // These methods below really only exist for testing purposes
    // That's why they are package-scoped
    int getInputGuardrailMethodCount() {
        return this.inputGuardrails.size();
    }

    int getOutputGuardrailMethodCount() {
        return this.outputGuardrails.size();
    }

    <MethodKey> Optional<dev.langchain4j.guardrail.config.InputGuardrailsConfig> getInputConfig(MethodKey method) {
        return Optional.ofNullable(this.inputGuardrails.get(method)).map(InputGuardrailExecutor::config);
    }

    <MethodKey> Optional<dev.langchain4j.guardrail.config.OutputGuardrailsConfig> getOutputConfig(MethodKey method) {
        return Optional.ofNullable(this.outputGuardrails.get(method)).map(OutputGuardrailExecutor::config);
    }

    <MethodKey> List<InputGuardrail> getInputGuardrails(MethodKey method) {
        return Optional.ofNullable(method)
                .map(this.inputGuardrails::get)
                .map(InputGuardrailExecutor::guardrails)
                .orElseGet(List::of);
    }

    <MethodKey> List<OutputGuardrail> getOutputGuardrails(MethodKey method) {
        return Optional.ofNullable(method)
                .map(this.outputGuardrails::get)
                .map(OutputGuardrailExecutor::guardrails)
                .orElseGet(List::of);
    }
}
