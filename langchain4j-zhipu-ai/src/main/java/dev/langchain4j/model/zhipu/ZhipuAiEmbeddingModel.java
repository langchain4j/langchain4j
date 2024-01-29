package dev.langchain4j.model.zhipu;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.zhipu.embedding.EmbeddingRequest;
import dev.langchain4j.model.zhipu.embedding.EmbeddingResponse;
import dev.langchain4j.model.zhipu.spi.ZhipuAiEmbeddingModelBuilderFactory;
import dev.langchain4j.spi.ServiceHelper;

import java.util.List;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.zhipu.DefaultZhipuAiHelper.*;

/**
 * Represents an ZhipuAI embedding model, such as embedding-2.
 */
public class ZhipuAiEmbeddingModel implements EmbeddingModel {

    private final String baseUrl;
    private final Integer maxRetries;
    private final String model;
    private final ZhipuAiClient client;

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
        return ServiceHelper.loadFactoryService(
                ZhipuAiEmbeddingModelBuilderFactory.class,
                ZhipuAiEmbeddingModelBuilder::new
        );
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {

        EmbeddingRequest request = EmbeddingRequest.builder()
                .model(this.model)
                .input(toEmbedTexts(textSegments))
                .build();

        EmbeddingResponse response = withRetry(() -> client.embedAll(request), maxRetries);

        return Response.from(
                toEmbed(response),
                tokenUsageFrom(response.getUsage())
        );
    }

    public static class ZhipuAiEmbeddingModelBuilder {
        private String baseUrl;
        private String apiKey;
        private String model;
        private Integer maxRetries;
        private Boolean logRequests;
        private Boolean logResponses;

        public ZhipuAiEmbeddingModelBuilder() {
        }

        public ZhipuAiEmbeddingModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public ZhipuAiEmbeddingModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public ZhipuAiEmbeddingModelBuilder model(String model) {
            this.model = model;
            return this;
        }

        public ZhipuAiEmbeddingModelBuilder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public ZhipuAiEmbeddingModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public ZhipuAiEmbeddingModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public ZhipuAiEmbeddingModel build() {
            return new ZhipuAiEmbeddingModel(
                    this.baseUrl,
                    this.apiKey,
                    this.model,
                    this.maxRetries,
                    this.logRequests,
                    this.logResponses
            );
        }
    }
}
