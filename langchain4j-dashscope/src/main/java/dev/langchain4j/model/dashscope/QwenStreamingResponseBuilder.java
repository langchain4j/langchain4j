package dev.langchain4j.model.dashscope;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.dashscope.extension.aigc.generation.GenerationResult;
import dev.langchain4j.model.dashscope.extension.aigc.multimodalconversation.MultiModalConversationResult;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;

public class QwenStreamingResponseBuilder {
    private final StringBuilder generatedContent = new StringBuilder();
    private Integer inputTokenCount;
    private Integer outputTokenCount;
    private FinishReason finishReason;

    public QwenStreamingResponseBuilder() {
    }

    public String append(GenerationResult partialResponse) {
        if (partialResponse == null) {
            return null;
        }

        Response<AiMessage> response = QwenHelper.responseFrom(partialResponse);

        TokenUsage usage = response.tokenUsage();
        if (usage != null) {
            inputTokenCount = usage.inputTokenCount();
            outputTokenCount = usage.outputTokenCount();
        }

        FinishReason finishReason = response.finishReason();
        if (finishReason != null) {
            this.finishReason = finishReason;
        }

        String partialContent = response.content().text();
        generatedContent.append(partialContent);

        return partialContent;
    }

    public String append(MultiModalConversationResult partialResponse) {
        if (partialResponse == null) {
            return null;
        }

        Response<AiMessage> response = QwenHelper.responseFrom(partialResponse);

        TokenUsage usage = response.tokenUsage();
        if (usage != null) {
            inputTokenCount = usage.inputTokenCount();
            outputTokenCount = usage.outputTokenCount();
        }

        FinishReason finishReason = response.finishReason();
        if (finishReason != null) {
            this.finishReason = finishReason;
        }

        String partialContent = response.content().text();
        generatedContent.append(partialContent);

        return partialContent;
    }

    public Response<AiMessage> build() {
        return Response.from(
                AiMessage.from(generatedContent.toString()),
                new TokenUsage(inputTokenCount, outputTokenCount),
                finishReason
        );
    }
}