package dev.langchain4j.service.guardrail;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailRequest;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

/**
 * Defines a service for executing guardrails associated with methods in an AI service.
 * Guardrails are constraints or validations applied either to input or output of a method.
 */
public interface GuardrailService {
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
     * @param <MethodKey>> The type of the method key, representing a unique identifier for methods.
     */
    <MethodKey> InputGuardrailResult executeInputGuardrails(MethodKey method, InputGuardrailRequest params);

    /**
     * Executes the input guardrails associated with the given method and parameters,
     * and retrieves a modified or validated {@link UserMessage} based on the result.
     *
     * @param <MethodKey> The type of the method key, representing a unique identifier for methods.
     * @param method The method whose input guardrails are to be executed. Nullable.
     * @param params The parameters to validate against the input guardrails. Must not be null.
     * @return A {@link UserMessage} derived from the provided parameters and the result
     *         of the input guardrails execution. If guardrails are applied successfully,
     *         a potentially rewritten user message is returned. If no guardrails are
     *         associated with the method, the original user message is returned.
     */
    default <MethodKey> UserMessage executeGuardrails(MethodKey method, InputGuardrailRequest params) {
        return executeInputGuardrails(method, params).userMessage(params);
    }

    /**
     * Executes the output guardrails associated with a given {@code Method}.
     *
     * @param method The method whose output guardrails are to be executed.
     * @param params The parameters to validate against the output guardrails. Must not be null.
     * @return The result of executing the output guardrails, encapsulated in an {@code OutputGuardrailResult}.
     * If no guardrails are associated with the method, a successful result is returned by default.
     * @param <MethodKey>> The type of the method key, representing a unique identifier for methods.
     */
    <MethodKey> OutputGuardrailResult executeOutputGuardrails(MethodKey method, OutputGuardrailRequest params);

    /**
     * Whether or not a method has any input guardrails associated with it
     * @param method The method
     * @return {@code true} If {@code method} has input guardrails. {@code false} otherwise
     * @param <MethodKey>> The type of the method key, representing a unique identifier for methods.
     */
    <MethodKey> boolean hasInputGuardrails(MethodKey method);

    /**
     * Whether or not a method has any output guardrails associated with it
     * @param method The method
     * @return {@code true} If {@code method} has output guardrails. {@code false} otherwise
     * @param <MethodKey>> The type of the method key, representing a unique identifier for methods.
     */
    <MethodKey> boolean hasOutputGuardrails(MethodKey method);

    /**
     * Executes the guardrails associated with a given method and parameters, returning the appropriate response.
     *
     * @param <MethodKey> The type of the method key, representing a unique identifier for methods.
     * @param <T> The type of response to produce
     * @param method The method whose output guardrails are to be executed. Nullable.
     * @param params The parameters to validate against the output guardrails. Must not be null.
     * @return A {@link ChatResponse} that encapsulates the output of executing the guardrails based on the provided parameters.
     */
    default <MethodKey, T> T executeGuardrails(MethodKey method, OutputGuardrailRequest params) {
        return executeOutputGuardrails(method, params).response(params);
    }

    /**
     * Creates a new instance of {@link Builder} for the specified AI service class.
     *
     * @param aiServiceClass The {@code Class} object representing the AI service for which the builder is being created.
     * @return A {@link Builder} instance initialized with the specified AI service class.
     */
    static Builder builder(Class<?> aiServiceClass) {
        return new GuardrailServiceBuilder(aiServiceClass);
    }

    interface Builder {
        /**
         * Configures the input guardrails for the builder.
         *
         * @param config The configuration for input guardrails. Must not be null.
         * @return The current instance of {@link Builder} for method chaining.
         * @throws IllegalArgumentException if {@code config} is null.
         */
        Builder inputGuardrailsConfig(dev.langchain4j.guardrail.config.InputGuardrailsConfig config);

        /**
         * Configures the output guardrails for the Builder.
         *
         * @param config The configuration for output guardrails. Must not be null.
         * @return The current instance of {@link Builder} for method chaining.
         * @throws IllegalArgumentException if {@code config} is null.
         */
        Builder outputGuardrailsConfig(dev.langchain4j.guardrail.config.OutputGuardrailsConfig config);

