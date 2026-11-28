package dev.langchain4j.service.guardrail;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.agent.tool.ToolSource;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.guardrail.ToolInputGuardrailExecutor;
import dev.langchain4j.guardrail.ToolOutputGuardrailExecutor;
import java.util.Optional;

/**
 * The {@link ToolGuardrailService.ToolKey} of a tool in a {@link DefaultToolGuardrailService}: it holds the executors
 * of the chains of the tool, which {@link ToolGuardrailChainResolver} resolved when the tool was bound, and the service
 * that bound it. A chain without guardrails has no executor.
 *
 * @param owner             the service that bound the tool: the only one the key is meaningful to
 * @param toolSpecification the specification of the tool
 * @param toolSource        where the tool comes from
 * @param inputExecutor     the executor of the input guardrails of the tool, if any
 * @param outputExecutor    the executor of the output guardrails of the tool, if any
 */
record DefaultToolKey(
        DefaultToolGuardrailService owner,
        ToolSpecification toolSpecification,
        ToolSource toolSource,
        Optional<ToolInputGuardrailExecutor> inputExecutor,
        Optional<ToolOutputGuardrailExecutor> outputExecutor)
        implements ToolGuardrailService.ToolKey {

    DefaultToolKey {
        ensureNotNull(owner, "owner");
        ensureNotNull(toolSpecification, "toolSpecification");
        ensureNotNull(toolSource, "toolSource");
        ensureNotNull(inputExecutor, "inputExecutor");
        ensureNotNull(outputExecutor, "outputExecutor");
    }

    /**
     * Whether the key was bound by {@code service}: the same instance, not an equal one.
     */
    boolean isOwnedBy(DefaultToolGuardrailService service) {
        return this.owner == service;
    }

    @Override
    public String toString() {
        return "DefaultToolKey{toolName=%s, toolSource=%s, hasInputGuardrails=%s, hasOutputGuardrails=%s}"
                .formatted(toolName(), toolSource, inputExecutor.isPresent(), outputExecutor.isPresent());
    }
}
