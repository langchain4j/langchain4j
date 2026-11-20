package dev.langchain4j.service.guardrail;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.classloading.ClassMetadataProvider;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailExecutor;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailExecutor;
import dev.langchain4j.guardrail.config.InputGuardrailsConfig;
import dev.langchain4j.guardrail.config.OutputGuardrailsConfig;
import dev.langchain4j.service.guardrail.GuardrailService.Builder;
import dev.langchain4j.spi.classloading.ClassMetadataProviderFactory;
import java.util.HashMap;
import java.util.List;

/**
 * A builder class for creating and configuring a {@link GuardrailService} instance, which provides input and output guardrail
 * mechanisms for an AI service class. This class allows customization of the guardrails through configuration objects,
 * guardrail classes, or direct instances of guardrails.
 * <p>
 * This builder supports setting guardrails via both annotations and explicit configurations in code. The order of
 * precedence is described in {@link Builder#build()}.
 */
final class GuardrailServiceBuilder implements Builder {
    private final Class<?> aiServiceClass;
    private final GuardrailBuilderSettings<InputGuardrail, InputGuardrailsConfig> input =
            new GuardrailBuilderSettings<>();
    private final GuardrailBuilderSettings<OutputGuardrail, OutputGuardrailsConfig> output =
            new GuardrailBuilderSettings<>();

    GuardrailServiceBuilder(Class<?> aiServiceClass) {
        this.aiServiceClass = ensureNotNull(aiServiceClass, "aiServiceClass");
    }

    /**
     * Configures the input guardrails for the Builder.
     *
     * @param config The configuration for input guardrails. Must not be null.
     * @return The current instance of {@link Builder} for method chaining.
     * @throws IllegalArgumentException if {@code config} is null.
     */
    @Override
    public Builder inputGuardrailsConfig(InputGuardrailsConfig config) {
        this.input.config(config);
        return this;
    }

    /**
     * Configures the output guardrails for the Builder.
     *
     * @param config The configuration for output guardrails. Must not be null.
     * @return The current instance of {@link Builder} for method chaining.
     * @throws IllegalArgumentException if {@code config} is null.
     */
    @Override
    public Builder outputGuardrailsConfig(OutputGuardrailsConfig config) {
        this.output.config(config);
        return this;
    }

