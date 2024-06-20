package dev.langchain4j.model.localai;

import dev.ai4j.openai4j.OpenAiClient;
import dev.ai4j.openai4j.embedding.EmbeddingRequest;
import dev.ai4j.openai4j.embedding.EmbeddingResponse;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.DimensionAwareEmbeddingModel;
import dev.langchain4j.model.localai.spi.LocalAiEmbeddingModelBuilderFactory;
import dev.langchain4j.model.output.Response;
import lombok.Builder;

import java.time.Duration;
import java.util.List;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.time.Duration.ofSeconds;
import static java.util.stream.Collectors.toList;

/**
 * See <a href="https://localai.io/features/embeddings/">LocalAI documentation</a> for more details.
 */
public class LocalAiEmbeddingModel extends DimensionAwareEmbeddingModel {

    private final OpenAiClient client;
    private final String modelName;
    private final Integer maxRetries;

    @Builder
    public LocalAiEmbeddingModel(String baseUrl,
                                 String modelName,
                                 Duration timeout,
                                 Integer maxRetries,
                                 Boolean logRequests,
                                 Boolean logResponses) {

        timeout = timeout == null ? ofSeconds(60) : timeout;
        maxRetries = maxRetries == null ? 3 : maxRetries;

        this.client = OpenAiClient.builder()
                .openAiApiKey("ignored")
                .baseUrl(ensureNotBlank(baseUrl, "baseUrl"))
                .callTimeout(timeout)
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .writeTimeout(timeout)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .build();
        this.modelName = ensureNotBlank(modelName, "modelName");
        this.maxRetries = maxRetries;
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {

        List<String> texts = textSegments.stream()
                .map(TextSegment::text)
                .collect(toList());

        EmbeddingRequest request = EmbeddingRequest.builder()
                .input(texts)
                .model(modelName)
                .build();

        EmbeddingResponse response = withRetry(() -> client.embedding(request).execute(), maxRetries);

        List<Embedding> embeddings = response.data().stream()
                .map(openAiEmbedding -> Embedding.from(openAiEmbedding.embedding()))
                .collect(toList());

        return Response.from(embeddings);
    }

    public static LocalAiEmbeddingModelBuilder builder() {
        for (LocalAiEmbeddingModelBuilderFactory factory : loadFactories(LocalAiEmbeddingModelBuilderFactory.class)) {
            return factory.get();
        }
        return new LocalAiEmbeddingModelBuilder();
    }

    public static class LocalAiEmbeddingModelBuilder {
        public LocalAiEmbeddingModelBuilder() {
            // This is public so it can be extended
            // By default with Lombok it becomes package private
        }
    }
}
