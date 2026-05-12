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

    private final List<Part> functionCallParts = new ArrayList<>();

    private final boolean returnThinking;

    private volatile TokenUsage tokenUsage;
    private volatile FinishReason finishReason;

    StreamingChatResponseBuilder() {
        this(false);
    }

    StreamingChatResponseBuilder(boolean returnThinking) {
        this.returnThinking = returnThinking;
    }

    record TextAndFunctions(String text, List<FunctionCall> functionCalls) {}

    TextAndFunctions append(GenerateContentResponse partialResponse) {
        if (partialResponse == null) {
            return new TextAndFunctions(null, List.of());
        }

        List<Candidate> candidates = partialResponse.getCandidatesList();
        if (candidates.isEmpty() || candidates.get(0) == null) {
            return new TextAndFunctions(null, List.of());
        }

        String text = ResponseHandler.getText(partialResponse);
        contentBuilder.append(text);

        List<Part> functionCallParts = candidates.stream()
                .map(Candidate::getContent)
                .map(Content::getPartsList)
                .flatMap(List::stream)
                .filter(Part::hasFunctionCall)
                .collect(Collectors.toList());

        if (!functionCallParts.isEmpty()) {
            this.functionCallParts.addAll(functionCallParts);
        }

        List<FunctionCall> functionCalls =
                functionCallParts.stream().map(Part::getFunctionCall).collect(Collectors.toList());

        if (partialResponse.hasUsageMetadata()) {
            tokenUsage = TokenUsageMapper.map(partialResponse.getUsageMetadata());
        }

        Candidate.FinishReason finishReason = ResponseHandler.getFinishReason(partialResponse);
        if (finishReason != null) {
            this.finishReason = FinishReasonMapper.map(finishReason);
        }

        return new TextAndFunctions(text, functionCalls);
    }

    Response<AiMessage> build() {
        if (!functionCallParts.isEmpty()) {
            return Response.from(
                    FunctionCallHelper.fromFunctionCallParts(functionCallParts, returnThinking),
                    tokenUsage,
                    finishReason);
        } else {
            return Response.from(AiMessage.from(contentBuilder.toString()), tokenUsage, finishReason);
        }
    }
}
