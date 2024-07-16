package dev.langchain4j.model.cohere;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.DimensionAwareEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import lombok.Builder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static java.time.Duration.ofSeconds;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;

/**
 * An implementation of an {@link EmbeddingModel} that uses
 * <a href="https://docs.cohere.com/docs/embed">Cohere Embed API</a>.
 */
public class CohereEmbeddingModel extends DimensionAwareEmbeddingModel {

    private static final String DEFAULT_BASE_URL = "https://api.cohere.ai/v1/";
    private static final int DEFAULT_MAX_SEGMENTS_PER_BATCH = 96;

    private final CohereClient client;
    private final String modelName;
    private final String inputType;
    private final int maxSegmentsPerBatch;

    @Builder
    public CohereEmbeddingModel(String baseUrl,
                                String apiKey,
                                String modelName,
                                String inputType,
                                Duration timeout,
                                Boolean logRequests,
                                Boolean logResponses,
                                Integer maxSegmentsPerBatch) {
        this.client = CohereClient.builder()
                .baseUrl(getOrDefault(baseUrl, DEFAULT_BASE_URL))
                .apiKey(ensureNotBlank(apiKey, "apiKey"))
                .timeout(getOrDefault(timeout, ofSeconds(60)))
                .logRequests(getOrDefault(logRequests, false))
                .logResponses(getOrDefault(logResponses, false))
                .build();
        this.modelName = modelName;
        this.inputType = inputType;
        this.maxSegmentsPerBatch = getOrDefault(maxSegmentsPerBatch, DEFAULT_MAX_SEGMENTS_PER_BATCH);
    }

    public static CohereEmbeddingModel withApiKey(String apiKey) {
        return CohereEmbeddingModel.builder().apiKey(apiKey).build();
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
        Integer totalTokenUsage = 0;

        for (int i = 0; i < texts.size(); i += maxSegmentsPerBatch) {

            List<String> batch = texts.subList(i, Math.min(i + maxSegmentsPerBatch, texts.size()));

            EmbedRequest request = EmbedRequest.builder()
                    .texts(batch)
                    .inputType(inputType)
                    .model(modelName)
                    .build();

            EmbedResponse response = this.client.embed(request);
            
            embeddings.addAll(getEmbeddings(response));
            totalTokenUsage += getTokenUsage(response);
        }

        return Response.from(
                embeddings,
                new TokenUsage(totalTokenUsage,0)
        );

    }

    private static List<Embedding> getEmbeddings(EmbedResponse response) {
        return stream(response.getEmbeddings())
                .map(Embedding::from)
                .collect(toList());
    }

    private static Integer getTokenUsage(EmbedResponse response) {
        if (response.getMeta() != null
                && response.getMeta().getBilledUnits() != null
                && response.getMeta().getBilledUnits().getInputTokens() != null) {
            return response.getMeta().getBilledUnits().getInputTokens();
        }
        return 0;
    }
}