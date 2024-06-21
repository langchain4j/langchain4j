package dev.langchain4j.service;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolExecutor;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * Handles response from a language model for AI Service that is streamed token-by-token.
 * Handles both regular (text) responses and responses with the request to execute one or multiple tools.
 */
class AiServiceStreamingResponseHandler implements StreamingResponseHandler<AiMessage> {

    private final Logger log = LoggerFactory.getLogger(AiServiceStreamingResponseHandler.class);

    private final AiServiceContext context;
    private final Object memoryId;

    private final List<ChatMessage> tempToolExecutionMessages;

    private final Consumer<String> tokenHandler;
    private final Consumer<Response<AiMessage>> completionHandler;
    private final Consumer<Throwable> errorHandler;

    private final TokenUsage tokenUsage;

    AiServiceStreamingResponseHandler(AiServiceContext context,
                                      Object memoryId,
                                      List<ChatMessage> tempToolExecutionMessages,
                                      Consumer<String> tokenHandler,
                                      Consumer<Response<AiMessage>> completionHandler,
                                      Consumer<Throwable> errorHandler,
                                      TokenUsage tokenUsage) {
        this.context = ensureNotNull(context, "context");
        this.memoryId = ensureNotNull(memoryId, "memoryId");
        this.tempToolExecutionMessages = new ArrayList<>(tempToolExecutionMessages);

        this.tokenHandler = ensureNotNull(tokenHandler, "tokenHandler");
        this.completionHandler = completionHandler;
        this.errorHandler = errorHandler;

        this.tokenUsage = ensureNotNull(tokenUsage, "tokenUsage");
    }

    @Override
    public void onNext(String token) {
        tokenHandler.accept(token);
    }

    @Override
    public void onComplete(Response<AiMessage> response) {

        AiMessage aiMessage = response.content();
        addChatMessage(aiMessage);

        if (aiMessage.hasToolExecutionRequests()) {
            for (ToolExecutionRequest toolExecutionRequest : aiMessage.toolExecutionRequests()) {
                ToolExecutor toolExecutor = context.toolExecutors.get(toolExecutionRequest.name());
                String toolExecutionResult = toolExecutor.execute(toolExecutionRequest, memoryId);
                ToolExecutionResultMessage toolExecutionResultMessage = ToolExecutionResultMessage.from(
                        toolExecutionRequest,
                        toolExecutionResult
                );
                addChatMessage(toolExecutionResultMessage);
            }

            context.streamingChatModel.generate(
                    getMemoryMessages(memoryId),
                    context.toolSpecifications,
                    new AiServiceStreamingResponseHandler(
                            context,
                            memoryId,
                            tempToolExecutionMessages,
                            tokenHandler,
                            completionHandler,
                            errorHandler,
                            TokenUsage.sum(tokenUsage, response.tokenUsage())
                    )
            );
        } else {
            if (completionHandler != null) {
                completionHandler.accept(Response.from(
                        aiMessage,
                        TokenUsage.sum(tokenUsage, response.tokenUsage()),
                        response.finishReason())
                );
            }
        }
    }

    /**
     * Add ChatMessage into memory
     * or if memory is not available, into temporary list tempToolExecutionMessages.
     *
     * @param chatMessage the ChatMessage to add. {@link AiMessage} or {@link ToolExecutionResultMessage}
     */
    private void addChatMessage(ChatMessage chatMessage) {
        if (context.hasChatMemory()) {
            context.chatMemory(memoryId).add(chatMessage);
        } else {
            tempToolExecutionMessages.add(chatMessage);
        }
    }

    /**
     * Get memory messages
     * or if memory is not available, get messages from temporary list tempToolExecutionMessages.
     *
     * @param memoryId the memory id.
     * @return the list of memory messages.
     */
    private List<ChatMessage> getMemoryMessages(Object memoryId) {
        return context.hasChatMemory() ? context.chatMemory(memoryId).messages() : tempToolExecutionMessages;
    }

    @Override
    public void onError(Throwable error) {
        if (errorHandler != null) {
            try {
                errorHandler.accept(error);
            } catch (Exception e) {
                log.error("While handling the following error...", error);
                log.error("...the following error happened", e);
            }
        } else {
            log.warn("Ignored error", error);
        }
    }
}
