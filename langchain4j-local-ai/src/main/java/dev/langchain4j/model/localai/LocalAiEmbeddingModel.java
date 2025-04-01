package dev.langchain4j.model.localai;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.DimensionAwareEmbeddingModel;
import dev.langchain4j.model.localai.spi.LocalAiEmbeddingModelBuilderFactory;
import dev.langchain4j.model.openai.internal.OpenAiClient;
import dev.langchain4j.model.openai.internal.embedding.EmbeddingRequest;
import dev.langchain4j.model.openai.internal.embedding.EmbeddingResponse;
import dev.langchain4j.model.output.Response;

import java.time.Duration;
import java.util.List;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
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

    public LocalAiEmbeddingModel(String baseUrl,
                                 String modelName,
                                 Duration timeout,
                                 Integer maxRetries,
                                 Boolean logRequests,
                                 Boolean logResponses) {

        timeout = timeout == null ? ofSeconds(60) : timeout;
        maxRetries = maxRetries == null ? 3 : maxRetries;

        this.client = OpenAiClient.builder()
                .baseUrl(ensureNotBlank(baseUrl, "baseUrl"))
                .connectTimeout(timeout)
                .readTimeout(timeout)
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

        EmbeddingResponse response = withRetryMappingExceptions(() -> client.embedding(request).execute(), maxRetries);

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
        private String baseUrl;
        private String modelName;
        private Duration timeout;
        private Integer maxRetries;
        private Boolean logRequests;
        private Boolean logResponses;

        public LocalAiEmbeddingModelBuilder() {
            // This is public so it can be extended
            // By default with Lombok it becomes package private
        }

        public LocalAiEmbeddingModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public LocalAiEmbeddingModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public LocalAiEmbeddingModelBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public LocalAiEmbeddingModelBuilder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public LocalAiEmbeddingModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public LocalAiEmbeddingModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public LocalAiEmbeddingModel build() {
            return new LocalAiEmbeddingModel(this.baseUrl, this.modelName, this.timeout, this.maxRetries, this.logRequests, this.logResponses);
        }

        public String toString() {
            return "LocalAiEmbeddingModel.LocalAiEmbeddingModelBuilder(baseUrl=" + this.baseUrl + ", modelName=" + this.modelName + ", timeout=" + this.timeout + ", maxRetries=" + this.maxRetries + ", logRequests=" + this.logRequests + ", logResponses=" + this.logResponses + ")";
        }
    }
}
