package dev.langchain4j.service.guardrail;

import dev.langchain4j.guardrail.Guardrail;
import dev.langchain4j.guardrail.GuardrailRequest;
import dev.langchain4j.guardrail.GuardrailResult;
import dev.langchain4j.guardrail.config.GuardrailsConfig;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * The contract shared by every service that executes guardrails on behalf of a <em>target</em> (the AI service for
 * {@link GuardrailService}, the tools of an AI service for tool guardrails): given a key that identifies what the
 * guardrails apply to (for AI services, the method), it runs the input guardrails or the output guardrails registered
 * for it.
 * <p>
 *     The key is opaque to this contract. A key without guardrails is not an error: the execution then returns a
 *     successful result.
 * </p>
 * <p>
 *     This is the black box the rest of the framework, and the frameworks that extend it, depend on, without knowing
 *     which kind of guardrails is behind it.
 * </p>
 * <p>
 *     This interface is sealed: the kinds of guardrail services are the ones LangChain4j defines, so the request and
 *     result types of every service match its kind of guardrails. Each kind's interface ({@link GuardrailService} for
 *     AI services) is {@code non-sealed}: frameworks implement that interface, or customize the default
 *     implementation through its SPIs.
 * </p>
 *
 * @param <IP> The type of {@link GuardrailRequest} of the input guardrails
 * @param <IR> The type of {@link GuardrailResult} of the input guardrails
 * @param <OP> The type of {@link GuardrailRequest} of the output guardrails
 * @param <OR> The type of {@link GuardrailResult} of the output guardrails
 * @since 1.24.0
 */
