package dev.langchain4j.service.guardrail;

import dev.langchain4j.guardrail.InputGuardrailParams;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.guardrail.OutputGuardrailParams;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import java.lang.reflect.Method;
import org.jspecify.annotations.Nullable;

/**
 * Defines a service for executing guardrails associated with methods in an AI service.
 * Guardrails are constraints or validations applied either to input or output of a method.
 *
 * @param <MK> The type of the method key, representing a unique identifier for methods.
 */
public interface GuardrailService<MK> {
    /**
     * Retrieves the class representing the AI service to which the guardrails apply.
     *
     * @return The {@code Class} object representing the AI service.
     */
    Class<?> aiServiceClass();

    /**
     * Executes the input guardrails associated with a given {@link Method}
     *
     * @param method The method whose input guardrails are to be executed.
     * @param params The parameters to validate against the input guardrails. Must not be null.
     * @return The result of executing the input guardrails, encapsulated in an {@code InputGuardrailResult}.
     * If no guardrails are associated with the method, a successful result is returned by default.
     */
    InputGuardrailResult executeGuardrails(@Nullable MK method, InputGuardrailParams params);

    /**
     * Executes the output guardrails associated with a given {@code Method}.
     *
     * @param method The method whose output guardrails are to be executed.
     * @param params The parameters to validate against the output guardrails. Must not be null.
     * @return The result of executing the output guardrails, encapsulated in an {@code OutputGuardrailResult}.
     * If no guardrails are associated with the method, a successful result is returned by default.
     */
    OutputGuardrailResult executeGuardrails(@Nullable MK method, OutputGuardrailParams params);
}
