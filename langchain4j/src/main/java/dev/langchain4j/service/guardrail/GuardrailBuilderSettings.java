package dev.langchain4j.service.guardrail;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.classinstance.ClassInstanceLoader;
import dev.langchain4j.guardrail.Guardrail;
import dev.langchain4j.guardrail.config.GuardrailsConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * What a guardrail service builder has been given for one kind of guardrail (for example the input guardrails of AI
 * services, or the output guardrails of tools): a configuration, guardrail instances and guardrail classes.
 * <p>
 *     Setting guardrails or guardrail classes replaces what was set before, and {@code null} clears them. The guardrails
 *     the builder resolves are the instances first, followed by the guardrails created from the classes.
 * </p>
 *
 * @param <G> The type of {@link Guardrail}
 * @param <C> The type of {@link GuardrailsConfig}
 */
final class GuardrailBuilderSettings<G extends Guardrail<?, ?>, C extends GuardrailsConfig> {

    private C config;
    private final List<G> guardrails = new ArrayList<>();
    private final List<Class<? extends G>> guardrailClasses = new ArrayList<>();

    /**
     * Sets the configuration.
     *
     * @param config The configuration. Must not be null.
     * @return This instance.
     */
    GuardrailBuilderSettings<G, C> config(C config) {
        this.config = ensureNotNull(config, "config");
        return this;
    }

    /**
     * Sets the guardrail instances, replacing the ones set before.
     *
     * @param guardrails The guardrails. {@code null} clears them.
     * @return This instance.
     */
    GuardrailBuilderSettings<G, C> guardrails(List<? extends G> guardrails) {
        this.guardrails.clear();

        if (guardrails != null) {
            this.guardrails.addAll(guardrails);
        }

        return this;
    }

    /**
     * Sets the guardrail classes, replacing the ones set before.
     *
     * @param guardrailClasses The classes of the guardrails. {@code null} clears them.
     * @return This instance.
     */
    GuardrailBuilderSettings<G, C> guardrailClasses(List<? extends Class<? extends G>> guardrailClasses) {
        this.guardrailClasses.clear();

        if (guardrailClasses != null) {
            this.guardrailClasses.addAll(guardrailClasses);
        }

        return this;
    }

    /**
     * The configuration, if one was set.
     */
    Optional<C> config() {
        return Optional.ofNullable(this.config);
    }

    /**
     * Whether a configuration was set.
     */
    boolean hasConfig() {
        return this.config != null;
    }

    /**
     * Whether guardrail instances or guardrail classes were set.
     */
    boolean hasGuardrails() {
        return !this.guardrails.isEmpty() || !this.guardrailClasses.isEmpty();
    }

    /**
     * Whether both a configuration and guardrails were set.
     */
    boolean hasGuardrailsAndConfig() {
        return hasGuardrails() && hasConfig();
    }

    /**
     * The guardrails to run: the instances that were set, then the guardrails created from the classes that were set.
     * The classes are instantiated on every call.
     */
    List<G> resolveGuardrails() {
        return Stream.concat(this.guardrails.stream(), instantiateGuardrailClasses().stream())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * The guardrail classes that were set.
     */
    List<Class<? extends G>> guardrailClasses() {
        return List.copyOf(this.guardrailClasses);
    }

    /**
     * The guardrail instances that were set.
     */
    List<G> guardrailInstances() {
        return List.copyOf(this.guardrails);
    }

    /**
     * The guardrails created from the classes that were set, in order. The classes are instantiated on every call.
     */
    List<G> instantiateGuardrailClasses() {
        return this.guardrailClasses.stream()
                .<G>map(GuardrailBuilderSettings::instantiate)
                .toList();
    }

    /**
     * Creates guardrails from their classes, in order, through the same mechanism as {@link #instantiate(Class)}.
     *
     * @param guardrailClasses The classes of the guardrails. Must not be null, nor contain {@code null}.
     * @return The guardrails.
     * @param <G> The type of {@link Guardrail}
     */
    static <G> List<G> instantiateAll(Class<? extends G>[] guardrailClasses) {
        ensureNotNull(guardrailClasses, "guardrailClasses");
        return Stream.of(guardrailClasses)
                .<G>map(GuardrailBuilderSettings::instantiate)
                .toList();
    }

    /**
     * Creates a guardrail from its class, through the same mechanism for every kind of guardrail.
     *
     * @param guardrailClass The class of the guardrail. Must not be null.
     * @return The guardrail.
     * @param <G> The type of {@link Guardrail}
     */
    static <G> G instantiate(Class<? extends G> guardrailClass) {
        ensureNotNull(guardrailClass, "guardrailClass");
        return ClassInstanceLoader.getClassInstance(guardrailClass);
    }
}
