package dev.langchain4j.service.guardrail;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.guardrail.Guardrail;
import dev.langchain4j.guardrail.GuardrailExecutor;
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
 * @param <IE> The type of the executors of the input guardrails, which run guardrails of type {@code IG} with a
 *             configuration of type {@code IC}
 * @param <OE> The type of the executors of the output guardrails, which run guardrails of type {@code OG} with a
 *             configuration of type {@code OC}
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
                IE extends GuardrailExecutor<IC, IP, IR, IG, ?>,
                OE extends GuardrailExecutor<OC, OP, OR, OG, ?>,
                B extends GenericGuardrailService.Builder<IP, IR, OP, OR, S, IG, OG, IC, OC, B>>
        implements GenericGuardrailService.Builder<IP, IR, OP, OR, S, IG, OG, IC, OC, B>
        permits GuardrailServiceBuilder {

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

    /**
     * Creates an executor for the input guardrails.
     *
     * @param config     The configuration of the executor. {@code null} means the default.
     * @param guardrails The guardrails the executor runs.
     */
    protected abstract IE newInputExecutor(IC config, List<IG> guardrails);

    /**
     * Creates an executor for the output guardrails.
     *
     * @param config     The configuration of the executor. {@code null} means the default.
     * @param guardrails The guardrails the executor runs.
     */
    protected abstract OE newOutputExecutor(OC config, List<OG> guardrails);

    /**
     * The executor for the input guardrails the builder was given, with the configuration it was given. It runs no
     * guardrail if the builder was given none.
     */
    protected IE defaultInputExecutor() {
        return newInputExecutor(this.input.config().orElse(null), this.input.resolveGuardrails());
    }

    /**
     * The executor for the output guardrails the builder was given, with the configuration it was given. It runs no
     * guardrail if the builder was given none.
     */
    protected OE defaultOutputExecutor() {
        return newOutputExecutor(this.output.config().orElse(null), this.output.resolveGuardrails());
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
