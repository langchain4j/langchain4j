package dev.langchain4j.model.openai;

import dev.ai4j.openai4j.OpenAiClient;
import dev.ai4j.openai4j.embedding.EmbeddingRequest;
import dev.ai4j.openai4j.embedding.EmbeddingResponse;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.TokenCountEstimator;
import dev.langchain4j.model.output.Result;
import lombok.Builder;

import java.time.Duration;
import java.util.List;

import static dev.langchain4j.model.openai.OpenAiModelName.TEXT_EMBEDDING_ADA_002;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

public class OpenAiEmbeddingModel implements EmbeddingModel, TokenCountEstimator {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(15);

    private final OpenAiClient client;
    private final String modelName;
    private final OpenAiTokenizer tokenizer;

    @Builder
    public OpenAiEmbeddingModel(String apiKey,
                                String modelName,
                                Duration timeout,
                                Boolean logRequests,
                                Boolean logResponses) {
        this.client = OpenAiClient.builder()
                .apiKey(apiKey)
                .callTimeout(timeout == null ? DEFAULT_TIMEOUT : timeout)
                .connectTimeout(timeout == null ? DEFAULT_TIMEOUT : timeout)
                .readTimeout(timeout == null ? DEFAULT_TIMEOUT : timeout)
                .writeTimeout(timeout == null ? DEFAULT_TIMEOUT : timeout)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .build();
        this.modelName = modelName == null ? TEXT_EMBEDDING_ADA_002 : modelName;
        this.tokenizer = new OpenAiTokenizer(this.modelName);
    }

    @Override
    public Result<Embedding> embed(String text) {
        Result<List<Embedding>> result = embedTexts(singletonList(text));
        return Result.from(result.get().get(0));
    }

    @Override
    public Result<Embedding> embed(TextSegment textSegment) {
        return embed(textSegment.text());
    }

    @Override
    public Result<List<Embedding>> embedAll(List<TextSegment> textSegments) {

        List<String> texts = textSegments.stream()
                .map(TextSegment::text)
                .collect(toList());

        return embedTexts(texts);
    }

    private Result<List<Embedding>> embedTexts(List<String> texts) {

        EmbeddingRequest request = EmbeddingRequest.builder()
                .input(texts)
                .model(modelName)
                .build();

        EmbeddingResponse response = client.embedding(request).execute();

        List<Embedding> embeddings = response.data().stream()
                .map(openAiEmbedding -> Embedding.from(openAiEmbedding.embedding()))
                .collect(toList());

        return Result.from(embeddings);
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
