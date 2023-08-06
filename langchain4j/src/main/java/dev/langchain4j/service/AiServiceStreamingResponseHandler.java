package dev.langchain4j.service;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolExecutor;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.StreamingResponseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

import static dev.langchain4j.data.message.AiMessage.aiMessage;
import static dev.langchain4j.data.message.ToolExecutionResultMessage.toolExecutionResultMessage;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * Handles response from LLM for AI Service that is streamed token-by-token.
 * Handles both regular (text) responses and responses with the request to execute a tool.
 */
class AiServiceStreamingResponseHandler implements StreamingResponseHandler {

    private final Logger log = LoggerFactory.getLogger(AiServiceStreamingResponseHandler.class);

    private final AiServiceContext context;
    private final ChatMemory chatMemory;

    private final Consumer<String> tokenHandler;
    private final Runnable completionHandler;
    private final Consumer<Throwable> errorHandler;

    private final StringBuilder answerBuilder;
    private final StringBuilder toolNameBuilder;
    private final StringBuilder toolArgumentsBuilder;

    AiServiceStreamingResponseHandler(AiServiceContext context,
                                      ChatMemory chatMemory,
                                      Consumer<String> tokenHandler,
                                      Runnable completionHandler,
                                      Consumer<Throwable> errorHandler) {
        this.context = ensureNotNull(context, "context");
        this.chatMemory = chatMemory;

        this.tokenHandler = ensureNotNull(tokenHandler, "tokenHandler");
        this.completionHandler = completionHandler;
        this.errorHandler = errorHandler;

        this.answerBuilder = new StringBuilder();
        this.toolNameBuilder = new StringBuilder();
        this.toolArgumentsBuilder = new StringBuilder();
    }

    @Override
    public void onNext(String partialResult) {
        answerBuilder.append(partialResult);
        tokenHandler.accept(partialResult);
    }

    @Override
    public void onToolName(String name) {
        toolNameBuilder.append(name);
    }

    @Override
    public void onToolArguments(String arguments) {
        toolArgumentsBuilder.append(arguments);
    }

    @Override
    public void onComplete() {

        String toolName = toolNameBuilder.toString();

        if (toolName.isEmpty()) {
            if (chatMemory != null) {
                chatMemory.add(aiMessage(answerBuilder.toString()));
            }
            if (completionHandler != null) {
                completionHandler.run();
            }
        } else {

            ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                    .name(toolName)
                    .arguments(toolArgumentsBuilder.toString())
                    .build();

            chatMemory.add(aiMessage(toolExecutionRequest));

            ToolExecutor toolExecutor = context.toolExecutors.get(toolName); // TODO what if no such tool?
            String toolExecutionResult = toolExecutor.execute(toolExecutionRequest);
            ToolExecutionResultMessage toolExecutionResultMessage
                    = toolExecutionResultMessage(toolExecutionRequest.name(), toolExecutionResult);

            chatMemory.add(toolExecutionResultMessage);

            // TODO what if there are multiple tool executions in a row? (for the future)
            context.streamingChatLanguageModel.sendMessages(
                    chatMemory.messages(),
                    new AiServiceStreamingResponseHandler(context, chatMemory, tokenHandler, completionHandler, errorHandler)
            );
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