        /**
         * Configures the classes of input guardrails for the Builder. Existing input guardrail classes will be cleared.
         *
         * @param guardrailClasses A list of classes implementing the {@link InputGuardrail} interface to be used
         *                         as input guardrails. May be {@code null}.
         * @param <I> The type of {@link InputGuardrail}
         * @return The current instance of {@link Builder} for method chaining.
         */
        <I extends InputGuardrail> Builder inputGuardrailClasses(List<Class<? extends I>> guardrailClasses);

        /**
         * Configures the classes of input guardrails for the Builder.
         * Existing input guardrail classes will be cleared.
         *
         * @param guardrailClasses An array of classes implementing the {@link InputGuardrail} interface to be used
         *                         as input guardrails. May be {@code null}.
         * @param <I> The type of {@link InputGuardrail}
         * @return The current instance of {@link Builder} for method chaining.
         */
        default <I extends InputGuardrail> Builder inputGuardrailClasses(Class<? extends I>... guardrailClasses) {
            return Optional.ofNullable(guardrailClasses)
                    .map(g -> inputGuardrailClasses(List.of(g)))
                    .orElse(this);
        }

        /**
         * Configures the classes of output guardrails for the Builder.
         * Existing output guardrail classes will be cleared.
         *
         * @param guardrailClasses A list of classes implementing the {@link OutputGuardrail} interface to be used
         *                         as output guardrails. May be {@code null}.
         * @param <O> The type of {@link OutputGuardrail}
         * @return The current instance of {@link Builder} for method chaining.
         */
        <O extends OutputGuardrail> Builder outputGuardrailClasses(List<Class<? extends O>> guardrailClasses);

        /**
         * Configures the classes of output guardrails for the Builder.
         * Existing output guardrail classes will be cleared.
         *
         * @param guardrailClasses An array of classes implementing the {@link OutputGuardrail} interface to be used
         *                         as output guardrails. May be {@code null}.
         * @param <O> The type of {@link OutputGuardrail}
         * @return The current instance of {@link Builder} for method chaining.
         */
        default <O extends OutputGuardrail> Builder outputGuardrailClasses(Class<? extends O>... guardrailClasses) {
            return Optional.ofNullable(guardrailClasses)
                    .map(g -> outputGuardrailClasses(List.of(g)))
                    .orElse(this);
        }

        /**
         * Sets the input guardrails for the Builder. Existing input guardrails
         * will be cleared, and the provided input guardrails will be added.
         *
         * @param guardrails A list of input guardrails implementing the {@link InputGuardrail} interface.
         *                   Can be {@code null}, in which case no guardrails will be added.
         * @return The current instance of {@link Builder} for method chaining.
         */
        <I extends InputGuardrail> Builder inputGuardrails(List<I> guardrails);

        /**
         * Configures the input guardrails for the Builder.
         *
         * @param guardrails An array of input guardrails implementing the {@link InputGuardrail} interface.
         *                   May be {@code null}, in which case no guardrails will be added.
         * @return The current instance of {@link Builder} for method chaining.
         */
        default <I extends InputGuardrail> Builder inputGuardrails(I... guardrails) {
            return Optional.ofNullable(guardrails)
                    .map(ig -> inputGuardrails(List.of(ig)))
                    .orElse(this);
        }

        /**
         * Sets the output guardrails for the Builder. Existing output guardrails
         * will be cleared, and the provided output guardrails will be added.
         *
         * @param guardrails A list of output guardrails implementing the {@link OutputGuardrail}
         *                   interface. Can be {@code null}, in which case no guardrails will be added.
         * @return The current instance of {@link Builder} for method chaining.
         */
        <O extends OutputGuardrail> Builder outputGuardrails(List<O> guardrails);

        /**
         * Configures the output guardrails for the Builder.
         *
         * @param guardrails An array of output guardrails implementing the {@link OutputGuardrail} interface.
         *                   May be {@code null}, in which case no guardrails will be added.
         * @return The current instance of {@link Builder} for method chaining.
         */
        default <O extends OutputGuardrail> Builder outputGuardrails(O... guardrails) {
            return Optional.ofNullable(guardrails)
                    .map(og -> outputGuardrails(List.of(og)))
                    .orElse(this);
        }

        /**
         * Builds and returns an instance of {@link GuardrailService}.
         * This method configures input and output guardrails at the service level
         * using the provided class-level or method-level annotations. If no
         * method-level annotations are present, it defers to class-level annotations,
         * and if those are absent, it uses the settings defined in the builder.
         *
         * @return an instance of {@link GuardrailService} configured with appropriate
         * input and output guardrails.
         */
        GuardrailService build();
    }
}
