package dev.langchain4j.service.guardrail;

import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailExecutor;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailExecutor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Responsible for managing and applying input and output guardrails
 * to methods of a specified AI service class. Guardrails are defined through annotations
 * at either the class or method level and are used to enforce constraints or rules for
 * processing requests and responses.
 *
 * This class initializes guardrails for all methods of the specified AI service class,
 * allowing input and output validation, transformation, or restriction through the
 * specified guardrail implementations. The guardrails can be customized through configurations
 * specific to each guardrail type.
 * <p>
 *     Obtain instances via {@link GuardrailService#builder(Class)}
 * </p>
 */
final class DefaultGuardrailService extends AbstractGuardrailService {
    DefaultGuardrailService(
            Class<?> aiServiceClass,
            Map<Object, InputGuardrailExecutor> inputGuardrails,
            Map<Object, OutputGuardrailExecutor> outputGuardrails) {
        super(aiServiceClass, inputGuardrails, outputGuardrails);
    }

    // These methods below really only exist for testing purposes
    // Thats why they are package-scoped
    Optional<dev.langchain4j.guardrail.config.InputGuardrailsConfig> getInputConfig(String methodName) {
        return findMethod(methodName).flatMap(super::getInputConfig);
    }

    Optional<dev.langchain4j.guardrail.config.OutputGuardrailsConfig> getOutputConfig(String methodName) {
        return findMethod(methodName).flatMap(super::getOutputConfig);
    }

    List<InputGuardrail> getInputGuardrails(String methodName) {
        return findMethod(methodName).map(super::getInputGuardrails).orElseGet(List::of);
    }

    List<OutputGuardrail> getOutputGuardrails(String methodName) {
        return findMethod(methodName).map(super::getOutputGuardrails).orElseGet(List::of);
    }

    private Optional<Method> findMethod(String methodName) {
        return Stream.of(aiServiceClass().getMethods())
                .filter(method -> methodName.equals(method.getName()))
                .findFirst();
    }
}
