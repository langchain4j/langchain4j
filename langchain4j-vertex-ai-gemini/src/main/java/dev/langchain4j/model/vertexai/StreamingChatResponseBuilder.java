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
    private volatile TokenUsage tokenUsage;
    private volatile FinishReason finishReason;

    void append(GenerateContentResponse partialResponse) {
        if (partialResponse == null) {
            return;
        }

        List<Candidate> candidates = partialResponse.getCandidatesList();
        if (candidates.isEmpty() || candidates.get(0) == null) {
            return;
        }

        contentBuilder.append(ResponseHandler.getText(partialResponse));

        if (partialResponse.hasUsageMetadata()) {
            tokenUsage = TokenUsageMapper.map(partialResponse.getUsageMetadata());
        }

        Candidate.FinishReason finishReason = ResponseHandler.getFinishReason(partialResponse);
        if (finishReason != null) {
            this.finishReason = FinishReasonMapper.map(finishReason);
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
