package dev.langchain4j.model.ollama;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.AbstractEmbeddingModel;
import dev.langchain4j.model.ollama.spi.OllamaEmbeddingModelBuilderFactory;
import dev.langchain4j.model.output.Response;
import lombok.Builder;

import java.time.Duration;
import java.util.*;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.time.Duration.ofSeconds;

/**
 * <a href="https://github.com/jmorganca/ollama/blob/main/docs/api.md">Ollama API reference</a>
 */
public class OllamaEmbeddingModel extends AbstractEmbeddingModel {

    private final OllamaClient client;
    private final String modelName;
    private final Integer maxRetries;

    @Builder
    public OllamaEmbeddingModel(String baseUrl,
                                String modelName,
                                Duration timeout,
                                Integer maxRetries,
                                Boolean logRequests,
                                Boolean logResponses,
                                Map<String, String> customHeaders) {
        this.client = OllamaClient.builder()
                .baseUrl(baseUrl)
                .timeout(getOrDefault(timeout, ofSeconds(60)))
                .logRequests(logRequests)
                .logResponses(logResponses)
                .customHeaders(customHeaders)
                .build();
        this.modelName = ensureNotBlank(modelName, "modelName");
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

            embeddings.add(Embedding.from(response.getEmbedding()));
        });

        return Response.from(embeddings);
    }

    public static OllamaEmbeddingModelBuilder builder() {
        for (OllamaEmbeddingModelBuilderFactory factory : loadFactories(OllamaEmbeddingModelBuilderFactory.class)) {
            return factory.get();
        }
        return new OllamaEmbeddingModelBuilder();
    }

    @Override
    protected Map<String, Integer> dimensionMap() {
        return new HashMap<>();
    }

    @Override
    protected String modelName() {
        return modelName;
    }

    public static class OllamaEmbeddingModelBuilder {
        public OllamaEmbeddingModelBuilder() {
            // This is public so it can be extended
            // By default with Lombok it becomes package private
        }
    }
}
