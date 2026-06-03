package dev.langchain4j.service;

import dev.langchain4j.model.chat.ChatModel;

/**
 * Provides a system message each time an AI service method is invoked.
 * <p>
 * Unlike {@link AiServices#systemMessageProvider(java.util.function.Function)},
 * this provider receives a {@link InvocationContext} that exposes the {@link ChatModel}
 * configured on the AI service, allowing the system message to vary based on model capabilities.
 * <p>
 * When both {@code @SystemMessage} and this provider are configured,
 * {@code @SystemMessage} takes precedence.
 */
@FunctionalInterface
public interface SystemMessageProvider {

    /**
     * Returns the system message (or system message template) for the current invocation.
     *
     * @param invocationContext the invocation context
     * @return a system message or a template containing unresolved variables (e.g. "{{name}}"),
     *         which will be resolved using the values of method parameters annotated with @{@link V}
     */
    String get(InvocationContext invocationContext);

    /**
     * Context available to the {@link SystemMessageProvider} at invocation time.
     */
    interface InvocationContext {

        /**
         * Returns the chat memory ID for the current invocation.
         * This is the value of the method parameter annotated with @{@link MemoryId},
         * or "default" if no such parameter exists.
         */
        Object memoryId();

        /**
         * Returns the {@link ChatModel} configured on the AI service via
         * {@link AiServices#chatModel(ChatModel)}, or {@code null} if only a
         * streaming chat model was configured.
         * <p>
         * This is the default model set at build time; per-request overrides
         * via {@link dev.langchain4j.model.chat.request.ChatRequestParameters}
         * are not reflected here.
         */
        ChatModel defaultChatModel();
    }
}
