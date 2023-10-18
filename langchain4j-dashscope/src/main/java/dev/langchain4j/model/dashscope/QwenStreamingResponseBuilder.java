package dev.langchain4j.model.dashscope;

import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.aigc.generation.GenerationUsage;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;

import static dev.langchain4j.model.dashscope.QwenHelper.answerFrom;
import static dev.langchain4j.model.dashscope.QwenHelper.finishReasonFrom;

public class QwenStreamingResponseBuilder {
    private String generatedContent = "";

    private Integer inputTokenCount;

    private Integer outputTokenCount;

    private FinishReason finishReason;

    public QwenStreamingResponseBuilder() {}

    public String append(GenerationResult partialResponse) {
        if (partialResponse == null) {
            return null;
        }

        GenerationUsage usage = partialResponse.getUsage();
        if (usage != null) {
            inputTokenCount = usage.getInputTokens();
            outputTokenCount = usage.getOutputTokens();
        }

        FinishReason finishReason = finishReasonFrom(partialResponse);
        if (finishReason != null) {
            this.finishReason = finishReason;
        }

        String partialContent = answerFrom(partialResponse);
        String delta = null;
        if (partialContent.length() > generatedContent.length() ) {
            delta = partialContent.substring(generatedContent.length());
            generatedContent = partialContent;
        }

        return delta;
    }

    public Response<AiMessage> build() {
        return Response.from(
                AiMessage.from(generatedContent),
                new TokenUsage(inputTokenCount, outputTokenCount),
                finishReason
        );
    }
}
