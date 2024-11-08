package dev.langchain4j.model.googleai;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static dev.langchain4j.model.googleai.FinishReasonMapper.fromGFinishReasonToFinishReason;
import static dev.langchain4j.model.googleai.PartsAndContentsMapper.fromGPartsToAiMessage;

/**
 * A builder class for constructing streaming responses from Gemini AI model.
 * This class accumulates partial responses and builds a final response.
 */
class GeminiStreamingResponseBuilder {
    private final boolean includeCodeExecutionOutput;
    private final StringBuilder contentBuilder;
    private final List<ToolExecutionRequest> functionCalls;
    private TokenUsage tokenUsage;
    private FinishReason finishReason;

    /**
     * Constructs a new GeminiStreamingResponseBuilder.
     *
     * @param includeCodeExecutionOutput whether to include code execution output in the response
     */
    public GeminiStreamingResponseBuilder(boolean includeCodeExecutionOutput) {
        this.includeCodeExecutionOutput = includeCodeExecutionOutput;
        this.contentBuilder = new StringBuilder();
        this.functionCalls = new ArrayList<>();
    }

    /**
     * Appends a partial response to the builder.
     *
     * @param partialResponse the partial response from Gemini AI
     * @return an Optional containing the text of the partial response, or empty if no valid text
     */
    public Optional<String> append(GeminiGenerateContentResponse partialResponse) {
        if (partialResponse == null) {
            return Optional.empty();
        }

        GeminiCandidate firstCandidate = partialResponse.getCandidates().get(0);

        updateFinishReason(firstCandidate);
        updateTokenUsage(partialResponse.getUsageMetadata());

        GeminiContent content = firstCandidate.getContent();
        if (content == null || content.getParts() == null) {
            return Optional.empty();
        }

        AiMessage message = fromGPartsToAiMessage(content.getParts(), this.includeCodeExecutionOutput);
        updateContentAndFunctionCalls(message);

        return Optional.ofNullable(message.text());
    }

    /**
     * Builds the final response from all accumulated partial responses.
     *
     * @return a Response object containing the final AiMessage, token usage, and finish reason
     */
    public Response<AiMessage> build() {
        AiMessage aiMessage = createAiMessage();
        return Response.from(aiMessage, tokenUsage, finishReason);
    }

    private void updateTokenUsage(GeminiUsageMetadata tokenCounts) {
        this.tokenUsage = new TokenUsage(
            tokenCounts.getPromptTokenCount(),
            tokenCounts.getCandidatesTokenCount(),
            tokenCounts.getTotalTokenCount()
        );
    }

    private void updateFinishReason(GeminiCandidate candidate) {
        if (candidate.getFinishReason() != null) {
            this.finishReason = fromGFinishReasonToFinishReason(candidate.getFinishReason());
        }
    }

    private void updateContentAndFunctionCalls(AiMessage message) {
        Optional.ofNullable(message.text()).ifPresent(contentBuilder::append);
        if (message.hasToolExecutionRequests()) {
            functionCalls.addAll(message.toolExecutionRequests());
        }
    }

    private AiMessage createAiMessage() {
        String text = contentBuilder.toString();
        boolean hasText = !text.isEmpty() && !text.isBlank();
        boolean hasFunctionCall = !functionCalls.isEmpty();

        if (hasText && hasFunctionCall) {
            return new AiMessage(text, functionCalls);
        } else if (hasText) {
            return new AiMessage(text);
        } else if (hasFunctionCall) {
            return new AiMessage(functionCalls);
        }

        throw new RuntimeException("Gemini has responded neither with text nor with a function call.");
    }
}
