package dev.langchain4j.service.guardrail;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.guardrail.Guardrail;
import dev.langchain4j.guardrail.GuardrailRequest;
import dev.langchain4j.guardrail.GuardrailResult;
import dev.langchain4j.guardrail.config.GuardrailsConfig;
import java.util.List;

/**
 * Base class of the builders of the {@link AbstractKeyedGuardrailService}s: it implements the setters of the
 * {@link GenericGuardrailService.Builder} once, on top of one {@link GuardrailBuilderSettings} for the input guardrails
 * and one for the output guardrails. A concrete builder adds what is specific to its kind of guardrails: how the
 * guardrails and executors are found for a key, and {@link #build()}.
 * <p>
 *     This class is sealed, like the services it builds: the builders of this package are the ones for AI services and
 *     for tools.
 * </p>
 *
 * @param <IP> The type of {@link GuardrailRequest} of the input guardrails
 * @param <IR> The type of {@link GuardrailResult} of the input guardrails
 * @param <OP> The type of {@link GuardrailRequest} of the output guardrails
 * @param <OR> The type of {@link GuardrailResult} of the output guardrails
 * @param <S>  The type of service the builder builds
 * @param <IG> The type of the input guardrails
 * @param <OG> The type of the output guardrails
 * @param <IC> The type of the configuration of the input guardrails
 * @param <OC> The type of the configuration of the output guardrails
 * @param <B>  The type of the builder, so that the methods can be chained
 */
abstract sealed class AbstractKeyedGuardrailServiceBuilder<
                IP extends GuardrailRequest<IP>,
                IR extends GuardrailResult<IR>,
                OP extends GuardrailRequest<OP>,
                OR extends GuardrailResult<OR>,
                S extends GenericGuardrailService<IP, IR, OP, OR>,
                IG extends Guardrail<IP, IR>,
                OG extends Guardrail<OP, OR>,
                IC extends GuardrailsConfig,
                OC extends GuardrailsConfig,
                B extends GenericGuardrailService.Builder<IP, IR, OP, OR, S, IG, OG, IC, OC, B>>
        implements GenericGuardrailService.Builder<IP, IR, OP, OR, S, IG, OG, IC, OC, B>
        permits GuardrailServiceBuilder, ToolGuardrailServiceBuilder {

    private final Class<?> targetClass;
    private final GuardrailBuilderSettings<IG, IC> input = new GuardrailBuilderSettings<>();
    private final GuardrailBuilderSettings<OG, OC> output = new GuardrailBuilderSettings<>();

    /**
     * @param targetClass The class the guardrails apply to (the AI service class). Must not be null.
     */
    protected AbstractKeyedGuardrailServiceBuilder(Class<?> targetClass) {
        this.targetClass = ensureNotNull(targetClass, "targetClass");
    }

    /**
     * The class the guardrails apply to.
     */
    protected Class<?> targetClass() {
        return this.targetClass;
    }

    /**
     * What the builder was given for the input guardrails.
     */
    protected GuardrailBuilderSettings<IG, IC> inputSettings() {
        return this.input;
    }

    /**
     * What the builder was given for the output guardrails.
     */
    protected GuardrailBuilderSettings<OG, OC> outputSettings() {
        return this.output;
    }

    @Override
    public B inputGuardrailsConfig(IC config) {
        this.input.config(config);
        return self();
    }

    @Override
    public B outputGuardrailsConfig(OC config) {
        this.output.config(config);
        return self();
    }

    @Override
    public <G extends IG> B inputGuardrailClasses(List<Class<? extends G>> guardrailClasses) {
        this.input.guardrailClasses(guardrailClasses);
        return self();
    }

    @Override
    public <G extends OG> B outputGuardrailClasses(List<Class<? extends G>> guardrailClasses) {
        this.output.guardrailClasses(guardrailClasses);
        return self();
    }

    @Override
    public <G extends IG> B inputGuardrails(List<G> guardrails) {
        this.input.guardrails(guardrails);
        return self();
    }

    @Override
    public <G extends OG> B outputGuardrails(List<G> guardrails) {
        this.output.guardrails(guardrails);
        return self();
    }

    @SuppressWarnings("unchecked")
    private B self() {
        return (B) this;
    }
}
