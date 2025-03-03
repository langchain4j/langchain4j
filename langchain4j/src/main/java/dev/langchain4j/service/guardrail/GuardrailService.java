package dev.langchain4j.service.guardrail;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.classloading.ClassInstanceLoader;
import dev.langchain4j.guardrail.Guardrail;
import dev.langchain4j.guardrail.GuardrailParams;
import dev.langchain4j.guardrail.GuardrailResult;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.config.InputGuardrailsConfig;
import dev.langchain4j.guardrail.config.InputGuardrailsConfigBuilder;
import dev.langchain4j.guardrail.config.OutputGuardrailsConfig;
import dev.langchain4j.guardrail.config.OutputGuardrailsConfigBuilder;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;

/**
 * GuardrailService is responsible for managing and applying input and output guardrails
 * to methods of a specified AI service class. Guardrails are defined through annotations
 * at either the class or method level and are used to enforce constraints or rules for
 * processing requests and responses.
 *
 * This class initializes guardrails for all methods of the specified AI service class,
 * allowing input and output validation, transformation, or restriction through the
 * specified guardrail implementations. The guardrails can be customized through configurations
 * specific to each guardrail type.
 */
public class GuardrailService {
    private final Class<?> aiServiceClass;
    private final ConcurrentMap<Method, GuardrailsHolder> guardrails = new ConcurrentHashMap<>(2);

    public GuardrailService(Class<?> aiServiceClass) {
        this.aiServiceClass = ensureNotNull(aiServiceClass, "aiServiceClass");

        for (var method : this.aiServiceClass.getMethods()) {
            this.guardrails.computeIfAbsent(method, this::computeGuardrailsForAiServiceMethod);
        }
    }

    /**
     * Computes the guardrails for a specific method of an AI service class.
     * This method retrieves and processes input and output guardrail annotations
     * at both the method and class levels. The guardrails defined on the method
     * override those defined at the class level when applicable.
     *
     * @param method the method for which the guardrails are computed
     * @return a {@code GuardrailsHolder} containing the computed input and output guardrails,
     * or {@code null} if no guardrails are defined
     */
    protected GuardrailsHolder computeGuardrailsForAiServiceMethod(Method method) {
        // For both input & output guardrails, first check the method
        // If nothing on the method, then fall back to the class
        var inputGuardrails = Optional.ofNullable(method.getAnnotation(InputGuardrails.class))
                .or(() -> Optional.ofNullable(this.aiServiceClass.getAnnotation(InputGuardrails.class)))
                .map(this::computeInputGuardrails)
                .orElse(null);

        var outputGuardrails = Optional.ofNullable(method.getAnnotation(OutputGuardrails.class))
                .or(() -> Optional.ofNullable(this.aiServiceClass.getAnnotation(OutputGuardrails.class)))
                .map(this::computeOutputGuardrails)
                .orElse(null);

        return new GuardrailsHolder(inputGuardrails, outputGuardrails);
    }

    protected <P extends GuardrailParams, R extends GuardrailResult<R>, G extends Guardrail<P, R>>
            G getGuardrailClassInstance(Class<G> guardrailClass) {
        ensureNotNull(guardrailClass, "guardrailClass");
        return ClassInstanceLoader.getClassInstance(guardrailClass);
    }

    private InputGuardrailHolder computeInputGuardrails(InputGuardrails inputGuardrails) {
        return new InputGuardrailHolder(computeConfig(inputGuardrails.config()), getGuardrails(inputGuardrails));
    }

    private InputGuardrailsConfig computeConfig(dev.langchain4j.service.guardrail.InputGuardrailsConfig config) {
        return InputGuardrailsConfigBuilder.newBuilder().build();
    }

    private <I extends InputGuardrail> List<I> getGuardrails(InputGuardrails inputGuardrails) {
        return Stream.of(inputGuardrails.value())
                .map(guardrailClass -> (I) getGuardrailClassInstance(guardrailClass))
                .toList();
    }

    private OutputGuardrailHolder computeOutputGuardrails(OutputGuardrails outputGuardrails) {
        return new OutputGuardrailHolder(computeConfig(outputGuardrails.config()), getGuardrails(outputGuardrails));
    }

    private OutputGuardrailsConfig computeConfig(dev.langchain4j.service.guardrail.OutputGuardrailsConfig config) {
        return OutputGuardrailsConfigBuilder.newBuilder()
                .maxRetries(config.maxRetries())
                .build();
    }

    private <O extends OutputGuardrail> List<O> getGuardrails(OutputGuardrails outputGuardrails) {
        return Stream.of(outputGuardrails.value())
                .map(guardrailClass -> (O) getGuardrailClassInstance(guardrailClass))
                .toList();
    }

    protected record GuardrailsHolder(InputGuardrailHolder inputGuardrails, OutputGuardrailHolder outputGuardrails) {}

    protected record InputGuardrailHolder(InputGuardrailsConfig config, List<InputGuardrail> guardrails) {
        protected InputGuardrailHolder {
            ensureNotNull(config, "config");
            ensureNotNull(guardrails, "guardrails");
        }
    }

    protected record OutputGuardrailHolder(OutputGuardrailsConfig config, List<OutputGuardrail> guardrails) {
        protected OutputGuardrailHolder {
            ensureNotNull(config, "config");
            ensureNotNull(guardrails, "guardrails");
        }
    }

    // These methods below really only exist for testing purposes
    // Thats why they are package-scoped
    Optional<InputGuardrailsConfig> getInputConfig(String methodName) {
        return findMethod(methodName)
                .map(this.guardrails::get)
                .map(GuardrailsHolder::inputGuardrails)
                .map(InputGuardrailHolder::config);
    }

    Optional<OutputGuardrailsConfig> getOutputConfig(String methodName) {
        return findMethod(methodName)
                .map(this.guardrails::get)
                .map(GuardrailsHolder::outputGuardrails)
                .map(OutputGuardrailHolder::config);
    }

    List<InputGuardrail> getInputGuardrails(String methodName) {
        return findMethod(methodName)
                .map(this.guardrails::get)
                .map(GuardrailsHolder::inputGuardrails)
                .map(InputGuardrailHolder::guardrails)
                .orElseGet(List::of);
    }

    List<OutputGuardrail> getOutputGuardrails(String methodName) {
        return findMethod(methodName)
                .map(this.guardrails::get)
                .map(GuardrailsHolder::outputGuardrails)
                .map(OutputGuardrailHolder::guardrails)
                .orElseGet(List::of);
    }

    private Optional<Method> findMethod(String methodName) {
        return Stream.of(this.aiServiceClass.getMethods())
                .filter(method -> methodName.equals(method.getName()))
                .findFirst();
    }
}
