package dev.langchain4j.model.ollama;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.model.ollama.InternalOllamaHelper.chatResponseMetadataFrom;
import static dev.langchain4j.model.ollama.InternalOllamaHelper.toFinishReason;
import static dev.langchain4j.model.ollama.InternalOllamaHelper.toToolExecutionRequests;
import static dev.langchain4j.model.output.FinishReason.TOOL_EXECUTION;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * This class needs to be thread safe because it is called when a streaming result comes back
 * and there is no guarantee that this thread will be the same as the one that initiated the request,
 * in fact it almost certainly won't be.
 */
class OllamaStreamingResponseBuilder {

    private final StringBuffer contentBuilder = new StringBuffer();
    private volatile String modelName;
    private volatile TokenUsage tokenUsage;
    private final List<ToolExecutionRequest> toolExecutionRequests = new CopyOnWriteArrayList<>();

    void append(OllamaChatResponse partialResponse) {
        if (partialResponse == null) {
            return;
        }

        if (modelName == null && partialResponse.getModel() != null) {
            modelName = partialResponse.getModel();
        }

        if (partialResponse.getEvalCount() != null && partialResponse.getPromptEvalCount() != null) {
            this.tokenUsage = new TokenUsage(partialResponse.getPromptEvalCount(), partialResponse.getEvalCount());
        }

        Message message = partialResponse.getMessage();
        if (message == null) {
            return;
        }

        List<ToolCall> toolCalls = message.getToolCalls();
        if (!isNullOrEmpty(toolCalls)) {
            this.toolExecutionRequests.addAll(toToolExecutionRequests(toolCalls));
        }

        String content = message.getContent();
        if (content != null) {
            contentBuilder.append(content);
        }
    }

    ChatResponse build(OllamaChatResponse ollamaChatResponse) {
        if (!isNullOrEmpty(toolExecutionRequests)) {
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from(toolExecutionRequests))
                    .metadata(chatResponseMetadataFrom(modelName, TOOL_EXECUTION, tokenUsage))
                    .build();
        }

        String text = contentBuilder.toString();
        if (text.isEmpty()) {
            return null;
        } else {
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from(text))
                    .metadata(chatResponseMetadataFrom(modelName, toFinishReason(ollamaChatResponse), tokenUsage))
                    .build();
        }
    }
}
