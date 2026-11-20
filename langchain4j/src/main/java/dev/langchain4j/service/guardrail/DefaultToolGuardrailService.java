package dev.langchain4j.service.guardrail;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.guardrail.ToolInputGuardrailExecutor;
import dev.langchain4j.guardrail.ToolInputGuardrailRequest;
import dev.langchain4j.guardrail.ToolInputGuardrailResult;
import dev.langchain4j.guardrail.ToolOutputGuardrailExecutor;
import dev.langchain4j.guardrail.ToolOutputGuardrailRequest;
import dev.langchain4j.guardrail.ToolOutputGuardrailResult;
import dev.langchain4j.service.tool.AiServiceTool;
import dev.langchain4j.service.tool.ToolProvider;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * The default {@link ToolGuardrailService}. Obtain instances via {@link ToolGuardrailService#builder(Class)}.
 * <p>
 *     It differs from the default {@link GuardrailService} in where the chains are resolved. The AI service one gets a
 *     finished {@code method → executor} map from its builder: the methods of an AI service are all known when it is
 *     built. That is not possible for tools: they are bound after the service is built, and the tools of a
 *     {@link ToolProvider} (for example an MCP server) can be provided anew, and so bound, for every invocation. So the
 *     chains are resolved when a tool is bound, by the {@link ToolGuardrailChainResolver} the builder creates (the
 *     annotation lookup, the class hierarchy walk, the removal of duplicates and the creation of the executors live
 *     there).
 * </p>
 * <p>
 *     {@link #bind(Class, AiServiceTool)} returns a {@link DefaultToolKey} that holds the executors of the chains of
 *     the tool, and the lookups read them out of the key. Nothing is kept here per tool, so a map of keys cannot grow
 *     with every invocation: the keys of the tools provided for an invocation are not retained after it.
 * </p>
 * <p>
 *     Every lookup rejects a key this instance did not bind: {@code null}, a key of another implementation, and a
 *     {@link DefaultToolKey} of another instance (which would run the chain of that other service, silently skipping
 *     the global guardrails of this one).
 * </p>
 */
final class DefaultToolGuardrailService implements ToolGuardrailService {

    private final Class<?> aiServiceClass;
    private final ToolGuardrailChainResolver chainResolver;
    private final KeyedGuardrailExecutors<ToolInputGuardrailRequest, ToolInputGuardrailResult, ToolInputGuardrailExecutor>
            inputGuardrails;
    private final KeyedGuardrailExecutors<
                    ToolOutputGuardrailRequest, ToolOutputGuardrailResult, ToolOutputGuardrailExecutor>
            outputGuardrails;

    DefaultToolGuardrailService(Class<?> aiServiceClass, ToolGuardrailChainResolver chainResolver) {
        this.aiServiceClass = ensureNotNull(aiServiceClass, "aiServiceClass");
        this.chainResolver = ensureNotNull(chainResolver, "chainResolver");
        this.inputGuardrails = new KeyedGuardrailExecutors<>(
                key -> ownKey(key).inputExecutor(), ToolInputGuardrailResult::success);
        this.outputGuardrails = new KeyedGuardrailExecutors<>(
                key -> ownKey(key).outputExecutor(), ToolOutputGuardrailResult::success);
    }

    @Override
    public Class<?> targetClass() {
        return this.aiServiceClass;
    }

    @Override
    public ToolKey bind(Class<?> toolClass, AiServiceTool tool) {
        return this.chainResolver.resolve(this, toolClass, tool);
    }

    @Override
    public ToolInputGuardrailResult executeInputGuardrails(ToolKey key, ToolInputGuardrailRequest request) {
        return this.inputGuardrails.execute(key, request);
    }

    @Override
    public CompletableFuture<ToolInputGuardrailResult> executeInputGuardrailsAsync(
            ToolKey key, ToolInputGuardrailRequest request) {
        return this.inputGuardrails.executeAsync(key, request);
    }

    @Override
    public ToolOutputGuardrailResult executeOutputGuardrails(ToolKey key, ToolOutputGuardrailRequest request) {
        return this.outputGuardrails.execute(key, request);
    }

    @Override
    public CompletableFuture<ToolOutputGuardrailResult> executeOutputGuardrailsAsync(
            ToolKey key, ToolOutputGuardrailRequest request) {
        return this.outputGuardrails.executeAsync(key, request);
    }

    @Override
    public boolean hasInputGuardrails(ToolKey key) {
        return this.inputGuardrails.hasGuardrails(key);
    }

    @Override
    public boolean hasOutputGuardrails(ToolKey key) {
        return this.outputGuardrails.hasGuardrails(key);
    }

    /**
     * The key as a {@link DefaultToolKey} bound by this instance. Any other key fails: running no guardrails for it,
     * or the guardrails of another service, would silently skip the guardrails of this one.
     */
    private DefaultToolKey ownKey(Object key) {
        return Optional.ofNullable(key)
                .filter(DefaultToolKey.class::isInstance)
                .map(DefaultToolKey.class::cast)
                .filter(defaultToolKey -> defaultToolKey.isOwnedBy(this))
                .orElseThrow(() -> new IllegalArgumentException(foreignKeyMessage(key)));
    }

    private static String foreignKeyMessage(Object key) {
        String message;

        if (key == null) {
            message = "The tool key must not be null: pass the key bind(..) returned for the tool";
        } else if (key instanceof DefaultToolKey defaultToolKey) {
            message = "The key of the tool '%s' was returned by bind(..) of another tool guardrail service"
                    .formatted(defaultToolKey.toolName());
        } else {
            message = "%s is not a key returned by bind(..) of this tool guardrail service"
                    .formatted(key.getClass().getName());
        }

        return message;
    }
}
