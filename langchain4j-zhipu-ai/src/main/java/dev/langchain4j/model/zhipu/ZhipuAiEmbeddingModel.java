package dev.langchain4j.model.zhipu;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.DimensionAwareEmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.zhipu.embedding.EmbeddingRequest;
import dev.langchain4j.model.zhipu.embedding.EmbeddingResponse;
import dev.langchain4j.model.zhipu.shared.Usage;
import dev.langchain4j.model.zhipu.spi.ZhipuAiEmbeddingModelBuilderFactory;
import lombok.Builder;

import java.time.Duration;
import java.util.List;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.zhipu.DefaultZhipuAiHelper.*;
import static dev.langchain4j.model.zhipu.embedding.EmbeddingModel.EMBEDDING_2;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.util.stream.Collectors.toList;

/**
 * Represents an ZhipuAI embedding model, such as embedding-2 and embedding-3.
 */
public class ZhipuAiEmbeddingModel extends DimensionAwareEmbeddingModel {

    private final Integer maxRetries;
    private final String model;
    private final ZhipuAiClient client;
    private final Integer dimensions;

    @Builder
    public ZhipuAiEmbeddingModel(
            String baseUrl,
            String apiKey,
            String model,
            Integer dimensions,
            Integer maxRetries,
            Boolean logRequests,
            Boolean logResponses,
            Duration callTimeout,
            Duration connectTimeout,
            Duration readTimeout,
            Duration writeTimeout
    ) {
        this.model = getOrDefault(model, EMBEDDING_2.toString());
        this.dimensions = dimensions;
        this.maxRetries = getOrDefault(maxRetries, 3);
        this.client = ZhipuAiClient.builder()
                .baseUrl(getOrDefault(baseUrl, "https://open.bigmodel.cn/"))
                .apiKey(apiKey)
                .callTimeout(callTimeout)
                .connectTimeout(connectTimeout)
                .writeTimeout(writeTimeout)
                .readTimeout(readTimeout)
                .logRequests(getOrDefault(logRequests, false))
                .logResponses(getOrDefault(logResponses, false))
                .build();
    }

    public static ZhipuAiEmbeddingModelBuilder builder() {
        for (ZhipuAiEmbeddingModelBuilderFactory factories : loadFactories(ZhipuAiEmbeddingModelBuilderFactory.class)) {
            return factories.get();
        }
        return new ZhipuAiEmbeddingModelBuilder();
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {

        List<EmbeddingResponse> embeddingRequests = textSegments.stream()
                .map(item -> EmbeddingRequest.builder()
                        .input(item.text())
                        .model(this.model)
                        .dimensions(this.dimensions)
                        .build()
                )
                .map(request -> withRetry(() -> client.embedAll(request), maxRetries))
                .collect(toList());

        Usage usage = getEmbeddingUsage(embeddingRequests);

        return Response.from(
                toEmbed(embeddingRequests),
                tokenUsageFrom(usage)
        );
    }

    public static class ZhipuAiEmbeddingModelBuilder {
        public ZhipuAiEmbeddingModelBuilder() {
        }
    }
}
