package dev.langchain4j.model.openai;

import dev.ai4j.openai4j.OpenAiClient;
import dev.ai4j.openai4j.embedding.EmbeddingRequest;
import dev.ai4j.openai4j.embedding.EmbeddingResponse;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.TokenCountEstimator;
import dev.langchain4j.model.openai.spi.OpenAiEmbeddingModelBuilderFactory;
import dev.langchain4j.model.output.Response;
import lombok.Builder;

import java.net.Proxy;
import java.time.Duration;
import java.util.List;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.*;
import static dev.langchain4j.model.openai.OpenAiModelName.TEXT_EMBEDDING_ADA_002;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.time.Duration.ofSeconds;
import static java.util.stream.Collectors.toList;

/**
 * Represents an OpenAI embedding model, such as text-embedding-ada-002.
 */
public class OpenAiEmbeddingModel implements EmbeddingModel, TokenCountEstimator {

    private final OpenAiClient client;
    private final String modelName;
    private final Integer dimensions;
    private final String user;
    private final Integer maxRetries;
    private final Tokenizer tokenizer;

    @Builder
    public OpenAiEmbeddingModel(String baseUrl,
                                String apiKey,
                                String organizationId,
                                String modelName,
                                Integer dimensions,
                                String user,
                                Duration timeout,
                                Integer maxRetries,
                                Proxy proxy,
                                Boolean logRequests,
                                Boolean logResponses,
                                Tokenizer tokenizer) {

        baseUrl = getOrDefault(baseUrl, OPENAI_URL);
        if (OPENAI_DEMO_API_KEY.equals(apiKey)) {
            baseUrl = OPENAI_DEMO_URL;
        }

        timeout = getOrDefault(timeout, ofSeconds(60));

        this.client = OpenAiClient.builder()
                .openAiApiKey(apiKey)
                .baseUrl(baseUrl)
                .organizationId(organizationId)
                .callTimeout(timeout)
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .writeTimeout(timeout)
                .proxy(proxy)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .userAgent(DEFAULT_USER_AGENT)
                .build();
        this.modelName = getOrDefault(modelName, TEXT_EMBEDDING_ADA_002);
        this.dimensions = dimensions;
        this.user = user;
        this.maxRetries = getOrDefault(maxRetries, 3);
        this.tokenizer = getOrDefault(tokenizer, () -> new OpenAiTokenizer(this.modelName));
    }

    public String modelName() {
        return modelName;
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {

        List<String> texts = textSegments.stream()
                .map(TextSegment::text)
                .collect(toList());

        return embedTexts(texts);
    }

    private Response<List<Embedding>> embedTexts(List<String> texts) {

        EmbeddingRequest request = EmbeddingRequest.builder()
                .input(texts)
                .model(modelName)
                .dimensions(dimensions)
                .user(user)
                .build();

        EmbeddingResponse response = withRetry(() -> client.embedding(request).execute(), maxRetries);

        List<Embedding> embeddings = response.data().stream()
                .map(openAiEmbedding -> Embedding.from(openAiEmbedding.embedding()))
                .collect(toList());

        return Response.from(
                embeddings,
                tokenUsageFrom(response.usage())
        );
    }

    @Override
    public int estimateTokenCount(String text) {
        return tokenizer.estimateTokenCountInText(text);
    }

    public static OpenAiEmbeddingModel withApiKey(String apiKey) {
        return builder().apiKey(apiKey).build();
    }

    public static OpenAiEmbeddingModelBuilder builder() {
        for (OpenAiEmbeddingModelBuilderFactory factory : loadFactories(OpenAiEmbeddingModelBuilderFactory.class)) {
            return factory.get();
        }
        return new OpenAiEmbeddingModelBuilder();
    }

    public static class OpenAiEmbeddingModelBuilder {

        public OpenAiEmbeddingModelBuilder() {
            // This is public so it can be extended
            // By default with Lombok it becomes package private
        }

        public OpenAiEmbeddingModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public OpenAiEmbeddingModelBuilder modelName(OpenAiEmbeddingModelName modelName) {
            this.modelName = modelName.toString();
            return this;
        }
    }
}
