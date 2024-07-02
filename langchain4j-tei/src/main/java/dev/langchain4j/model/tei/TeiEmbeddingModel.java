package dev.langchain4j.model.tei;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.model.tei.client.EmbeddingRequest;
import dev.langchain4j.model.tei.client.EmbeddingResponse;
import dev.langchain4j.model.tei.spi.TeiEmbeddingModelBuilderFactory;
import lombok.Builder;

import java.time.Duration;
import java.util.List;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.time.Duration.ofSeconds;
import static java.util.stream.Collectors.toList;

public class TeiEmbeddingModel implements EmbeddingModel {

    private final TeiClient client;

    private final Integer maxRetries;

    @Builder
    public TeiEmbeddingModel(String baseUrl,
                             Duration timeout,
                             Integer maxRetries) {
        this.client = TeiClient.builder()
                .baseUrl(baseUrl)
                .timeout(getOrDefault(timeout, ofSeconds(60)))
                .build();
        this.maxRetries = getOrDefault(maxRetries, 3);
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        EmbeddingRequest request = EmbeddingRequest.builder()
                .input(textSegments.stream().map(TextSegment::text).collect(toList())).build();
        EmbeddingResponse response = withRetry(() -> client.embedding(request), maxRetries);

        List<Embedding> embeddings = response.getData().stream()
                .map(openAiEmbedding -> Embedding.from(openAiEmbedding.getEmbedding()))
                .collect(toList());

        TokenUsage tokenUsage = new TokenUsage(response.getUsage().getTotalTokens(), 0);
        return Response.from(embeddings, tokenUsage);
    }

    public static TeiEmbeddingModelBuilder builder() {
        for (TeiEmbeddingModelBuilderFactory factory : loadFactories(TeiEmbeddingModelBuilderFactory.class)) {
            return factory.get();
        }
        return new TeiEmbeddingModelBuilder();
    }

    public static class TeiEmbeddingModelBuilder {
        public TeiEmbeddingModelBuilder() {
            // This is public so it can be extended
            // By default with Lombok it becomes package private
        }
    }
}
