package dev.langchain4j.service.guardrail;

import dev.langchain4j.Internal;
import dev.langchain4j.guardrail.InputGuardrailExecutor;
import dev.langchain4j.guardrail.InputGuardrailRequest;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.guardrail.OutputGuardrailExecutor;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import java.util.Map;

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
 * @deprecated Do not extend this class. To customize how guardrails are found and created, provide a
 * {@link dev.langchain4j.spi.classloading.ClassMetadataProviderFactory} (it also decides how the method passed at
 * invocation time maps to the method keys, see
 * {@link dev.langchain4j.spi.classloading.ClassMetadataProviderFactory#resolveMethodKey(Object)}) and a
 * {@link dev.langchain4j.spi.classloading.ClassInstanceFactory}, and use {@link GuardrailService#builder(Class)}.
 * Frameworks that need to replace the whole builder can still provide a
 * {@link dev.langchain4j.service.guardrail.spi.GuardrailServiceBuilderFactory}.
 */
@Internal
@Deprecated(since = "1.24.0")
public abstract non-sealed class AbstractGuardrailService
        extends AbstractKeyedGuardrailService<
                InputGuardrailRequest,
                InputGuardrailResult,
                OutputGuardrailRequest,
                OutputGuardrailResult,
                InputGuardrailExecutor,
                OutputGuardrailExecutor>
        implements GuardrailService {

    /**
     * Creates a service for the given AI service class, with the executors registered for each method key.
     *
     * @param aiServiceClass   The AI service class the guardrails apply to.
     * @param inputGuardrails  The input executor of each method key.
     * @param outputGuardrails The output executor of each method key.
     */
    protected AbstractGuardrailService(
            Class<?> aiServiceClass,
            Map<Object, InputGuardrailExecutor> inputGuardrails,
            Map<Object, OutputGuardrailExecutor> outputGuardrails) {
        super(
                aiServiceClass,
                inputGuardrails,
                outputGuardrails,
                InputGuardrailResult::success,
                OutputGuardrailResult::success);
    }

    @Override
    public Class<?> aiServiceClass() {
        return targetClass();
    }
}
