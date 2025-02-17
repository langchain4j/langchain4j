package dev.langchain4j.model.googleai;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static dev.langchain4j.model.chat.policy.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

@Experimental
@Slf4j
public class GoogleAiEmbeddingModel implements EmbeddingModel {
    private static final int MAX_NUMBER_OF_SEGMENTS_PER_BATCH = 100;

    private final GeminiService geminiService;

    private final String modelName;
    private final String apiKey;
    private final Integer maxRetries;
    private final TaskType taskType;
    private final String titleMetadataKey;
    private final Integer outputDimensionality;

    @Builder
    public GoogleAiEmbeddingModel(
            String modelName,
            String apiKey,
            Integer maxRetries,
            TaskType taskType,
            String titleMetadataKey,
            Integer outputDimensionality,
            Duration timeout,
            Boolean logRequestsAndResponses
    ) {

        this.modelName = ensureNotBlank(modelName, "modelName");
        this.apiKey = ensureNotBlank(apiKey, "apiKey");

        this.maxRetries = getOrDefault(maxRetries, 3);

        this.taskType = taskType;
        this.titleMetadataKey = getOrDefault(titleMetadataKey, "title");

        this.outputDimensionality = outputDimensionality;

        Duration timeout1 = getOrDefault(timeout, Duration.ofSeconds(60));

        boolean logRequestsAndResponses1 = logRequestsAndResponses != null && logRequestsAndResponses;

        this.geminiService = new GeminiService(logRequestsAndResponses1 ? log : null, timeout1);
    }

    @Override
    public Response<Embedding> embed(TextSegment textSegment) {
        GoogleAiEmbeddingRequest embeddingRequest = getGoogleAiEmbeddingRequest(textSegment);

        GoogleAiEmbeddingResponse geminiResponse = withRetry(() -> this.geminiService.embed(this.modelName, this.apiKey, embeddingRequest), this.maxRetries);

        if (geminiResponse != null) {
            return Response.from(Embedding.from(geminiResponse.getEmbedding().getValues()));
        } else {
            throw new RuntimeException("Gemini embedding response was null (embed)");
        }
    }

    @Override
    public Response<Embedding> embed(String text) {
        return embed(TextSegment.from(text));
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        List<GoogleAiEmbeddingRequest> embeddingRequests = textSegments.stream()
                .map(this::getGoogleAiEmbeddingRequest)
                .collect(Collectors.toList());

        List<Embedding> allEmbeddings = new ArrayList<>();
        int numberOfEmbeddings = embeddingRequests.size();
        int numberOfBatches = 1 + numberOfEmbeddings / MAX_NUMBER_OF_SEGMENTS_PER_BATCH;

        for (int i = 0; i < numberOfBatches; i++) {
            int startIndex = MAX_NUMBER_OF_SEGMENTS_PER_BATCH * i;
            int lastIndex = Math.min(startIndex + MAX_NUMBER_OF_SEGMENTS_PER_BATCH, numberOfEmbeddings);

            if (startIndex >= numberOfEmbeddings) break;

            GoogleAiBatchEmbeddingRequest batchEmbeddingRequest = new GoogleAiBatchEmbeddingRequest();
            batchEmbeddingRequest.setRequests(embeddingRequests.subList(startIndex, lastIndex));

            GoogleAiBatchEmbeddingResponse geminiResponse = withRetry(() -> this.geminiService.batchEmbed(this.modelName, this.apiKey, batchEmbeddingRequest));

            if (geminiResponse != null) {
                allEmbeddings.addAll(geminiResponse.getEmbeddings().stream()
                        .map(values -> Embedding.from(values.getValues()))
                        .collect(Collectors.toList()));
            } else {
                throw new RuntimeException("Gemini embedding response was null (embedAll)");
            }
        }

        return Response.from(allEmbeddings);
    }

    private GoogleAiEmbeddingRequest getGoogleAiEmbeddingRequest(TextSegment textSegment) {
        GeminiPart geminiPart = GeminiPart.builder()
                .text(textSegment.text())
                .build();

        GeminiContent content = new GeminiContent(Collections.singletonList(geminiPart), null);

        String title = null;
        if (TaskType.RETRIEVAL_DOCUMENT.equals(this.taskType)) {
            if (textSegment.metadata() != null && textSegment.metadata().getString(this.titleMetadataKey) != null) {
                title = textSegment.metadata().getString(this.titleMetadataKey);
            }
        }

        return new GoogleAiEmbeddingRequest(
                "models/" + this.modelName,
                content,
                this.taskType,
                title,
                this.outputDimensionality
        );
    }

    @Override
    public int dimension() {
        return getOrDefault(this.outputDimensionality, 768);
    }

    public enum TaskType {
        RETRIEVAL_QUERY,
        RETRIEVAL_DOCUMENT,
        SEMANTIC_SIMILARITY,
        CLASSIFICATION,
        CLUSTERING,
        QUESTION_ANSWERING,
        FACT_VERIFICATION
    }
}
