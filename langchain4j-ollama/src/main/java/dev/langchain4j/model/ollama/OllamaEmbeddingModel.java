package dev.langchain4j.model.ollama;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import lombok.Builder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;

/**
 * Represents an Ollama embedding model.
 */
public class OllamaEmbeddingModel implements EmbeddingModel {

    private final OllamaClient client;
    private final String modelName;
    private final Integer maxRetries;

    @Builder
    public OllamaEmbeddingModel(String baseUrl, Duration timeout,
                                String modelName, Integer maxRetries) {
        this.client = OllamaClient.builder().baseUrl(baseUrl).timeout(timeout).build();
        this.modelName = modelName;
        this.maxRetries = getOrDefault(maxRetries, 3);
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        List<Embedding> embeddings = new ArrayList<>();
        textSegments.forEach(textSegment -> {
            EmbeddingRequest request = EmbeddingRequest.builder()
                    .model(modelName)
                    .prompt(textSegment.text())
                    .build();

            EmbeddingResponse response = withRetry(() -> client.embed(request), maxRetries);
            embeddings.add(new Embedding(response.getEmbedding()));
        });

        return Response.from(embeddings);
    }
}
