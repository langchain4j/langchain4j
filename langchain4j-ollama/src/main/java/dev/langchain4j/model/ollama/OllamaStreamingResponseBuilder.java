package dev.langchain4j.model.ollama;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;

/**
 * This class needs to be thread safe because it is called when a streaming result comes back
 * and there is no guarantee that this thread will be the same as the one that initiated the request,
 * in fact it almost certainly won't be.
 */
class OllamaStreamingResponseBuilder {

    private StringBuffer contentBuilder = new StringBuffer();
    private volatile TokenUsage tokenUsage;

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

        String content = partialResponse.getMessage().getContent();
        if (content != null) {
            contentBuilder.append(content);
        }
    }

    Response<AiMessage> build() {
        if (contentBuilder.toString().isEmpty()) {
            return null;
        }
        return Response.from(
                AiMessage.from(contentBuilder.toString()),
                tokenUsage
        );
    }
}