public sealed interface GenericGuardrailService<
                IP extends GuardrailRequest<IP>,
                IR extends GuardrailResult<IR>,
                OP extends GuardrailRequest<OP>,
                OR extends GuardrailResult<OR>>
        permits GuardrailService, AbstractKeyedGuardrailService {

    /**
     * The class the guardrails of this service apply to: the AI service class, for AI service guardrails.
     *
     * @return The target class.
     */
    Class<?> targetClass();

    /**
     * Executes the input guardrails registered for the key.
     *
     * @param key The key identifying what the guardrails apply to.
     * @param request The request to validate. Must not be null.
     * @param <K> The type of the key.
     * @return The result, a success if no guardrail is registered for the key.
     */
    <K> IR executeInputGuardrails(K key, IP request);

    /**
     * Non-blocking counterpart of {@link #executeInputGuardrails(Object, GuardrailRequest)}.
     *
     * @param key The key identifying what the guardrails apply to.
     * @param request The request to validate. Must not be null.
     * @param <K> The type of the key.
     * @return A {@link CompletableFuture} that completes with the result.
     */
    <K> CompletableFuture<IR> executeInputGuardrailsAsync(K key, IP request);

    /**
     * Executes the output guardrails registered for the key.
     *
     * @param key The key identifying what the guardrails apply to.
     * @param request The request to validate. Must not be null.
     * @param <K> The type of the key.
     * @return The result, a success if no guardrail is registered for the key.
     */
    <K> OR executeOutputGuardrails(K key, OP request);

    /**
     * Non-blocking counterpart of {@link #executeOutputGuardrails(Object, GuardrailRequest)}.
     *
     * @param key The key identifying what the guardrails apply to.
     * @param request The request to validate. Must not be null.
     * @param <K> The type of the key.
     * @return A {@link CompletableFuture} that completes with the result.
     */
    <K> CompletableFuture<OR> executeOutputGuardrailsAsync(K key, OP request);

    /**
     * Whether input guardrails are registered for the key.
     *
     * @param key The key identifying what the guardrails apply to.
     * @param <K> The type of the key.
     * @return {@code true} if there is at least one input guardrail.
     */
    <K> boolean hasInputGuardrails(K key);

    /**
     * Whether output guardrails are registered for the key.
     *
     * @param key The key identifying what the guardrails apply to.
     * @param <K> The type of the key.
     * @return {@code true} if there is at least one output guardrail.
     */
    <K> boolean hasOutputGuardrails(K key);

    /**
     * Builds a {@link GenericGuardrailService}. It mirrors the hierarchy of the services: every kind of guardrail
     * service has a builder that extends this one, with the guardrail and configuration types of that kind.
     * <p>
     *     A builder is given a configuration, guardrail instances and guardrail classes for the input guardrails and for
     *     the output guardrails. Setting guardrails, or guardrail classes, replaces what was set before, and
     *     {@code null} clears them.
     * </p>
     * <p>
     *     The type parameters tie the guardrails to the service: the input guardrails validate the requests the
     *     service runs its input guardrails on, and the output guardrails the requests of its output guardrails. Like
     *     the services, this interface is sealed, and the builder interface of each kind (such as
     *     {@link GuardrailService.Builder}) is {@code non-sealed}.
     * </p>
     *
     * @param <IP> The type of {@link GuardrailRequest} of the input guardrails
     * @param <IR> The type of {@link GuardrailResult} of the input guardrails
     * @param <OP> The type of {@link GuardrailRequest} of the output guardrails
     * @param <OR> The type of {@link GuardrailResult} of the output guardrails
     * @param <S>  The type of service the builder builds
     * @param <IG> The type of the input guardrails, which validate the requests of type {@code IP}
     * @param <OG> The type of the output guardrails, which validate the requests of type {@code OP}
     * @param <IC> The type of the configuration of the input guardrails
     * @param <OC> The type of the configuration of the output guardrails
     * @param <B>  The type of the builder, so that the methods can be chained
     * @since 1.24.0
     */
    sealed interface Builder<
                    IP extends GuardrailRequest<IP>,
                    IR extends GuardrailResult<IR>,
                    OP extends GuardrailRequest<OP>,
                    OR extends GuardrailResult<OR>,
                    S extends GenericGuardrailService<IP, IR, OP, OR>,
                    IG extends Guardrail<IP, IR>,
                    OG extends Guardrail<OP, OR>,
                    IC extends GuardrailsConfig,
                    OC extends GuardrailsConfig,
                    B extends Builder<IP, IR, OP, OR, S, IG, OG, IC, OC, B>>
            permits GuardrailService.Builder, AbstractKeyedGuardrailServiceBuilder {

        /**
         * Configures the input guardrails for the builder.
         *
         * @param config The configuration for input guardrails. Must not be null.
         * @return The current instance of the builder for method chaining.
         * @throws IllegalArgumentException if {@code config} is null.
         */
        B inputGuardrailsConfig(IC config);

        /**
         * Configures the output guardrails for the builder.
         *
         * @param config The configuration for output guardrails. Must not be null.
         * @return The current instance of the builder for method chaining.
         * @throws IllegalArgumentException if {@code config} is null.
         */
        B outputGuardrailsConfig(OC config);

        /**
         * Configures the classes of input guardrails for the builder. Existing input guardrail classes will be cleared.
         *
         * @param guardrailClasses A list of classes of input guardrails. May be {@code null}.
         * @param <G> The type of the input guardrails
         * @return The current instance of the builder for method chaining.
         */
        <G extends IG> B inputGuardrailClasses(List<Class<? extends G>> guardrailClasses);

        /**
         * Configures the classes of input guardrails for the builder. Existing input guardrail classes will be cleared.
         *
         * @param guardrailClasses An array of classes of input guardrails. May be {@code null}.
         * @param <G> The type of the input guardrails
         * @return The current instance of the builder for method chaining.
         */
        @SuppressWarnings("unchecked")
        default <G extends IG> B inputGuardrailClasses(Class<? extends G>... guardrailClasses) {
            return Optional.ofNullable(guardrailClasses)
                    .map(classes -> inputGuardrailClasses(List.of(classes)))
                    .orElseGet(this::self);
        }

        /**
         * Configures the classes of output guardrails for the builder. Existing output guardrail classes will be cleared.
         *
         * @param guardrailClasses A list of classes of output guardrails. May be {@code null}.
         * @param <G> The type of the output guardrails
         * @return The current instance of the builder for method chaining.
         */
        <G extends OG> B outputGuardrailClasses(List<Class<? extends G>> guardrailClasses);

        /**
         * Configures the classes of output guardrails for the builder. Existing output guardrail classes will be cleared.
         *
         * @param guardrailClasses An array of classes of output guardrails. May be {@code null}.
         * @param <G> The type of the output guardrails
         * @return The current instance of the builder for method chaining.
         */
        @SuppressWarnings("unchecked")
        default <G extends OG> B outputGuardrailClasses(Class<? extends G>... guardrailClasses) {
            return Optional.ofNullable(guardrailClasses)
                    .map(classes -> outputGuardrailClasses(List.of(classes)))
                    .orElseGet(this::self);
        }

        /**
         * Sets the input guardrails for the builder. Existing input guardrails will be cleared.
         *
         * @param guardrails A list of input guardrails. May be {@code null}, in which case no guardrails will be set.
         * @param <G> The type of the input guardrails
         * @return The current instance of the builder for method chaining.
         */
        <G extends IG> B inputGuardrails(List<G> guardrails);

        /**
         * Sets the input guardrails for the builder. Existing input guardrails will be cleared.
         *
         * @param guardrails An array of input guardrails. May be {@code null}, in which case no guardrails will be set.
         * @param <G> The type of the input guardrails
         * @return The current instance of the builder for method chaining.
         */
        @SuppressWarnings("unchecked")
        default <G extends IG> B inputGuardrails(G... guardrails) {
            return Optional.ofNullable(guardrails)
                    .map(instances -> inputGuardrails(List.of(instances)))
                    .orElseGet(this::self);
        }

        /**
         * Sets the output guardrails for the builder. Existing output guardrails will be cleared.
         *
         * @param guardrails A list of output guardrails. May be {@code null}, in which case no guardrails will be set.
         * @param <G> The type of the output guardrails
         * @return The current instance of the builder for method chaining.
         */
        <G extends OG> B outputGuardrails(List<G> guardrails);

        /**
         * Sets the output guardrails for the builder. Existing output guardrails will be cleared.
         *
         * @param guardrails An array of output guardrails. May be {@code null}, in which case no guardrails will be set.
         * @param <G> The type of the output guardrails
         * @return The current instance of the builder for method chaining.
         */
        @SuppressWarnings("unchecked")
        default <G extends OG> B outputGuardrails(G... guardrails) {
            return Optional.ofNullable(guardrails)
                    .map(instances -> outputGuardrails(List.of(instances)))
                    .orElseGet(this::self);
        }

        /**
         * Builds the service.
         *
         * @return The service, configured with the guardrails and the configuration the builder was given.
         */
        S build();

        @SuppressWarnings("unchecked")
        private B self() {
            return (B) this;
        }
    }
}
