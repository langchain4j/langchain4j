package dev.langchain4j.model.zhipu;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.AbstractEmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.zhipu.embedding.EmbeddingRequest;
import dev.langchain4j.model.zhipu.embedding.EmbeddingResponse;
import dev.langchain4j.model.zhipu.shared.Usage;
import dev.langchain4j.model.zhipu.spi.ZhipuAiEmbeddingModelBuilderFactory;
import lombok.Builder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.zhipu.DefaultZhipuAiHelper.*;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

/**
 * Represents an ZhipuAI embedding model, such as embedding-2.
 */
public class ZhipuAiEmbeddingModel extends AbstractEmbeddingModel {

    private final String baseUrl;
    private final Integer maxRetries;
    private final String model;
    private final ZhipuAiClient client;

    @Builder
    public ZhipuAiEmbeddingModel(
            String baseUrl,
            String apiKey,
            String model,
            Integer maxRetries,
            Boolean logRequests,
            Boolean logResponses
    ) {
        this.baseUrl = getOrDefault(baseUrl, "https://open.bigmodel.cn/");
        this.model = getOrDefault(model, dev.langchain4j.model.zhipu.embedding.EmbeddingModel.EMBEDDING_2.toString());
        this.maxRetries = getOrDefault(maxRetries, 3);
        this.client = ZhipuAiClient.builder()
                .baseUrl(this.baseUrl)
                .apiKey(apiKey)
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
                        .build()
                )
                .map(request -> withRetry(() -> client.embedAll(request), maxRetries))
                .collect(Collectors.toList());

        Usage usage = getEmbeddingUsage(embeddingRequests);

        return Response.from(
                toEmbed(embeddingRequests),
                tokenUsageFrom(usage)
        );
    }

    @Override
    protected Map<String, Integer> dimensionMap() {
        return new HashMap<>();
    }

    @Override
    protected String modelName() {
        return model;
    }

    public static class ZhipuAiEmbeddingModelBuilder {
        public ZhipuAiEmbeddingModelBuilder() {
        }
    }
}
