package dev.langchain4j.model.openai;

import dev.ai4j.openai4j.OpenAiClient;
import dev.ai4j.openai4j.embedding.EmbeddingRequest;
import dev.ai4j.openai4j.embedding.EmbeddingResponse;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.TokenCountEstimator;
import lombok.Builder;

import java.time.Duration;
import java.util.List;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.model.openai.OpenAiModelName.TEXT_EMBEDDING_ADA_002;
import static java.time.Duration.ofSeconds;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

public class OpenAiEmbeddingModel implements EmbeddingModel, TokenCountEstimator {

    private final OpenAiClient client;
    private final String modelName;
    private final Integer maxRetries;
    private final OpenAiTokenizer tokenizer;

    @Builder
    public OpenAiEmbeddingModel(String apiKey,
                                String modelName,
                                Duration timeout,
                                Integer maxRetries,
                                Boolean logRequests,
                                Boolean logResponses) {

        modelName = modelName == null ? TEXT_EMBEDDING_ADA_002 : modelName;
        timeout = timeout == null ? ofSeconds(15) : timeout;
        maxRetries = maxRetries == null ? 3 : maxRetries;

        this.client = OpenAiClient.builder()
                .apiKey(apiKey)
                .callTimeout(timeout)
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .writeTimeout(timeout)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .build();
        this.modelName = modelName;
        this.maxRetries = maxRetries;
        this.tokenizer = new OpenAiTokenizer(this.modelName);
    }

    @Override
    public Embedding embed(String text) {
        List<Embedding> embeddings = embedTexts(singletonList(text));
        return embeddings.get(0);
    }

    @Override
    public Embedding embed(TextSegment textSegment) {
        return embed(textSegment.text());
    }

    @Override
    public List<Embedding> embedAll(List<TextSegment> textSegments) {

        List<String> texts = textSegments.stream()
                .map(TextSegment::text)
                .collect(toList());

        return embedTexts(texts);
    }

    private List<Embedding> embedTexts(List<String> texts) {

        EmbeddingRequest request = EmbeddingRequest.builder()
                .input(texts)
                .model(modelName)
                .build();

        EmbeddingResponse response = withRetry(() -> client.embedding(request).execute(), maxRetries);

        return response.data().stream()
                .map(openAiEmbedding -> Embedding.from(openAiEmbedding.embedding()))
                .collect(toList());
    }

    @Override
    public int estimateTokenCount(String text) {
        return tokenizer.countTokens(text);
    }

    @Override
    public int estimateTokenCount(TextSegment textSegment) {
        return estimateTokenCount(textSegment.text());
    }

    @Override
    public int estimateTokenCount(List<TextSegment> textSegments) {
        int tokenCount = 0;
        for (TextSegment textSegment : textSegments) {
            tokenCount += estimateTokenCount(textSegment);
        }
        return tokenCount;
    }

    public static OpenAiEmbeddingModel withApiKey(String apiKey) {
        return builder().apiKey(apiKey).build();
    }
}
