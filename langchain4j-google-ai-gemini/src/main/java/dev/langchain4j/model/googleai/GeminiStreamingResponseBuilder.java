package dev.langchain4j.model.googleai;

import static dev.langchain4j.data.message.AiMessage.GENERATED_IMAGES_KEY;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.model.googleai.FinishReasonMapper.fromGFinishReasonToFinishReason;
import static dev.langchain4j.model.googleai.PartsAndContentsMapper.fromGPartsToAiMessage;
import static dev.langchain4j.model.output.FinishReason.TOOL_EXECUTION;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.googleai.GeminiGenerateContentResponse.GeminiCandidate;
import dev.langchain4j.model.googleai.GeminiGenerateContentResponse.GeminiUsageMetadata;
import dev.langchain4j.model.googleai.GroundingMetadata.GroundingChunk;
import dev.langchain4j.model.googleai.GroundingMetadata.GroundingSupport;
import dev.langchain4j.model.googleai.GroundingMetadata.RetrievalMetadata;
import dev.langchain4j.model.googleai.GroundingMetadata.SearchEntryPoint;
import dev.langchain4j.model.googleai.UrlContextMetadata.UrlMetadata;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A builder class for constructing streaming responses from Gemini AI model.
 * This class accumulates partial responses and builds a final response.
 */
class GeminiStreamingResponseBuilder {

    private final boolean includeCodeExecutionOutput;
    private final Boolean returnThinking;

    private final StringBuilder contentBuilder;
    private final StringBuilder thoughtBuilder;
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();
    private final List<ToolExecutionRequest> functionCalls;

    private final AtomicReference<String> id = new AtomicReference<>();
    private final AtomicReference<String> modelName = new AtomicReference<>();
    private final AtomicReference<TokenUsage> tokenUsage = new AtomicReference<>();
    private final AtomicReference<FinishReason> finishReason = new AtomicReference<>();
    private final GroundingMetadataAccumulator groundingMetadata = new GroundingMetadataAccumulator();
    private final UrlContextMetadataAccumulator urlContextMetadata = new UrlContextMetadataAccumulator();

