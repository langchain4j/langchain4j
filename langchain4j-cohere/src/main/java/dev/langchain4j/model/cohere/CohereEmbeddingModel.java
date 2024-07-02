package dev.langchain4j.model.cohere;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.DimensionAwareEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import lombok.Builder;

import java.time.Duration;
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

    private final CohereClient client;
    private final String modelName;
    private final String inputType;

    @Builder
    public CohereEmbeddingModel(String baseUrl,
                                String apiKey,
                                String modelName,
                                String inputType,
                                Duration timeout,
                                Boolean logRequests,
                                Boolean logResponses) {
        this.client = CohereClient.builder()
                .baseUrl(getOrDefault(baseUrl, DEFAULT_BASE_URL))
                .apiKey(ensureNotBlank(apiKey, "apiKey"))
                .timeout(getOrDefault(timeout, ofSeconds(60)))
                .logRequests(getOrDefault(logRequests, false))
                .logResponses(getOrDefault(logResponses, false))
                .build();
        this.modelName = modelName;
        this.inputType = inputType;
    }

    public static CohereEmbeddingModel withApiKey(String apiKey) {
        return CohereEmbeddingModel.builder().apiKey(apiKey).build();
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {

        List<String> texts = textSegments.stream()
                .map(TextSegment::text)
                .collect(toList());

        EmbedRequest request = EmbedRequest.builder()
                .texts(texts)
                .inputType(inputType)
                .model(modelName)
                .build();

        EmbedResponse response = this.client.embed(request);

        return Response.from(getEmbeddings(response), getTokenUsage(response));
    }

    private static List<Embedding> getEmbeddings(EmbedResponse response) {
        return stream(response.getEmbeddings())
                .map(Embedding::from)
                .collect(toList());
    }

    private static TokenUsage getTokenUsage(EmbedResponse response) {
        if (response.getMeta() != null
                && response.getMeta().getBilledUnits() != null
                && response.getMeta().getBilledUnits().getInputTokens() != null) {
            return new TokenUsage(response.getMeta().getBilledUnits().getInputTokens(), 0);
        }
        return null;
    }
}