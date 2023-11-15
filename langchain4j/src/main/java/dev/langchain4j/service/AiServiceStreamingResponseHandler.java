package dev.langchain4j.service;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolExecutor;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * Handles response from a language model for AI Service that is streamed token-by-token.
 * Handles both regular (text) responses and responses with the request to execute a tool.
 */
class AiServiceStreamingResponseHandler implements StreamingResponseHandler<AiMessage> {

    private final Logger log = LoggerFactory.getLogger(AiServiceStreamingResponseHandler.class);

    private final AiServiceContext context;
    private final Object memoryId;

    private final Consumer<String> tokenHandler;
    private final Consumer<Response<AiMessage>> completionHandler;
    private final Consumer<Throwable> errorHandler;

    private final TokenUsage tokenUsage;

    AiServiceStreamingResponseHandler(AiServiceContext context,
                                      Object memoryId,
                                      Consumer<String> tokenHandler,
                                      Consumer<Response<AiMessage>> completionHandler,
                                      Consumer<Throwable> errorHandler,
                                      TokenUsage tokenUsage) {
        this.context = ensureNotNull(context, "context");
        this.memoryId = ensureNotNull(memoryId, "memoryId");

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

        if (context.hasChatMemory()) {
            context.chatMemory(memoryId).add(response.content());
        }

        ToolExecutionRequest toolExecutionRequest = response.content().toolExecutionRequest();
        if (toolExecutionRequest != null) {
            ToolExecutor toolExecutor = context.toolExecutors.get(toolExecutionRequest.name());
            String toolExecutionResult = toolExecutor.execute(toolExecutionRequest);
            ToolExecutionResultMessage toolExecutionResultMessage = ToolExecutionResultMessage.from(
                    toolExecutionRequest.name(),
                    toolExecutionResult
            );

            context.chatMemory(memoryId).add(toolExecutionResultMessage);

            context.streamingChatModel.generate(
                    context.chatMemory(memoryId).messages(),
                    context.toolSpecifications,
                    new AiServiceStreamingResponseHandler(
                            context,
                            memoryId,
                            tokenHandler,
                            completionHandler,
                            errorHandler,
                            tokenUsage.add(response.tokenUsage())
                    )
            );
        } else {
            if (completionHandler != null) {
                completionHandler.accept(Response.from(
                        response.content(),
                        tokenUsage.add(response.tokenUsage()),
                        response.finishReason())
                );
            }
        }
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
