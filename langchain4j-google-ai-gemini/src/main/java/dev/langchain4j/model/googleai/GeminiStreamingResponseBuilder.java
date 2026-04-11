package dev.langchain4j.model.googleai;

import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.model.googleai.FinishReasonMapper.fromGFinishReasonToFinishReason;
import static dev.langchain4j.model.googleai.PartsAndContentsMapper.fromGPartsToAiMessage;
import static dev.langchain4j.model.output.FinishReason.TOOL_EXECUTION;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.googleai.GeminiGenerateContentResponse.GeminiCandidate;
import dev.langchain4j.model.googleai.GeminiGenerateContentResponse.GeminiUsageMetadata;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A builder class for constructing streaming responses from Gemini AI model.
 * This class accumulates partial responses and builds a final response.
 */
class GeminiStreamingResponseBuilder {

    private final boolean includeCodeExecutionOutput;
    private final Boolean returnThinking;
    private final boolean returnServerToolResults;

    private final StringBuilder contentBuilder;
    private final StringBuilder thoughtBuilder;
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();
    private final List<ToolExecutionRequest> functionCalls;
    private final List<GeminiContent.GeminiPart> parts = new ArrayList<>();

    private final AtomicReference<String> id = new AtomicReference<>();
    private final AtomicReference<String> modelName = new AtomicReference<>();
    private final AtomicReference<TokenUsage> tokenUsage = new AtomicReference<>();
    private final AtomicReference<FinishReason> finishReason = new AtomicReference<>();
    private final AtomicReference<GeminiGenerateContentResponse.GeminiUrlContextMetadata> urlContextMetadata =
            new AtomicReference<>();
    private final AtomicReference<GroundingMetadata> groundingMetadata = new AtomicReference<>();

    GeminiStreamingResponseBuilder(
            boolean includeCodeExecutionOutput, Boolean returnThinking, boolean returnServerToolResults) {
        this.includeCodeExecutionOutput = includeCodeExecutionOutput;
        this.returnThinking = returnThinking;
        this.returnServerToolResults = returnServerToolResults;
        this.contentBuilder = new StringBuilder();
        this.thoughtBuilder = new StringBuilder();
        this.functionCalls = new ArrayList<>();
    }

    record TextAndTools(Optional<String> maybeText, Optional<String> maybeThought, List<ToolExecutionRequest> tools) {}

    /**
     * Appends a partial response to the builder.
     *
     * @param partialResponse the partial response from Gemini AI
     * @return an Optional containing the text of the partial response, or empty if no valid text
     */
    TextAndTools append(GeminiGenerateContentResponse partialResponse) {
        if (partialResponse == null) {
            return new TextAndTools(Optional.empty(), Optional.empty(), List.of());
        }

        List<GeminiCandidate> candidates = partialResponse.candidates();
        if (candidates == null || candidates.isEmpty()) {
            return new TextAndTools(Optional.empty(), Optional.empty(), List.of());
        }

        GeminiCandidate firstCandidate = candidates.get(0);

        updateId(partialResponse);
        updateModelName(partialResponse);
        updateFinishReason(firstCandidate);
        updateTokenUsage(partialResponse.usageMetadata());
        updateServerToolMetadata(partialResponse, firstCandidate);

        GeminiContent content = firstCandidate.content();
        if (content == null || content.parts() == null) {
            return new TextAndTools(Optional.empty(), Optional.empty(), List.of());
        }

        AiMessage message = fromGPartsToAiMessage(content.parts(), includeCodeExecutionOutput, returnThinking);
        updateContentAndFunctionCalls(message);
        parts.addAll(content.parts());

        return new TextAndTools(
                Optional.ofNullable(message.text()),
                Optional.ofNullable(message.thinking()),
                message.toolExecutionRequests());
    }

    /**
     * Builds the complete response from all accumulated partial responses.
     *
     * @return a Response object containing the complete AiMessage, token usage, and finish reason
     */
    ChatResponse build() {
        AiMessage aiMessage = createAiMessage();
        return ChatResponse.builder()
                .aiMessage(aiMessage)
                .metadata(GoogleAiGeminiChatResponseMetadata.builder()
                        .id(id.get())
                        .modelName(modelName.get())
                        .tokenUsage(tokenUsage.get())
                        .finishReason(aiMessage.hasToolExecutionRequests() ? TOOL_EXECUTION : finishReason.get())
                        .build())
                .build();
    }

    private void updateId(GeminiGenerateContentResponse response) {
        if (!isNullOrBlank(response.responseId())) {
            id.set(response.responseId());
        }
    }

    private void updateModelName(GeminiGenerateContentResponse response) {
        if (!isNullOrBlank(response.modelVersion())) {
            modelName.set(response.modelVersion());
        }
    }

    private void updateTokenUsage(GeminiUsageMetadata usageMetadata) {
        if (usageMetadata != null) {
            TokenUsage tokenUsage = new TokenUsage(
                    usageMetadata.promptTokenCount(),
                    usageMetadata.candidatesTokenCount(),
                    usageMetadata.totalTokenCount());
            this.tokenUsage.set(tokenUsage);
        }
    }

    private void updateFinishReason(GeminiCandidate candidate) {
        if (candidate.finishReason() != null) {
            this.finishReason.set(fromGFinishReasonToFinishReason(candidate.finishReason()));
        }
    }

    private void updateServerToolMetadata(GeminiGenerateContentResponse response, GeminiCandidate candidate) {
        if (candidate.urlContextMetadata() != null) {
            urlContextMetadata.set(candidate.urlContextMetadata());
        }
        if (response.groundingMetadata() != null) {
            groundingMetadata.set(response.groundingMetadata());
        } else if (candidate.groundingMetadata() != null) {
            groundingMetadata.set(candidate.groundingMetadata());
        }
    }

    private void updateContentAndFunctionCalls(AiMessage message) {
        Optional.ofNullable(message.text()).ifPresent(contentBuilder::append);
        Optional.ofNullable(message.thinking()).ifPresent(thoughtBuilder::append);
        attributes.putAll(message.attributes());
        if (message.hasToolExecutionRequests()) {
            functionCalls.addAll(message.toolExecutionRequests());
        }
    }

    private AiMessage createAiMessage() {
        String text = contentBuilder.toString();
        String thought = thoughtBuilder.toString();
        Map<String, Object> finalAttributes = new ConcurrentHashMap<>(attributes);

        if (returnServerToolResults) {
            List<GoogleAiGeminiServerToolResult> serverToolResults = GeminiServerToolsMapper.extractServerToolResults(
                    parts, urlContextMetadata.get(), groundingMetadata.get());
            if (!serverToolResults.isEmpty()) {
                finalAttributes.put(GeminiServerToolsMapper.SERVER_TOOL_RESULTS_KEY, serverToolResults);
            }
        }

        return AiMessage.builder()
                .text(text.isEmpty() ? null : text)
                .thinking(thought.isEmpty() ? null : thought)
                .toolExecutionRequests(functionCalls)
                .attributes(finalAttributes)
                .build();
    }
}
