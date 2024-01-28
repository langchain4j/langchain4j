package dev.langchain4j.model.zhipu;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.zhipu.spi.ZhipuAiEmbeddingModelBuilderFactory;
import dev.langchain4j.spi.ServiceHelper;
import lombok.Builder;

import java.util.List;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.zhipu.DefaultZhipuAiHelper.*;

public class ZhipuAiEmbeddingModel implements EmbeddingModel {

    private final String baseUrl;
    private final Integer maxRetries;
    private final ZhipuAiEmbeddingModelEnum model;
    private final ZhipuAiClient client;

    @Builder
    public ZhipuAiEmbeddingModel(
            String baseUrl,
            String apiKey,
            ZhipuAiEmbeddingModelEnum model,
            Integer maxRetries,
            Boolean logRequests,
            Boolean logResponses
    ) {
        this.baseUrl = getOrDefault(baseUrl, "https://open.bigmodel.cn/");
        this.model = getOrDefault(model, ZhipuAiEmbeddingModelEnum.EMBEDDING_2);
        this.maxRetries = getOrDefault(maxRetries, 3);
        this.client = ZhipuAiClient.builder()
                .baseUrl(this.baseUrl)
                .apiKey(apiKey)
                .logRequests(getOrDefault(logRequests, false))
                .logResponses(getOrDefault(logResponses, false))
                .build();
    }

    public static ZhipuAiEmbeddingModelBuilder builder() {
        return ServiceHelper.loadFactoryService(
                ZhipuAiEmbeddingModelBuilderFactory.class,
                ZhipuAiEmbeddingModelBuilder::new
        );
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {

        ZhipuAiEmbeddingRequest request = ZhipuAiEmbeddingRequest.builder()
                .model(this.model)
                .input(toEmbedTexts(textSegments))
                .build();

        ZhipuAiEmbeddingResponse response = withRetry(() -> client.embedAll(request), maxRetries);

        return Response.from(
                toEmbed(response),
                tokenUsageFrom(response.getUsage())
        );
    }

    public static class ZhipuAiEmbeddingModelBuilder {
        public ZhipuAiEmbeddingModelBuilder() {
            // This is public so it can be extended
            // By default with Lombok it becomes package private
        }
    }
}
