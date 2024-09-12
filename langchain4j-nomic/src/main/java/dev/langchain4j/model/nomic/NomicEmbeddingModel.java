package dev.langchain4j.model.nomic;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.ModelConstant;
import dev.langchain4j.model.embedding.DimensionAwareEmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import lombok.Builder;

import java.time.Duration;
import java.util.List;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static java.util.stream.Collectors.toList;

/**
 * An integration with Nomic Atlas's Text Embeddings API.
 * See more details <a href="https://docs.nomic.ai/reference/endpoints/nomic-embed-text">here</a>.
 */
public class NomicEmbeddingModel extends DimensionAwareEmbeddingModel {

    private final NomicClient client;
    private final String modelName;
    private final String taskType;
    private final Integer maxRetries;

    @Builder
    public NomicEmbeddingModel(
            String baseUrl,
            String apiKey,
            String modelName,
            String taskType,
            Duration timeout,
            Integer maxRetries,
            Boolean logRequests,
            Boolean logResponses
    ) {
        this.client = NomicClient.builder()
                .baseUrl(baseUrl)
                .apiKey(ensureNotBlank(apiKey, "apiKey"))
                .timeout(timeout)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .build();
        this.modelName = getOrDefault(modelName, "nomic-embed-text-v1");
        this.taskType = taskType;
        this.maxRetries = getOrDefault(maxRetries, ModelConstant.DEFAULT_CLIENT_RETRIES);
    }

    public static NomicEmbeddingModel withApiKey(String apiKey) {
        return NomicEmbeddingModel.builder().apiKey(apiKey).build();
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {

        EmbeddingRequest request = EmbeddingRequest.builder()
                .model(modelName)
                .texts(textSegments.stream().map(TextSegment::text).collect(toList()))
                .taskType(taskType)
                .build();

        EmbeddingResponse response = withRetry(() -> client.embed(request), maxRetries);

        List<Embedding> embeddings = response.getEmbeddings().stream()
                .map(Embedding::from).collect(toList());

        TokenUsage tokenUsage = new TokenUsage(response.getUsage().getTotalTokens(), 0);

        return Response.from(embeddings, tokenUsage);
    }
}
