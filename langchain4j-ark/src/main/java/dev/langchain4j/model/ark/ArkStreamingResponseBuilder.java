package dev.langchain4j.model.ark;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;

import static dev.langchain4j.model.ark.ArkHelper.*;

import com.volcengine.ark.runtime.model.Usage;
import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionChunk;

public class ArkStreamingResponseBuilder {
    private final StringBuilder generatedContent = new StringBuilder();

    private Integer inputTokenCount;

    private Integer outputTokenCount;

    private FinishReason finishReason;

    public ArkStreamingResponseBuilder() {
    }

    public String append(ChatCompletionChunk chunk) {
        if (chunk == null) {
            return null;
        }

        Usage usage = chunk.getUsage();
        if (usage != null) {
            inputTokenCount = (int) usage.getPromptTokens();
            outputTokenCount = (int) usage.getCompletionTokens();
        }

        FinishReason finishReason = finishReasonFrom(chunk);
        if (finishReason != null) {
            this.finishReason = finishReason;
            if (!hasAnswer(chunk)) {
                return null;
            }
        }

        String partialContent = answerFrom(chunk);
        if (Utils.isNotNullOrBlank(partialContent)) {
            generatedContent.append(partialContent);
        }

        return partialContent;
    }

    public Response<AiMessage> build() {
        return Response.from(
                AiMessage.from(generatedContent.toString()),
                new TokenUsage(inputTokenCount, outputTokenCount),
                finishReason
        );
    }

    public boolean isFinish() {
        return finishReason != null;
    }
}
