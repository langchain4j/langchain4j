package dev.langchain4j.service.guardrail;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Internal;
import dev.langchain4j.guardrail.GuardrailExecutor;
import dev.langchain4j.guardrail.GuardrailRequest;
import dev.langchain4j.guardrail.GuardrailResult;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Base class of the {@link GenericGuardrailService}s that look up the guardrails to run by key, in executors that are
 * registered up front: the input executor and the output executor of each key.
 * <p>
 *     The lookup, the null key handling and the "no guardrails" results live in {@code KeyedGuardrailExecutors}; this
 *     class puts the input side and the output side together behind the {@link GenericGuardrailService} contract.
 * </p>
 * <p>
 *     This class is sealed: the services built on it are the ones in this package (the AI service one, and the one
 *     for tools). The deprecated {@link AbstractGuardrailService}, which extension frameworks subclass, is the only
 *     permitted subclass that is not final, and goes away with it. Frameworks customize guardrails through the SPIs
 *     the default services use, not by extending this class.
 * </p>
 *
 * @param <IP> The type of {@link GuardrailRequest} of the input guardrails
 * @param <IR> The type of {@link GuardrailResult} of the input guardrails
 * @param <OP> The type of {@link GuardrailRequest} of the output guardrails
 * @param <OR> The type of {@link GuardrailResult} of the output guardrails
 * @param <IE> The type of {@link GuardrailExecutor} of the input guardrails
 * @param <OE> The type of {@link GuardrailExecutor} of the output guardrails
 * @since 1.25.0
 */
@Internal
public abstract sealed class AbstractKeyedGuardrailService<
                IP extends GuardrailRequest<IP>,
                IR extends GuardrailResult<IR>,
                OP extends GuardrailRequest<OP>,
                OR extends GuardrailResult<OR>,
                IE extends GuardrailExecutor<?, IP, IR, ?, ?>,
                OE extends GuardrailExecutor<?, OP, OR, ?, ?>>
        implements GenericGuardrailService<IP, IR, OP, OR>
        permits AbstractGuardrailService, DefaultAiServiceGuardrailService, DefaultToolGuardrailService {

    private final Class<?> targetClass;
    private final KeyedGuardrailExecutors<IP, IR, IE> inputGuardrails;
    private final KeyedGuardrailExecutors<OP, OR, OE> outputGuardrails;

    /**
     * Creates a service whose keys are used as they are, with no translation.
     *
     * @param targetClass         The class the guardrails apply to. Must not be null.
     * @param inputGuardrails     The input executor of each key. {@code null} means none.
     * @param outputGuardrails    The output executor of each key. {@code null} means none.
     * @param noInputGuardrails   Provides the result for a key that has no input executor.
     * @param noOutputGuardrails  Provides the result for a key that has no output executor.
     */
    protected AbstractKeyedGuardrailService(
            Class<?> targetClass,
            Map<Object, IE> inputGuardrails,
            Map<Object, OE> outputGuardrails,
            Supplier<IR> noInputGuardrails,
            Supplier<OR> noOutputGuardrails) {
        this(
                targetClass,
                inputGuardrails,
                outputGuardrails,
                noInputGuardrails,
                noOutputGuardrails,
                UnaryOperator.identity());
    }

    /**
     * Creates a service that translates the keys it is called with before looking them up.
     *
     * @param targetClass         The class the guardrails apply to. Must not be null.
     * @param inputGuardrails     The input executor of each key. {@code null} means none.
     * @param outputGuardrails    The output executor of each key. {@code null} means none.
     * @param noInputGuardrails   Provides the result for a key that has no input executor.
     * @param noOutputGuardrails  Provides the result for a key that has no output executor.
     * @param keyResolver         Translates the key a caller identifies the target with into the key the executors are
     *                            registered under. It is applied by every lookup, blocking and non-blocking alike.
     */
    protected AbstractKeyedGuardrailService(
            Class<?> targetClass,
            Map<Object, IE> inputGuardrails,
            Map<Object, OE> outputGuardrails,
            Supplier<IR> noInputGuardrails,
            Supplier<OR> noOutputGuardrails,
            UnaryOperator<Object> keyResolver) {
        this(
                targetClass,
                new KeyedGuardrailExecutors<>(inputGuardrails, noInputGuardrails, keyResolver),
                new KeyedGuardrailExecutors<>(outputGuardrails, noOutputGuardrails, keyResolver));
    }

    /**
     * Creates a service from how its input and output executors are found, for example a service whose keys carry
     * their executors.
     *
     * @param targetClass      The class the guardrails apply to. Must not be null.
     * @param inputGuardrails  Finds the input executor of a key.
     * @param outputGuardrails Finds the output executor of a key.
     */
    AbstractKeyedGuardrailService(
            Class<?> targetClass,
            KeyedGuardrailExecutors<IP, IR, IE> inputGuardrails,
            KeyedGuardrailExecutors<OP, OR, OE> outputGuardrails) {
        this.targetClass = ensureNotNull(targetClass, "targetClass");
        this.inputGuardrails = ensureNotNull(inputGuardrails, "inputGuardrails");
        this.outputGuardrails = ensureNotNull(outputGuardrails, "outputGuardrails");
    }

    @Override
    public Class<?> targetClass() {
        return this.targetClass;
    }

    @Override
    public <K> IR executeInputGuardrails(K key, IP request) {
        return this.inputGuardrails.execute(key, request);
    }

    @Override
    public <K> CompletableFuture<IR> executeInputGuardrailsAsync(K key, IP request) {
        return this.inputGuardrails.executeAsync(key, request);
    }

    @Override
    public <K> OR executeOutputGuardrails(K key, OP request) {
        return this.outputGuardrails.execute(key, request);
    }

    @Override
    public <K> CompletableFuture<OR> executeOutputGuardrailsAsync(K key, OP request) {
        return this.outputGuardrails.executeAsync(key, request);
    }

    @Override
    public <K> boolean hasInputGuardrails(K key) {
        return this.inputGuardrails.hasGuardrails(key);
    }

    @Override
    public <K> boolean hasOutputGuardrails(K key) {
        return this.outputGuardrails.hasGuardrails(key);
    }

    /**
     * The input executor registered for the key, if any.
     *
     * @param key The key identifying what the guardrails apply to. It is translated by the key resolver first.
     * @param <K> The type of the key.
     * @return The executor, or empty if none is registered for the key.
     */
    <K> Optional<IE> inputExecutor(K key) {
        return this.inputGuardrails.executor(key);
    }

    /**
     * The output executor registered for the key, if any.
     *
     * @param key The key identifying what the guardrails apply to. It is translated by the key resolver first.
     * @param <K> The type of the key.
     * @return The executor, or empty if none is registered for the key.
     */
    <K> Optional<OE> outputExecutor(K key) {
        return this.outputGuardrails.executor(key);
    }
}
