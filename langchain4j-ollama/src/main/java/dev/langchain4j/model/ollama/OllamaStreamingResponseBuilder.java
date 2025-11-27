package dev.langchain4j.model.ollama;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.model.ollama.InternalOllamaHelper.chatResponseMetadataFrom;
import static dev.langchain4j.model.ollama.InternalOllamaHelper.toFinishReason;
import static dev.langchain4j.model.output.FinishReason.TOOL_EXECUTION;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.internal.ToolCallBuilder;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;

/**
 * This class needs to be thread safe because it is called when a streaming result comes back
 * and there is no guarantee that this thread will be the same as the one that initiated the request,
 * in fact it almost certainly won't be.
 */
class OllamaStreamingResponseBuilder {

    private final StringBuffer contentBuilder = new StringBuffer();

    private final boolean returnThinking;
    private final StringBuffer thinkingBuilder;

    private final ToolCallBuilder toolCallBuilder;

    private volatile String modelName;
    private volatile TokenUsage tokenUsage;

    OllamaStreamingResponseBuilder(ToolCallBuilder toolCallBuilder, boolean returnThinking) {
        this.toolCallBuilder = toolCallBuilder;
        this.returnThinking = returnThinking;
        if (returnThinking) {
            this.thinkingBuilder = new StringBuffer();
        } else {
            this.thinkingBuilder = null;
        }
    }

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

        String content = message.getContent();
        if (content != null) {
            contentBuilder.append(content);
        }

        String thinking = message.getThinking();
        if (returnThinking && thinking != null) {
            thinkingBuilder.append(thinking);
        }
    }

    ChatResponse build(OllamaChatResponse ollamaChatResponse) {
        String text = contentBuilder.toString();

        String thinking = null;
        if (returnThinking) {
            thinking = thinkingBuilder.toString();
        }

        if (toolCallBuilder.hasRequests()) {
            return ChatResponse.builder()
                    .aiMessage(AiMessage.builder()
                            .text(isNullOrEmpty(text) ? null : text)
                            .thinking(isNullOrEmpty(thinking) ? null : thinking)
                            .toolExecutionRequests(toolCallBuilder.allRequests())
                            .build())
                    .metadata(chatResponseMetadataFrom(modelName, TOOL_EXECUTION, tokenUsage))
                    .build();
        }

        return ChatResponse.builder()
                .aiMessage(AiMessage.builder()
                        .text(isNullOrEmpty(text) ? null : text)
                        .thinking(isNullOrEmpty(thinking) ? null : thinking)
                        .build())
                .metadata(chatResponseMetadataFrom(modelName, toFinishReason(ollamaChatResponse), tokenUsage))
                .build();
    }
}
