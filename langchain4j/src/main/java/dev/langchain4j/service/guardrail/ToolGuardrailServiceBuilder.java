package dev.langchain4j.service.guardrail;

import dev.langchain4j.classloading.ClassMetadataProvider;
import dev.langchain4j.guardrail.Guardrail;
import dev.langchain4j.guardrail.ToolInputGuardrail;
import dev.langchain4j.guardrail.ToolInputGuardrailRequest;
import dev.langchain4j.guardrail.ToolInputGuardrailResult;
import dev.langchain4j.guardrail.ToolOutputGuardrail;
import dev.langchain4j.guardrail.ToolOutputGuardrailRequest;
import dev.langchain4j.guardrail.ToolOutputGuardrailResult;
import dev.langchain4j.guardrail.config.ToolInputGuardrailsConfig;
import dev.langchain4j.guardrail.config.ToolOutputGuardrailsConfig;
import dev.langchain4j.service.guardrail.ToolGuardrailService.Builder;
import java.util.List;
import java.util.stream.Stream;

/**
 * The default {@link ToolGuardrailService.Builder}. The guardrails it is given apply to every tool, and run before the
 * guardrails of the tool itself (see {@link ToolGuardrailService}).
 */
final class ToolGuardrailServiceBuilder
        extends AbstractKeyedGuardrailServiceBuilder<
                ToolInputGuardrailRequest,
                ToolInputGuardrailResult,
                ToolOutputGuardrailRequest,
                ToolOutputGuardrailResult,
                ToolGuardrailService,
                ToolInputGuardrail,
                ToolOutputGuardrail,
                ToolInputGuardrailsConfig,
                ToolOutputGuardrailsConfig,
                Builder>
        implements Builder {

    ToolGuardrailServiceBuilder(Class<?> aiServiceClass) {
        super(aiServiceClass);
    }

    /**
     * Builds the service. The guardrail classes are instantiated here, once. Unlike {@link GuardrailServiceBuilder},
     * this does not resolve the chains of the tools: it creates the {@link ToolGuardrailChainResolver} that resolves
     * them when the tools are bound (see {@link DefaultToolGuardrailService}).
     */
    @Override
    public ToolGuardrailService build() {
        return new DefaultToolGuardrailService(
                targetClass(),
                new ToolGuardrailChainResolver(
                        globalGuardrails(inputSettings()),
                        globalGuardrails(outputSettings()),
                        inputSettings().config().orElse(null),
                        outputSettings().config().orElse(null),
                        ClassMetadataProvider.getClassMetadataProviderFactory()));
    }

    /**
     * The guardrails of the builder in the order they run: the instances, then the guardrails created from the
     * classes, like the AI service guardrails.
     */
    private static <G extends Guardrail<?, ?>> List<GuardrailEntry<G>> globalGuardrails(
            GuardrailBuilderSettings<G, ?> settings) {
        return Stream.concat(
                        settings.guardrailInstances().stream().map(GuardrailEntry::fromInstance),
                        settings.guardrailClasses().stream()
                                .map(guardrailClass -> GuardrailEntry.<G>fromClass(
                                        guardrailClass, GuardrailBuilderSettings.instantiate(guardrailClass))))
                .toList();
    }
}
