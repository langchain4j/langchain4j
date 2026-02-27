package dev.langchain4j.guardrails.canarytoken;

import dev.langchain4j.Internal;
import dev.langchain4j.invocation.LangChain4jManaged;

/**
 * Holds the per-invocation canary token value, shared between
 * {@link CanaryTokenInputGuardrail} and {@link CanaryTokenOutputGuardrail}
 * via {@code InvocationContext.managedParameters()}.
 * <p>
 * By implementing {@link LangChain4jManaged} the state is stored in the
 * invocation-scoped managed-parameters map, keyed by {@code CanaryTokenState.class},
 * with no changes required to {@code GuardrailRequestParams}.
 * </p>
 */
@Internal
public record CanaryTokenState(String canaryValue) implements LangChain4jManaged {}
