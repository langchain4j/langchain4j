package dev.langchain4j.model.vertexai;

import com.google.cloud.vertexai.api.Candidate;
import com.google.cloud.vertexai.api.GenerateContentResponse;
import com.google.cloud.vertexai.generativeai.preview.ResponseHandler;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;

import java.util.List;

public class VertexAiGeminiStreamingChatResponseBuilder {

    private final StringBuffer contentBuilder = new StringBuffer();
    private volatile Candidate.FinishReason geminiFinishReason;
    private int inputTokenCount = 0;
    private int outputTokenCount = 0;
    private int totalTokenCount = 0;

    public void append(GenerateContentResponse partialResponse) {
        if (partialResponse == null) {
            return;
        }

        List<Candidate> candidates = partialResponse.getCandidatesList();
        if (candidates.isEmpty() || candidates.get(0) == null) {
            return;
        }

        Candidate.FinishReason finishReason = ResponseHandler.getFinishReason(partialResponse);
        if (finishReason != null) {
            this.geminiFinishReason = finishReason;
        }

        String token = ResponseHandler.getText(partialResponse);
        if (token != null) {
            contentBuilder.append(token);
        }
        if (partialResponse.hasUsageMetadata()) {
            GenerateContentResponse.UsageMetadata usageMetadata = partialResponse.getUsageMetadata();
            this.inputTokenCount = usageMetadata.getPromptTokenCount();
            this.outputTokenCount = usageMetadata.getCandidatesTokenCount();
            this.totalTokenCount = usageMetadata.getTotalTokenCount();
        }
    }

    public Response<AiMessage> build() {
        String content = contentBuilder.toString();
        if (!content.isEmpty()) {
            return Response.from(
                    AiMessage.from(content),
                    new TokenUsage(inputTokenCount, outputTokenCount, totalTokenCount),
                    VertexAiGeminiFinishReasonMapper.map(geminiFinishReason)
            );
        }
        return null;
    }

}
