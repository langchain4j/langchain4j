package dev.langchain4j.service;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.tool.ToolExecution;
import dev.langchain4j.service.tool.ToolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * Handles response from a language model for AI Service that is streamed token-by-token.
 * Handles both regular (text) responses and responses with the request to execute one or multiple tools.
 */
@Internal
class AiServiceStreamingResponseHandler implements StreamingChatResponseHandler {

    private final Logger log = LoggerFactory.getLogger(AiServiceStreamingResponseHandler.class);

    private final AiServiceContext context;
    private final Object memoryId;

    private final Consumer<String> partialResponseHandler;
    private final Consumer<ToolExecution> toolExecutionHandler;
    private final Consumer<ChatResponse> completeResponseHandler;

    private final Consumer<Throwable> errorHandler;

    private final List<ChatMessage> temporaryMemory;
    private final TokenUsage tokenUsage;

    private final List<ToolSpecification> toolSpecifications;
    private final Map<String, ToolExecutor> toolExecutors;

    AiServiceStreamingResponseHandler(AiServiceContext context,
                                      Object memoryId,
                                      Consumer<String> partialResponseHandler,
                                      Consumer<ToolExecution> toolExecutionHandler,
                                      Consumer<ChatResponse> completeResponseHandler,
                                      Consumer<Throwable> errorHandler,
                                      List<ChatMessage> temporaryMemory,
                                      TokenUsage tokenUsage,
                                      List<ToolSpecification> toolSpecifications,
                                      Map<String, ToolExecutor> toolExecutors) {
        this.context = ensureNotNull(context, "context");
        this.memoryId = ensureNotNull(memoryId, "memoryId");

        this.partialResponseHandler = ensureNotNull(partialResponseHandler, "partialResponseHandler");
        this.completeResponseHandler = completeResponseHandler;
        this.toolExecutionHandler = toolExecutionHandler;
        this.errorHandler = errorHandler;

        this.temporaryMemory = new ArrayList<>(temporaryMemory);
        this.tokenUsage = ensureNotNull(tokenUsage, "tokenUsage");

        this.toolSpecifications = copy(toolSpecifications);
        this.toolExecutors = copy(toolExecutors);
    }

    @Override
    public void onPartialResponse(String partialResponse) {
        partialResponseHandler.accept(partialResponse);
    }

    @Override
    public void onCompleteResponse(ChatResponse completeResponse) {

        AiMessage aiMessage = completeResponse.aiMessage();
        addToMemory(aiMessage);

        if (aiMessage.hasToolExecutionRequests()) {
            for (ToolExecutionRequest toolExecutionRequest : aiMessage.toolExecutionRequests()) {
                String toolName = toolExecutionRequest.name();
                ToolExecutor toolExecutor = toolExecutors.get(toolName);
                String toolExecutionResult = toolExecutor.execute(toolExecutionRequest, memoryId);
                ToolExecutionResultMessage toolExecutionResultMessage = ToolExecutionResultMessage.from(
                        toolExecutionRequest,
                        toolExecutionResult
                );
                addToMemory(toolExecutionResultMessage);

                if (toolExecutionHandler != null) {
                    ToolExecution toolExecution = ToolExecution.builder()
                            .request(toolExecutionRequest)
                            .result(toolExecutionResult)
                            .build();
                    toolExecutionHandler.accept(toolExecution);
                }
            }

            ChatRequest chatRequest = ChatRequest.builder()
                    .messages(messagesToSend(memoryId))
                    .toolSpecifications(toolSpecifications)
                    .build();

            StreamingChatResponseHandler handler = new AiServiceStreamingResponseHandler(
                    context,
                    memoryId,
                    partialResponseHandler,
                    toolExecutionHandler,
                    completeResponseHandler,
                    errorHandler,
                    temporaryMemory,
                    TokenUsage.sum(tokenUsage, completeResponse.metadata().tokenUsage()),
                    toolSpecifications,
                    toolExecutors
            );

            context.streamingChatModel.chat(chatRequest, handler);
        } else {
            if (completeResponseHandler != null) {
                ChatResponse finalChatResponse = ChatResponse.builder()
                        .aiMessage(aiMessage)
                        .metadata(completeResponse.metadata().toBuilder()
                                .tokenUsage(tokenUsage.add(completeResponse.metadata().tokenUsage()))
                                .build())
                        .build();
                completeResponseHandler.accept(finalChatResponse);
            }
        }
    }

    private void addToMemory(ChatMessage chatMessage) {
        if (context.hasChatMemory()) {
            context.chatMemoryService.getOrCreateChatMemory(memoryId).add(chatMessage);
        } else {
            temporaryMemory.add(chatMessage);
        }
    }

    private List<ChatMessage> messagesToSend(Object memoryId) {
        return context.hasChatMemory()
                ? context.chatMemoryService.getOrCreateChatMemory(memoryId).messages()
                : temporaryMemory;
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