    GeminiStreamingResponseBuilder(boolean includeCodeExecutionOutput, Boolean returnThinking) {
        this.includeCodeExecutionOutput = includeCodeExecutionOutput;
        this.returnThinking = returnThinking;
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
        updateGroundingMetadata(partialResponse, firstCandidate);
        updateUrlContextMetadata(firstCandidate);

        GeminiContent content = firstCandidate.content();
        if (content == null || content.parts() == null) {
            return new TextAndTools(Optional.empty(), Optional.empty(), List.of());
        }

        AiMessage message = fromGPartsToAiMessage(content.parts(), includeCodeExecutionOutput, returnThinking);
        updateContentAndFunctionCalls(message);

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
                        .groundingMetadata(groundingMetadata.build())
                        .urlContextMetadata(urlContextMetadata.build())
                        .build())
                .build();
    }

    private void updateId(GeminiGenerateContentResponse response) {
        if (!isNullOrBlank(response.responseId())) {
            id.set(response.responseId());
        }
    }

    private void updateGroundingMetadata(GeminiGenerateContentResponse response, GeminiCandidate candidate) {
        groundingMetadata.merge(
                response.groundingMetadata() != null ? response.groundingMetadata() : candidate.groundingMetadata());
    }

    private void updateUrlContextMetadata(GeminiCandidate candidate) {
        urlContextMetadata.merge(BaseGeminiChatModel.toUrlContextMetadata(candidate.urlContextMetadata()));
    }

    private void updateModelName(GeminiGenerateContentResponse response) {
        if (!isNullOrBlank(response.modelVersion())) {
            modelName.set(response.modelVersion());
        }
    }

    private void updateTokenUsage(GeminiUsageMetadata usageMetadata) {
        if (usageMetadata != null) {
            TokenUsage tokenUsage = GoogleAiGeminiTokenUsage.builder()
                    .inputTokenCount(usageMetadata.promptTokenCount())
                    .outputTokenCount(usageMetadata.candidatesTokenCount())
                    .totalTokenCount(usageMetadata.totalTokenCount())
                    .cachedContentTokenCount(usageMetadata.cachedContentTokenCount())
                    .thoughtsTokenCount(usageMetadata.thoughtsTokenCount())
                    .build();
            this.tokenUsage.set(tokenUsage);
        }
    }

    private void updateFinishReason(GeminiCandidate candidate) {
        if (candidate.finishReason() != null) {
            this.finishReason.set(fromGFinishReasonToFinishReason(candidate.finishReason()));
        }
    }

    private void updateContentAndFunctionCalls(AiMessage message) {
        Optional.ofNullable(message.text()).ifPresent(contentBuilder::append);
        Optional.ofNullable(message.thinking()).ifPresent(thoughtBuilder::append);
        mergeAttributes(message.attributes());
        if (message.hasToolExecutionRequests()) {
            functionCalls.addAll(message.toolExecutionRequests());
        }
    }

    private void mergeAttributes(Map<String, Object> partialAttributes) {
        partialAttributes.forEach((key, value) -> {
            if (GENERATED_IMAGES_KEY.equals(key)) {
                attributes.merge(key, value, GeminiStreamingResponseBuilder::concatenate);
            } else {
                attributes.put(key, value);
            }
        });
    }

    private static Object concatenate(Object existing, Object added) {
        List<Object> concatenated = new ArrayList<>((List<?>) existing);
        concatenated.addAll((List<?>) added);
        return concatenated;
    }

    private AiMessage createAiMessage() {
        String text = contentBuilder.toString();
        String thought = thoughtBuilder.toString();

        return AiMessage.builder()
                .text(text.isEmpty() ? null : text)
                .thinking(thought.isEmpty() ? null : thought)
                .toolExecutionRequests(functionCalls)
                .attributes(attributes)
                .build();
    }

    /**
     * Accumulates the grounding metadata of every streamed chunk into a single value.
     *
     * <p>A chunk either repeats what earlier chunks already reported or adds to it, and the response does not say
     * which, so an entry already seen is skipped and a new one is appended. Because a {@link GroundingSupport} points
     * at {@link GroundingChunk}s by their position, the positions reported by a chunk that contributes new sources are
     * translated into positions in the accumulated list.
     */
    private static class GroundingMetadataAccumulator {

        private final Map<GroundingChunk, Integer> chunkPositions = new HashMap<>();

        private boolean reported;
        private List<GroundingChunk> chunks;
        private Set<GroundingSupport> supports;
        private Set<String> webSearchQueries;
        private SearchEntryPoint searchEntryPoint;
        private RetrievalMetadata retrievalMetadata;
        private String googleMapsWidgetContextToken;

        void merge(GroundingMetadata metadata) {
            if (metadata == null) {
                return;
            }
            reported = true;

            int[] positions = mergeChunks(metadata.groundingChunks());
            mergeSupports(metadata.groundingSupports(), positions);

            if (metadata.webSearchQueries() != null) {
                if (webSearchQueries == null) {
                    webSearchQueries = new LinkedHashSet<>();
                }
                webSearchQueries.addAll(metadata.webSearchQueries());
            }
            if (metadata.searchEntryPoint() != null) {
                searchEntryPoint = metadata.searchEntryPoint();
            }
            if (metadata.retrievalMetadata() != null) {
                retrievalMetadata = metadata.retrievalMetadata();
            }
            if (metadata.googleMapsWidgetContextToken() != null) {
                googleMapsWidgetContextToken = metadata.googleMapsWidgetContextToken();
            }
        }

        GroundingMetadata build() {
            if (!reported) {
                return null;
            }
            return GroundingMetadata.builder()
                    .groundingChunks(chunks == null ? null : new ArrayList<>(chunks))
                    .groundingSupports(supports == null ? null : new ArrayList<>(supports))
                    .webSearchQueries(webSearchQueries == null ? null : new ArrayList<>(webSearchQueries))
                    .searchEntryPoint(searchEntryPoint)
                    .retrievalMetadata(retrievalMetadata)
                    .googleMapsWidgetContextToken(googleMapsWidgetContextToken)
                    .build();
        }

        private int[] mergeChunks(List<GroundingChunk> reportedChunks) {
            if (reportedChunks == null) {
                return new int[0];
            }
            if (chunks == null) {
                chunks = new ArrayList<>();
            }
            int[] positions = new int[reportedChunks.size()];
            for (int i = 0; i < reportedChunks.size(); i++) {
                GroundingChunk chunk = reportedChunks.get(i);
                Integer position = chunkPositions.get(chunk);
                if (position == null) {
                    position = chunks.size();
                    chunks.add(chunk);
                    chunkPositions.put(chunk, position);
                }
                positions[i] = position;
            }
            return positions;
        }

        private void mergeSupports(List<GroundingSupport> reportedSupports, int[] positions) {
            if (reportedSupports == null) {
                return;
            }
            if (supports == null) {
                supports = new LinkedHashSet<>();
            }
            for (GroundingSupport support : reportedSupports) {
                supports.add(withAccumulatedPositions(support, positions));
            }
        }

        private static GroundingSupport withAccumulatedPositions(GroundingSupport support, int[] positions) {
            if (support == null || support.groundingChunkIndices() == null) {
                return support;
            }
            List<Integer> indices = support.groundingChunkIndices().stream()
                    .map(index -> accumulatedPosition(index, positions))
                    .toList();
            return new GroundingSupport(indices, support.confidenceScores(), support.segment());
        }

        private static Integer accumulatedPosition(Integer index, int[] positions) {
            if (index == null || index < 0 || index >= positions.length) {
                return index;
            }
            return positions[index];
        }
    }

    /**
     * Accumulates the URL context metadata of every streamed chunk into a single value, keeping one entry per
     * retrieved URL so that a chunk repeating a URL only updates the retrieval status reported for it.
     */
    private static class UrlContextMetadataAccumulator {

        private Map<String, UrlMetadata> urlMetadataByUrl;

        void merge(UrlContextMetadata metadata) {
            if (metadata == null || metadata.urlMetadata() == null) {
                return;
            }
            if (urlMetadataByUrl == null) {
                urlMetadataByUrl = new LinkedHashMap<>();
            }
            for (UrlMetadata urlMetadata : metadata.urlMetadata()) {
                if (urlMetadata != null) {
                    urlMetadataByUrl.put(urlMetadata.retrievedUrl(), urlMetadata);
                }
            }
        }

        UrlContextMetadata build() {
            if (urlMetadataByUrl == null) {
                return null;
            }
            return new UrlContextMetadata(new ArrayList<>(urlMetadataByUrl.values()));
        }
    }
}
