package dev.langchain4j.model.vertexai.gemini;

import com.google.cloud.vertexai.api.*;
import com.google.cloud.vertexai.generativeai.ResponseHandler;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

class StreamingChatResponseBuilder {

    private final StringBuffer contentBuilder = new StringBuffer();

    private final List<FunctionCall> functionCalls = new ArrayList<>();

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

        List<FunctionCall> functionCalls = candidates.stream()
                .map(Candidate::getContent)
                .map(Content::getPartsList)
                .flatMap(List::stream)
                .filter(Part::hasFunctionCall)
                .map(Part::getFunctionCall)
                .collect(Collectors.toList());

        if (!functionCalls.isEmpty()) {
            this.functionCalls.addAll(functionCalls);
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
        if (!functionCalls.isEmpty()) {
            return Response.from(
                AiMessage.from(FunctionCallHelper.fromFunctionCalls(functionCalls)),
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
