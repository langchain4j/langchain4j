package dev.langchain4j.service;

import dev.langchain4j.Internal;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.guardrail.ChatExecutor;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.tool.ToolServiceContext;
import java.util.List;
import java.util.function.Function;

/**
 * Builds the {@link ChatExecutor} handed to output guardrails on a {@code reprompt()}.
 * <p>
 * Output guardrails must only ever see a final textual response, never an intermediate tool-only
 * {@link dev.langchain4j.data.message.AiMessage}. So when the reprompted model call requests tools, this executor
 * fully resolves them through {@code ToolService.executeInferenceAndToolsLoop} before returning, instead of handing
 * the raw response straight to the next guardrail.
 * <p>
 * The same wrapper is used by both the synchronous and the streaming paths; they differ only in the
 * {@code chatModelInvoker} used to perform each model call (a {@code ChatModel} for the former, a blocking
 * {@code StreamingChatModel} call for the latter).
 */
@Internal
final class ToolAwareRepromptExecutor {

    private ToolAwareRepromptExecutor() {}

    static ChatExecutor wrap(
            ChatExecutor rawChatExecutor,
            AiServiceContext context,
            Object memoryId,
            ChatRequestParameters parameters,
            InvocationContext invocationContext,
            ToolServiceContext toolServiceContext,
            Function<ChatRequest, ChatResponse> chatModelInvoker) {
        return new ChatExecutor() {
            @Override
            public ChatResponse execute() {
                return rawChatExecutor.execute();
            }

            @Override
            public ChatResponse execute(List<ChatMessage> chatMessages) {
                ChatResponse initialResponse = rawChatExecutor.execute(chatMessages);

                if (!initialResponse.aiMessage().hasToolExecutionRequests()) {
                    return initialResponse;
                }

                return context.toolService
                        .executeInferenceAndToolsLoop(
                                context,
                                memoryId,
                                initialResponse,
                                parameters,
                                chatMessages,
                                null,
                                invocationContext,
                                toolServiceContext,
                                chatModelInvoker)
                        .aggregateResponse();
            }
        };
    }
}
