package dev.langchain4j.service.guardrail;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailExecutor;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailExecutor;
import dev.langchain4j.guardrail.config.InputGuardrailsConfig;
import dev.langchain4j.guardrail.config.OutputGuardrailsConfig;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Looks inside a {@link GuardrailService} created by the builder: which executors, guardrails and configs it holds
 * for a method, found by the name of the method.
 */
final class GuardrailServiceInspector {
    private final DefaultAiServiceGuardrailService service;

    private GuardrailServiceInspector(DefaultAiServiceGuardrailService service) {
        this.service = service;
    }

    static GuardrailServiceInspector inspect(GuardrailService guardrailService) {
        assertThat(guardrailService).isInstanceOf(DefaultAiServiceGuardrailService.class);
        return new GuardrailServiceInspector((DefaultAiServiceGuardrailService) guardrailService);
    }

    /**
     * The number of methods of the AI service that have input guardrails: the builder registers an executor only for
     * the methods that have guardrails.
     */
    int getInputGuardrailMethodCount() {
        return countMethodsWith(method -> service.inputExecutor(method).isPresent());
    }

    /**
     * The number of methods of the AI service that have output guardrails: the builder registers an executor only for
     * the methods that have guardrails.
     */
    int getOutputGuardrailMethodCount() {
        return countMethodsWith(method -> service.outputExecutor(method).isPresent());
    }

    private int countMethodsWith(Predicate<Method> hasExecutor) {
        return (int) Stream.of(service.targetClass().getMethods())
                .filter(method -> !Modifier.isStatic(method.getModifiers()))
                .filter(hasExecutor)
                .count();
    }

    Optional<InputGuardrailsConfig> getInputConfig(String methodName) {
        return findMethod(methodName).flatMap(service::inputExecutor).map(InputGuardrailExecutor::config);
    }

    Optional<OutputGuardrailsConfig> getOutputConfig(String methodName) {
        return findMethod(methodName).flatMap(service::outputExecutor).map(OutputGuardrailExecutor::config);
    }

    List<InputGuardrail> getInputGuardrails(String methodName) {
        return findMethod(methodName)
                .flatMap(service::inputExecutor)
                .map(InputGuardrailExecutor::guardrails)
                .orElseGet(List::of);
    }

    List<OutputGuardrail> getOutputGuardrails(String methodName) {
        return findMethod(methodName)
                .flatMap(service::outputExecutor)
                .map(OutputGuardrailExecutor::guardrails)
                .orElseGet(List::of);
    }

    private Optional<Method> findMethod(String methodName) {
        return Stream.of(service.targetClass().getMethods())
                .filter(method -> methodName.equals(method.getName()))
                .findFirst();
    }
}
