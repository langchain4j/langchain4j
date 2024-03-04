package dev.langchain4j.model.vertexai;

import com.google.cloud.vertexai.api.*;
import com.google.cloud.vertexai.generativeai.ResponseHandler;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;

import java.util.List;
import java.util.Optional;

class StreamingChatResponseBuilder {

    private final StringBuffer contentBuilder = new StringBuffer();

    private boolean hasFunctionCall = false;
    private FunctionCall functionCall;

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

        Optional<Part> functionCallPart = candidates.stream()
                .map(Candidate::getContent)
                .map(Content::getPartsList)
                .flatMap(List::stream)
                .filter(Part::hasFunctionCall)
                .findFirst();

        if (functionCallPart.isPresent()) {
            this.hasFunctionCall = true;
            this.functionCall = functionCallPart.get().getFunctionCall();
        } else {
            contentBuilder.append(ResponseHandler.getText(partialResponse));
        }

        if (partialResponse.hasUsageMetadata()) {
            tokenUsage = TokenUsageMapper.map(partialResponse.getUsageMetadata());
        }

        Candidate.FinishReason finishReason = ResponseHandler.getFinishReason(partialResponse);
        if (finishReason != null) {
            this.finishReason = FinishReasonMapper.map(finishReason);
        }
    }

    Response<AiMessage> build() {
        if (hasFunctionCall) {
            return Response.from(
                AiMessage.from(FunctionCallHelper.fromFunctionCall(this.functionCall)),
                tokenUsage,
                finishReason
            );
        } else {
            return Response.from(
                AiMessage.from(contentBuilder.toString()),
                tokenUsage,
                finishReason
            );
        }
    }
}
