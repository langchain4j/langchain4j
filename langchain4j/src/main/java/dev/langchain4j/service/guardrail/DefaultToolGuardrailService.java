package dev.langchain4j.service.guardrail;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.guardrail.ToolInputGuardrailExecutor;
import dev.langchain4j.guardrail.ToolInputGuardrailRequest;
import dev.langchain4j.guardrail.ToolInputGuardrailResult;
import dev.langchain4j.guardrail.ToolOutputGuardrailExecutor;
import dev.langchain4j.guardrail.ToolOutputGuardrailRequest;
import dev.langchain4j.guardrail.ToolOutputGuardrailResult;
import dev.langchain4j.service.tool.AiServiceTool;

/**
 * The default {@link ToolGuardrailService}. Obtain instances via {@link ToolGuardrailService#builder(Class)}.
 * <p>
 *     It is the tool counterpart of {@link DefaultAiServiceGuardrailService}, with one difference in where the chains
 *     are resolved. The AI service one gets a finished {@code method → executor} map from its builder: the methods of
 *     an AI service are all known when it is built. That is not possible for tools: they are bound after the service is
 *     built, and the tools of a {@link dev.langchain4j.service.tool.ToolProvider} (for example an MCP server) can be
 *     provided anew, and so bound, for every invocation. So the chains are resolved when a tool is bound, by the
 *     {@link ToolGuardrailChainResolver} the builder creates (the annotation lookup, the class hierarchy walk, the
 *     removal of duplicates and the creation of the executors live there).
 * </p>
 * <p>
 *     {@link #bind(Class, AiServiceTool)} returns a {@link DefaultToolKey} that holds the executors of the chains of
 *     the tool, and the lookups read them out of the key. Nothing is kept here per tool, so a map of keys cannot grow
 *     with every invocation: the keys of the tools provided for an invocation are not retained after it.
 * </p>
 */
final class DefaultToolGuardrailService
        extends AbstractKeyedGuardrailService<
                ToolInputGuardrailRequest,
                ToolInputGuardrailResult,
                ToolOutputGuardrailRequest,
                ToolOutputGuardrailResult,
                ToolInputGuardrailExecutor,
                ToolOutputGuardrailExecutor>
        implements ToolGuardrailService {

    private final ToolGuardrailChainResolver chainResolver;

    DefaultToolGuardrailService(Class<?> aiServiceClass, ToolGuardrailChainResolver chainResolver) {
        super(
                aiServiceClass,
                KeyedGuardrailExecutors.withLookUp(DefaultToolKey::inputExecutorOf, ToolInputGuardrailResult::success),
                KeyedGuardrailExecutors.withLookUp(
                        DefaultToolKey::outputExecutorOf, ToolOutputGuardrailResult::success));
        this.chainResolver = ensureNotNull(chainResolver, "chainResolver");
    }

    @Override
    public ToolKey bind(Class<?> toolClass, AiServiceTool tool) {
        return this.chainResolver.resolve(toolClass, tool);
    }
}
