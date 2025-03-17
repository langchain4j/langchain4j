package dev.langchain4j.model.ollama;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;

/**
 * This class needs to be thread safe because it is called when a streaming result comes back
 * and there is no guarantee that this thread will be the same as the one that initiated the request,
 * in fact it almost certainly won't be.
 */
class OllamaStreamingResponseBuilder {

    private StringBuffer contentBuilder = new StringBuffer();
    private volatile TokenUsage tokenUsage;
    private volatile List<ToolExecutionRequest> toolExecutionRequests = new CopyOnWriteArrayList<>();

    void append(ChatResponse partialResponse) {
        if (partialResponse == null) {
            return;
        }

        if (partialResponse.getEvalCount() != null && partialResponse.getPromptEvalCount() != null) {
            this.tokenUsage = new TokenUsage(
                    partialResponse.getPromptEvalCount(),
                    partialResponse.getEvalCount()
            );
        }

        Message message = partialResponse.getMessage();
        if (message == null) {
            return;
        }

        List<ToolCall> toolCalls = message.getToolCalls();
        if (!isNullOrEmpty(toolCalls)) {
            this.toolExecutionRequests.addAll(OllamaMessagesUtils.toToolExecutionRequests(toolCalls));
        }

        String content = message.getContent();
        if (content != null) {
            contentBuilder.append(content);
        }
    }

    Response<AiMessage> build() {
        if (!isNullOrEmpty(toolExecutionRequests)) {
            return Response.from(AiMessage.from(toolExecutionRequests), tokenUsage);
        }

        String text = contentBuilder.toString();
        if (text.isEmpty()) {
            return null;
        } else {
            return Response.from(AiMessage.from(text), tokenUsage);
        }
    }
}
