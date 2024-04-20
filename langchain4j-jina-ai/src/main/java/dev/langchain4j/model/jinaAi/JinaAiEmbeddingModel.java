package dev.langchain4j.model.jinaAi;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import lombok.Builder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static java.time.Duration.ofSeconds;
import static java.util.stream.Collectors.toList;

public class JinaAiEmbeddingModel implements EmbeddingModel {

    /**
     * <a href="https://api.jina.ai/redoc#tag/embeddings">Jina API reference</a>
     */
    private final JinaAiClient client;
    private final String modelName;
    private final Integer maxRetries;

    @Builder
    public JinaAiEmbeddingModel(String baseUrl,
                                String apiKey,
                                String modelName,
                                Duration timeout,
                                Integer maxRetries) {
        this.client = JinaAiClient.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .timeout(getOrDefault(timeout, ofSeconds(60)))
                .build();
        this.modelName = ensureNotBlank(modelName, "modelName");
        this.maxRetries = getOrDefault(maxRetries, 3);
    }

    public static JinaAiEmbeddingModel withApiKey(String apiKey) {
        return JinaAiEmbeddingModel.builder().apiKey(apiKey).build();
    }


    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        EmbeddingRequest request = EmbeddingRequest.builder()
                .model(modelName)
                .texts(textSegments.stream().map(TextSegment::text).collect(toList()))
                .build();

        EmbeddingResponse response = withRetry(() -> client.embed(request), maxRetries);

        List<Embedding> embeddings = response.getEmbeddingList().stream()
                .map(JinaAiEmbedding::toEmbedding).collect(toList());

        TokenUsage tokenUsage = new TokenUsage(response.getUsage().getPromptTokens(),0 );
        return Response.from(embeddings,tokenUsage);
    }

}
