package dev.langchain4j.service.guardrail;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.classloading.ClassMetadataProvider;
import dev.langchain4j.guardrail.Guardrail;
import dev.langchain4j.guardrail.ToolInputGuardrail;
import dev.langchain4j.guardrail.ToolOutputGuardrail;
import dev.langchain4j.guardrail.config.ToolInputGuardrailsConfig;
import dev.langchain4j.guardrail.config.ToolOutputGuardrailsConfig;
import dev.langchain4j.service.guardrail.ToolGuardrailService.Builder;
import java.util.List;
import java.util.stream.Stream;

/**
 * The default {@link ToolGuardrailService.Builder}. The guardrails it is given apply to every tool, and run before the
 * guardrails of the tool itself (see {@link ToolGuardrailService}).
 */
final class ToolGuardrailServiceBuilder implements Builder {

    private final Class<?> aiServiceClass;
    private final GuardrailBuilderSettings<ToolInputGuardrail, ToolInputGuardrailsConfig> input =
            new GuardrailBuilderSettings<>();
    private final GuardrailBuilderSettings<ToolOutputGuardrail, ToolOutputGuardrailsConfig> output =
            new GuardrailBuilderSettings<>();

    ToolGuardrailServiceBuilder(Class<?> aiServiceClass) {
        this.aiServiceClass = ensureNotNull(aiServiceClass, "aiServiceClass");
    }

    @Override
    public Builder inputGuardrailsConfig(ToolInputGuardrailsConfig config) {
        this.input.config(config);
        return this;
    }

    @Override
    public Builder outputGuardrailsConfig(ToolOutputGuardrailsConfig config) {
        this.output.config(config);
        return this;
    }

    @Override
    public <I extends ToolInputGuardrail> Builder inputGuardrailClasses(List<Class<? extends I>> guardrailClasses) {
        this.input.guardrailClasses(guardrailClasses);
        return this;
    }

    @Override
    public <O extends ToolOutputGuardrail> Builder outputGuardrailClasses(List<Class<? extends O>> guardrailClasses) {
        this.output.guardrailClasses(guardrailClasses);
        return this;
    }

    @Override
    public <I extends ToolInputGuardrail> Builder inputGuardrails(List<I> guardrails) {
        this.input.guardrails(guardrails);
        return this;
    }

    @Override
    public <O extends ToolOutputGuardrail> Builder outputGuardrails(List<O> guardrails) {
        this.output.guardrails(guardrails);
        return this;
    }

    /**
     * Builds the service. The guardrail classes are instantiated here, once. Unlike {@link GuardrailServiceBuilder},
     * this does not resolve the chains of the tools: it creates the {@link ToolGuardrailChainResolver} that resolves
     * them when the tools are bound (see {@link DefaultToolGuardrailService}).
     */
    @Override
    public ToolGuardrailService build() {
        return new DefaultToolGuardrailService(
                this.aiServiceClass,
                new ToolGuardrailChainResolver(
                        globalGuardrails(this.input),
                        globalGuardrails(this.output),
                        this.input.config().orElse(null),
                        this.output.config().orElse(null),
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
