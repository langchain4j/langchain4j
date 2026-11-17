package dev.langchain4j.service.guardrail;

import dev.langchain4j.classloading.ClassMetadataProvider;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailExecutor;
import dev.langchain4j.guardrail.InputGuardrailRequest;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailExecutor;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailResult;
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
final class GuardrailServiceBuilder
        extends AbstractKeyedGuardrailServiceBuilder<
                InputGuardrailRequest,
                InputGuardrailResult,
                OutputGuardrailRequest,
                OutputGuardrailResult,
                GuardrailService,
                InputGuardrail,
                OutputGuardrail,
                InputGuardrailsConfig,
                OutputGuardrailsConfig,
                InputGuardrailExecutor,
                OutputGuardrailExecutor,
                Builder>
        implements Builder {
    GuardrailServiceBuilder(Class<?> aiServiceClass) {
        super(aiServiceClass);
    }

    @Override
    protected InputGuardrailExecutor newInputExecutor(InputGuardrailsConfig config, List<InputGuardrail> guardrails) {
        return InputGuardrailExecutor.builder()
                .config(config)
                .guardrails(guardrails)
                .build();
    }

    @Override
    protected OutputGuardrailExecutor newOutputExecutor(
            OutputGuardrailsConfig config, List<OutputGuardrail> guardrails) {
        return OutputGuardrailExecutor.builder()
                .config(config)
                .guardrails(guardrails)
                .build();
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
    public DefaultAiServiceGuardrailService build() {
        // Anything set here in this builder is relevant at the AiService level, NOT at the method level
        // Setting guardrails at the method level can only be done via the annotations

        var inputGuardrailsByMethod = new HashMap<Object, InputGuardrailExecutor>();
        var outputGuardrailsByMethod = new HashMap<Object, OutputGuardrailExecutor>();
        var factory = ClassMetadataProvider.getClassMetadataProviderFactory();

        factory.getNonStaticMethodsOnClass(targetClass()).forEach(method -> {
            var inputGuardrailsForMethod = computeInputGuardrailsForAiServiceMethod(method, factory);
            var outputGuardrailsForMethod = computeOutputGuardrailsForAiServiceMethod(method, factory);

            if (!inputGuardrailsForMethod.guardrails().isEmpty()) {
                inputGuardrailsByMethod.put(method, inputGuardrailsForMethod);
            }

            if (!outputGuardrailsForMethod.guardrails().isEmpty()) {
                outputGuardrailsByMethod.put(method, outputGuardrailsForMethod);
            }
        });

        return new DefaultAiServiceGuardrailService(
                targetClass(), inputGuardrailsByMethod, outputGuardrailsByMethod, factory::resolveMethodKey);
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
                inputSettings().config().orElseGet(() -> computeConfig(annotation)),
                inputSettings().hasGuardrails()
                        ? inputSettings().resolveGuardrails()
                        : GuardrailBuilderSettings.<InputGuardrail>instantiateAll(annotation.value()));
    }

    private OutputGuardrailExecutor computeOutputGuardrails(OutputGuardrails annotation) {
        return newOutputExecutor(
                outputSettings().config().orElseGet(() -> computeConfig(annotation)),
                outputSettings().hasGuardrails()
                        ? outputSettings().resolveGuardrails()
                        : GuardrailBuilderSettings.<OutputGuardrail>instantiateAll(annotation.value()));
    }

    private <MethodKey> InputGuardrailExecutor computeInputGuardrailsForAiServiceMethod(
            MethodKey method, ClassMetadataProviderFactory<MethodKey> factory) {
        // For both input & output guardrails, first check the builder
        // If nothing on the builder, then check the method
        // If nothing on the method, then fall back to the class
        if (inputSettings().hasGuardrailsAndConfig()) {
            // Don't need to introspect the annotation at all since everything is set on the builder
            return defaultInputExecutor();
        }

        // If we get here we know we need to introspect the annotation for one reason or another
        return factory.getAnnotation(method, InputGuardrails.class)
                .map(this::computeInputGuardrails)
                // Didn't exist at the method level, so check the class level
                .orElseGet(() -> factory.getAnnotation(targetClass(), InputGuardrails.class)
                        .map(this::computeInputGuardrails)
                        .orElseGet(this::defaultInputExecutor));
    }

    private <MethodKey> OutputGuardrailExecutor computeOutputGuardrailsForAiServiceMethod(
            MethodKey method, ClassMetadataProviderFactory<MethodKey> factory) {
        // For both input & output guardrails, first check the builder
        // If nothing on the builder, then check the method
        // If nothing on the method, then fall back to the class
        if (outputSettings().hasGuardrailsAndConfig()) {
            return defaultOutputExecutor();
        }

        // If we get here we know we need to introspect the annotation for one reason or another
        return factory.getAnnotation(method, OutputGuardrails.class)
                .map(this::computeOutputGuardrails)
                // Didn't exist at the method level, so check the class level
                .orElseGet(() -> factory.getAnnotation(targetClass(), OutputGuardrails.class)
                        .map(this::computeOutputGuardrails)
                        .orElseGet(this::defaultOutputExecutor));
    }
}
