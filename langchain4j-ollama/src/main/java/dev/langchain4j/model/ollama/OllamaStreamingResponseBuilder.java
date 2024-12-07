package dev.langchain4j.model.ollama;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

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

        if (partialResponse.getMessage().getToolCalls() != null && !partialResponse.getMessage().getToolCalls().isEmpty()) {
            this.toolExecutionRequests.addAll(OllamaMessagesUtils.toToolExecutionRequest(partialResponse.getMessage().getToolCalls()));
        }

        String content = partialResponse.getMessage().getContent();
        if (content != null) {
            contentBuilder.append(content);
        }
    }

    Response<AiMessage> build() {
        if (toolExecutionRequests != null && !toolExecutionRequests.isEmpty()) {
            return Response.from(
                    AiMessage.aiMessage(toolExecutionRequests), tokenUsage);

        }
        if (contentBuilder.toString().isEmpty()) {
            return null;
        }
        return Response.from(
                AiMessage.from(contentBuilder.toString()),
                tokenUsage
        );
    }
}