    /**
     * Configures the classes of input guardrails for the Builder. Existing input guardrail classes will be cleared.
     *
     * @param guardrailClasses A list of classes implementing the {@link InputGuardrail} interface to be used
     *                         as input guardrails. May be {@code null}.
     * @param <I> The type of {@link InputGuardrail}
     * @return The current instance of {@link Builder} for method chaining.
     */
    @Override
    public <I extends InputGuardrail> Builder inputGuardrailClasses(List<Class<? extends I>> guardrailClasses) {
        this.input.guardrailClasses(guardrailClasses);
        return this;
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
    @Override
    public <O extends OutputGuardrail> Builder outputGuardrailClasses(List<Class<? extends O>> guardrailClasses) {
        this.output.guardrailClasses(guardrailClasses);
        return this;
    }

    /**
     * Sets the input guardrails for the Builder. Existing input guardrails
     * will be cleared, and the provided input guardrails will be added.
     *
     * @param guardrails A list of input guardrails implementing the {@link InputGuardrail} interface.
     *                   Can be {@code null}, in which case no guardrails will be added.
     * @return The current instance of {@link Builder} for method chaining.
     */
    @Override
    public <I extends InputGuardrail> Builder inputGuardrails(List<I> guardrails) {
        this.input.guardrails(guardrails);
        return this;
    }

    /**
     * Sets the output guardrails for the Builder. Existing output guardrails
     * will be cleared, and the provided output guardrails will be added.
     *
     * @param guardrails A list of output guardrails implementing the {@link OutputGuardrail}
     *                   interface. Can be {@code null}, in which case no guardrails will be added.
     * @return The current instance of {@link Builder} for method chaining.
     */
    @Override
    public <O extends OutputGuardrail> Builder outputGuardrails(List<O> guardrails) {
        this.output.guardrails(guardrails);
        return this;
    }

    /**
     * Creates an executor for input guardrails; a {@code null} configuration means the default.
     */
    private static InputGuardrailExecutor newInputExecutor(
            InputGuardrailsConfig config, List<InputGuardrail> guardrails) {
        return InputGuardrailExecutor.builder()
                .config(config)
                .guardrails(guardrails)
                .build();
    }

    /**
     * Creates an executor for output guardrails; a {@code null} configuration means the default.
     */
    private static OutputGuardrailExecutor newOutputExecutor(
            OutputGuardrailsConfig config, List<OutputGuardrail> guardrails) {
        return OutputGuardrailExecutor.builder()
                .config(config)
                .guardrails(guardrails)
                .build();
    }

    /**
     * The executor for the input guardrails the builder was given, with the configuration it was given. It runs no
     * guardrail if the builder was given none.
     */
    private InputGuardrailExecutor defaultInputExecutor() {
        return newInputExecutor(
                this.input.config().orElse(null), this.input.resolveGuardrails());
    }

    /**
     * The executor for the output guardrails the builder was given, with the configuration it was given. It runs no
     * guardrail if the builder was given none.
     */
    private OutputGuardrailExecutor defaultOutputExecutor() {
        return newOutputExecutor(
                this.output.config().orElse(null), this.output.resolveGuardrails());
    }

    /**
     * Builds and returns an instance of {@link GuardrailService}.
     * This method configures input and output guardrails using the settings defined in the builder.
     * If not set it then uses the provided class-level or method-level annotations. If no
     * method-level annotations are present, it defers to class-level annotations.
     *
     * @return an instance of {@link GuardrailService} configured with appropriate
     * input and output guardrails.
     */
    @Override
    public DefaultGuardrailService build() {
        // Anything set here in this builder is relevant at the AiService level, NOT at the method level
        // Setting guardrails at the method level can only be done via the annotations

        var inputGuardrailsByMethod = new HashMap<Object, InputGuardrailExecutor>();
        var outputGuardrailsByMethod = new HashMap<Object, OutputGuardrailExecutor>();
        var factory = ClassMetadataProvider.getClassMetadataProviderFactory();

        factory.getNonStaticMethodsOnClass(this.aiServiceClass).forEach(method -> {
            var inputGuardrailsForMethod = computeInputGuardrailsForAiServiceMethod(method, factory);
            var outputGuardrailsForMethod = computeOutputGuardrailsForAiServiceMethod(method, factory);

            if (!inputGuardrailsForMethod.guardrails().isEmpty()) {
                inputGuardrailsByMethod.put(method, inputGuardrailsForMethod);
            }

            if (!outputGuardrailsForMethod.guardrails().isEmpty()) {
                outputGuardrailsByMethod.put(method, outputGuardrailsForMethod);
            }
        });

        return new DefaultGuardrailService(this.aiServiceClass, inputGuardrailsByMethod, outputGuardrailsByMethod);
    }

    private static InputGuardrailsConfig computeConfig(InputGuardrails annotation) {
        return InputGuardrailsConfig.builder().build();
    }

    private static OutputGuardrailsConfig computeConfig(OutputGuardrails annotation) {
        return OutputGuardrailsConfig.builder()
                .maxRetries(annotation.maxRetries())
                .build();
    }

    private InputGuardrailExecutor computeInputGuardrails(InputGuardrails annotation) {
        return newInputExecutor(
                this.input.config().orElseGet(() -> computeConfig(annotation)),
                this.input.hasGuardrails()
                        ? this.input.resolveGuardrails()
                        : GuardrailBuilderSettings.<InputGuardrail>instantiateAll(annotation.value()));
    }

    private OutputGuardrailExecutor computeOutputGuardrails(OutputGuardrails annotation) {
        return newOutputExecutor(
                this.output.config().orElseGet(() -> computeConfig(annotation)),
                this.output.hasGuardrails()
                        ? this.output.resolveGuardrails()
                        : GuardrailBuilderSettings.<OutputGuardrail>instantiateAll(annotation.value()));
    }

    private <MethodKey> InputGuardrailExecutor computeInputGuardrailsForAiServiceMethod(
            MethodKey method, ClassMetadataProviderFactory<MethodKey> factory) {
        // For both input & output guardrails, first check the builder
        // If nothing on the builder, then check the method
        // If nothing on the method, then fall back to the class
        if (this.input.hasGuardrailsAndConfig()) {
            // Don't need to introspect the annotation at all since everything is set on the builder
            return defaultInputExecutor();
        }

        // If we get here we know we need to introspect the annotation for one reason or another
        return factory.getAnnotation(method, InputGuardrails.class)
                .map(this::computeInputGuardrails)
                // Didn't exist at the method level, so check the class level
                .orElseGet(() -> factory.getAnnotation(this.aiServiceClass, InputGuardrails.class)
                        .map(this::computeInputGuardrails)
                        .orElseGet(this::defaultInputExecutor));
    }

    private <MethodKey> OutputGuardrailExecutor computeOutputGuardrailsForAiServiceMethod(
            MethodKey method, ClassMetadataProviderFactory<MethodKey> factory) {
        // For both input & output guardrails, first check the builder
        // If nothing on the builder, then check the method
        // If nothing on the method, then fall back to the class
        if (this.output.hasGuardrailsAndConfig()) {
            return defaultOutputExecutor();
        }

        // If we get here we know we need to introspect the annotation for one reason or another
        return factory.getAnnotation(method, OutputGuardrails.class)
                .map(this::computeOutputGuardrails)
                // Didn't exist at the method level, so check the class level
                .orElseGet(() -> factory.getAnnotation(this.aiServiceClass, OutputGuardrails.class)
                        .map(this::computeOutputGuardrails)
                        .orElseGet(this::defaultOutputExecutor));
    }
}
