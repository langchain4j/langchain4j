package dev.langchain4j.model.vertexai;

import com.google.cloud.vertexai.api.Candidate;
import com.google.cloud.vertexai.api.GenerateContentResponse;
import com.google.cloud.vertexai.generativeai.preview.ResponseHandler;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;

import java.util.List;

class StreamingChatResponseBuilder {

    private final StringBuffer contentBuilder = new StringBuffer();
    private volatile FinishReason finishReason;
    private volatile TokenUsage tokenUsage;

    void append(GenerateContentResponse partialResponse) {
        if (partialResponse == null) {
            return;
        }

        List<Candidate> candidates = partialResponse.getCandidatesList();
        if (candidates.isEmpty() || candidates.get(0) == null) {
            return;
        }

        Candidate.FinishReason finishReason = ResponseHandler.getFinishReason(partialResponse);
        if (finishReason != null) {
            this.finishReason = FinishReasonMapper.map(finishReason);
        }

        String token = ResponseHandler.getText(partialResponse);
        if (token != null) {
            contentBuilder.append(token);
        }

        if (partialResponse.hasUsageMetadata()) {
            tokenUsage = TokenUsageMapper.map(partialResponse.getUsageMetadata());
        }
    }

    Response<AiMessage> build() {
        return Response.from(
                AiMessage.from(contentBuilder.toString()),
                tokenUsage,
                finishReason
        );
    }
}
