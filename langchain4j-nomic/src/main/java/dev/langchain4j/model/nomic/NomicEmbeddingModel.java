package dev.langchain4j.model.nomic;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.DimensionAwareEmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import lombok.Builder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.model.chat.policy.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static java.time.Duration.ofSeconds;
import static java.util.stream.Collectors.toList;

/**
 * An integration with Nomic Atlas's Text Embeddings API.
 * See more details <a href="https://docs.nomic.ai/reference/endpoints/nomic-embed-text">here</a>.
 */
public class NomicEmbeddingModel extends DimensionAwareEmbeddingModel {

    private static final String DEFAULT_BASE_URL = "https://api-atlas.nomic.ai/v1/";

    private final NomicClient client;
    private final String modelName;
    private final String taskType;
    private final Integer maxSegmentsPerBatch;
    private final Integer maxRetries;

    @Builder
    public NomicEmbeddingModel(
            String baseUrl,
            String apiKey,
            String modelName,
            String taskType,
            Integer maxSegmentsPerBatch,
            Duration timeout,
            Integer maxRetries,
            Boolean logRequests,
            Boolean logResponses
    ) {
        this.client = NomicClient.builder()
                .baseUrl(getOrDefault(baseUrl, DEFAULT_BASE_URL))
                .apiKey(ensureNotBlank(apiKey, "apiKey"))
                .timeout(getOrDefault(timeout, ofSeconds(60)))
                .logRequests(getOrDefault(logRequests, false))
                .logResponses(getOrDefault(logResponses, false))
                .build();
        this.modelName = getOrDefault(modelName, "nomic-embed-text-v1");
        this.taskType = taskType;
        this.maxSegmentsPerBatch = getOrDefault(maxSegmentsPerBatch, 500);
        this.maxRetries = getOrDefault(maxRetries, 3);
    }

    /**
     * @deprecated Please use {@code builder()} instead, and explicitly set the model name and,
     * if necessary, other parameters.
     * <b>The default value for the model name will be removed in future releases!</b>
     */
    @Deprecated(forRemoval = true)
    public static NomicEmbeddingModel withApiKey(String apiKey) {
        return NomicEmbeddingModel.builder().apiKey(apiKey).build();
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {

        List<String> texts = textSegments.stream()
                .map(TextSegment::text)
                .collect(toList());

        return embedTexts(texts);
    }

    private Response<List<Embedding>> embedTexts(List<String> texts) {

        List<Embedding> embeddings = new ArrayList<>();
        int inputTokenCount = 0;

        for (int i = 0; i < texts.size(); i += maxSegmentsPerBatch) {
            List<String> batch = texts.subList(i, Math.min(i + maxSegmentsPerBatch, texts.size()));

            EmbeddingRequest request = EmbeddingRequest.builder()
                    .model(modelName)
                    .texts(batch)
                    .taskType(taskType)
                    .build();

            EmbeddingResponse response = withRetry(() -> this.client.embed(request), maxRetries);

            embeddings.addAll(getEmbeddings(response));
            inputTokenCount += getTokenUsage(response);
        }

        return Response.from(embeddings, new TokenUsage(inputTokenCount, 0));
    }

    private List<Embedding> getEmbeddings(EmbeddingResponse response) {
        return response.getEmbeddings().stream()
                .map(Embedding::from)
                .collect(toList());
    }

    private Integer getTokenUsage(EmbeddingResponse response) {
        if (response.getUsage() != null) {
            return response.getUsage().getTotalTokens();
        }
        return 0;
    }
}
