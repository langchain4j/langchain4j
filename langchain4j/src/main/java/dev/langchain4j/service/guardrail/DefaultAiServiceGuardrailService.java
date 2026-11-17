package dev.langchain4j.service.guardrail;

import dev.langchain4j.guardrail.InputGuardrailExecutor;
import dev.langchain4j.guardrail.InputGuardrailRequest;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.guardrail.OutputGuardrailExecutor;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import java.util.Map;
import java.util.function.UnaryOperator;

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
 * <p>
 *     This is the {@link GuardrailService} for AI services, built on {@link AbstractKeyedGuardrailService}. It
 *     does not extend the deprecated {@link AbstractGuardrailService}, which only remains for the extension frameworks
 *     that already subclass it.
 * </p>
 */
final class DefaultAiServiceGuardrailService
        extends AbstractKeyedGuardrailService<
                InputGuardrailRequest,
                InputGuardrailResult,
                OutputGuardrailRequest,
                OutputGuardrailResult,
                InputGuardrailExecutor,
                OutputGuardrailExecutor>
        implements GuardrailService {

    DefaultAiServiceGuardrailService(
            Class<?> aiServiceClass,
            Map<Object, InputGuardrailExecutor> inputGuardrails,
            Map<Object, OutputGuardrailExecutor> outputGuardrails,
            UnaryOperator<Object> methodKeyResolver) {
        super(
                aiServiceClass,
                inputGuardrails,
                outputGuardrails,
                InputGuardrailResult::success,
                OutputGuardrailResult::success,
                methodKeyResolver);
    }

    @Override
    @SuppressWarnings("deprecation")
    public Class<?> aiServiceClass() {
        return targetClass();
    }
}
