package dev.langchain4j.model.googleai;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.model.googleai.FinishReasonMapper.fromGFinishReasonToFinishReason;
import static dev.langchain4j.model.googleai.PartsAndContentsMapper.fromGPartsToAiMessage;
import static dev.langchain4j.model.output.FinishReason.TOOL_EXECUTION;

/**
 * A builder class for constructing streaming responses from Gemini AI model.
 * This class accumulates partial responses and builds a final response.
 */
class GeminiStreamingResponseBuilder {

    private final boolean includeCodeExecutionOutput;
    private final StringBuilder contentBuilder;
    private final List<ToolExecutionRequest> functionCalls;

    private final AtomicReference<String> id = new AtomicReference<>();
    private final AtomicReference<String> modelName = new AtomicReference<>();
    private final AtomicReference<TokenUsage> tokenUsage = new AtomicReference<>();
    private final AtomicReference<FinishReason> finishReason = new AtomicReference<>();

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

        updateId(partialResponse);
        updateModelName(partialResponse);
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
     * Builds the complete response from all accumulated partial responses.
     *
     * @return a Response object containing the complete AiMessage, token usage, and finish reason
     */
    public ChatResponse build() {
        AiMessage aiMessage = createAiMessage();

        FinishReason finishReason = this.finishReason.get();
        if (aiMessage.hasToolExecutionRequests()) {
            finishReason = TOOL_EXECUTION;
        }

        return ChatResponse.builder()
                .aiMessage(aiMessage)
                .metadata(ChatResponseMetadata.builder()
                        .id(id.get())
                        .modelName(modelName.get())
                        .tokenUsage(tokenUsage.get())
                        .finishReason(finishReason)
                        .build())
                .build();
    }

    private void updateId(GeminiGenerateContentResponse response) {
        if (!isNullOrBlank(response.getResponseId())) {
            id.set(response.getResponseId());
        }
    }

    private void updateModelName(GeminiGenerateContentResponse response) {
        if (!isNullOrBlank(response.getModelVersion())) {
            modelName.set(response.getModelVersion());
        }
    }

    private void updateTokenUsage(GeminiUsageMetadata usageMetadata) {
        if (usageMetadata != null) {
            TokenUsage tokenUsage = new TokenUsage(
                    usageMetadata.getPromptTokenCount(),
                    usageMetadata.getCandidatesTokenCount(),
                    usageMetadata.getTotalTokenCount()
            );
            this.tokenUsage.set(tokenUsage);
        }
    }

    private void updateFinishReason(GeminiCandidate candidate) {
        if (candidate.getFinishReason() != null) {
            this.finishReason.set(fromGFinishReasonToFinishReason(candidate.getFinishReason()));
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
        return AiMessage.builder()
                .text(text.isEmpty() ? null : text)
                .toolExecutionRequests(functionCalls)
                .build();
    }
}
