package dev.langchain4j.service.guardrail;

import dev.langchain4j.guardrail.ToolInputGuardrailExecutor;
import dev.langchain4j.guardrail.ToolOutputGuardrailExecutor;
import java.util.Optional;

/**
 * The {@link ToolGuardrailService.ToolKey} of a tool in a {@link DefaultToolGuardrailService}: it holds the executors
 * of the chains of the tool, which {@link ToolGuardrailChainResolver} resolved when the tool was bound. A chain without
 * guardrails has no executor.
 *
 * @param toolName       the name of the tool, for diagnostics
 * @param inputExecutor  the executor of the input guardrails of the tool, if any
 * @param outputExecutor the executor of the output guardrails of the tool, if any
 */
record DefaultToolKey(
        String toolName,
        Optional<ToolInputGuardrailExecutor> inputExecutor,
        Optional<ToolOutputGuardrailExecutor> outputExecutor)
        implements ToolGuardrailService.ToolKey {

    /**
     * The input executor held by the key, if the key is a {@link DefaultToolKey} and holds one.
     */
    static Optional<ToolInputGuardrailExecutor> inputExecutorOf(Object key) {
        return of(key).flatMap(DefaultToolKey::inputExecutor);
    }

    /**
     * The output executor held by the key, if the key is a {@link DefaultToolKey} and holds one.
     */
    static Optional<ToolOutputGuardrailExecutor> outputExecutorOf(Object key) {
        return of(key).flatMap(DefaultToolKey::outputExecutor);
    }

    private static Optional<DefaultToolKey> of(Object key) {
        return Optional.ofNullable(key).filter(DefaultToolKey.class::isInstance).map(DefaultToolKey.class::cast);
    }
}
