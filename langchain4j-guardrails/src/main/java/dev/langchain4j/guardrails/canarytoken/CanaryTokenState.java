package dev.langchain4j.guardrails.canarytoken;

import dev.langchain4j.Internal;
import dev.langchain4j.guardrail.GuardrailRequestParams;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.invocation.LangChain4jManaged;
import java.util.Map;
import java.util.Optional;

/**
 * Holds the per-invocation canary token value, shared between
 * {@link CanaryTokenInputGuardrail} and {@link CanaryTokenOutputGuardrail}.
 * <p>
 * State is stored and retrieved exclusively via {@link GuardrailRequestParams},
 * keeping all {@link InvocationContext#managedParameters()} plumbing internal to
 * this class. Neither guardrail implementation needs to reference
 * {@link InvocationContext} or {@link LangChain4jManaged} directly.
 * </p>
 *
 * <p>Usage from {@link CanaryTokenInputGuardrail}:</p>
 * <pre>{@code
 * // store
 * CanaryTokenState.store(request.requestParams(), new CanaryTokenState(canaryValue));
 *
 * // idempotency check
 * if (CanaryTokenState.isPresent(request.requestParams())) { return success(); }
 * }</pre>
 *
 * <p>Usage from {@link CanaryTokenOutputGuardrail}:</p>
 * <pre>{@code
 * Optional<CanaryTokenState> state = CanaryTokenState.from(request.requestParams());
 * state.ifPresent(s -> ...);
 * }</pre>
 */
@Internal
public record CanaryTokenState(String canaryValue) implements LangChain4jManaged {

    /**
     * Returns the {@link CanaryTokenState} stored in the given {@link GuardrailRequestParams},
     * or {@link Optional#empty()} if none was stored yet.
     *
     * @param params the guardrail request params for the current invocation
     * @return an {@link Optional} containing the stored state, or empty
     */
    public static Optional<CanaryTokenState> from(GuardrailRequestParams params) {
        Map<Class<? extends LangChain4jManaged>, LangChain4jManaged> managed = managedMap(params);
        if (managed == null) {
            return Optional.empty();
        }
        LangChain4jManaged value = managed.get(CanaryTokenState.class);
        return (value instanceof CanaryTokenState state) ? Optional.of(state) : Optional.empty();
    }

    /**
     * Returns {@code true} if a {@link CanaryTokenState} is already present in the given params,
     * which means the canary was already injected for this invocation.
     *
     * @param params the guardrail request params for the current invocation
     * @return {@code true} if a canary state is stored
     */
    public static boolean isPresent(GuardrailRequestParams params) {
        Map<Class<? extends LangChain4jManaged>, LangChain4jManaged> managed = managedMap(params);
        return managed != null && managed.containsKey(CanaryTokenState.class);
    }

    /**
     * Stores the given {@link CanaryTokenState} into the given {@link GuardrailRequestParams}.
     * Does nothing if the managed-parameters map is not available.
     *
     * @param params the guardrail request params for the current invocation
     * @param state  the state to store
     */
    public static void store(GuardrailRequestParams params, CanaryTokenState state) {
        Map<Class<? extends LangChain4jManaged>, LangChain4jManaged> managed = managedMap(params);
        if (managed != null) {
            managed.put(CanaryTokenState.class, state);
        }
    }

    private static Map<Class<? extends LangChain4jManaged>, LangChain4jManaged> managedMap(
            GuardrailRequestParams params) {
        if (params == null) {
            return null;
        }
        InvocationContext ctx = params.invocationContext();
        if (ctx == null) {
            return null;
        }
        return ctx.managedParameters();
    }
}
