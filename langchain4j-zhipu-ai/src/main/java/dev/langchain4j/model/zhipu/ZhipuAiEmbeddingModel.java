package dev.langchain4j.model.zhipu;

import com.zhipu.oapi.ClientV4;
import com.zhipu.oapi.service.v4.embedding.EmbeddingApiResponse;
import com.zhipu.oapi.service.v4.embedding.EmbeddingRequest;
import com.zhipu.oapi.service.v4.model.Usage;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.zhipu.spi.ZhipuAiEmbeddingModelBuilderFactory;
import lombok.Builder;

import java.util.List;
import java.util.stream.Collectors;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.zhipu.DefaultZhipuAiHelper.*;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

/**
 * Represents an ZhipuAI embedding model, such as embedding-2.
 */
public class ZhipuAiEmbeddingModel implements EmbeddingModel {

    private final Integer maxRetries;
    private final String model;
    private final ClientV4 client;

    @Builder
    public ZhipuAiEmbeddingModel(
            String apiKey,
            String model,
            Integer maxRetries
    ) {
        this.model = getOrDefault(model, ZhipuAiEmbeddingModelName.EMBEDDING_2.toString());
        this.maxRetries = getOrDefault(maxRetries, 3);
        this.client = new ClientV4.Builder(apiKey).build();
    }

    public static ZhipuAiEmbeddingModelBuilder builder() {
        for (ZhipuAiEmbeddingModelBuilderFactory factories : loadFactories(ZhipuAiEmbeddingModelBuilderFactory.class)) {
            return factories.get();
        }
        return new ZhipuAiEmbeddingModelBuilder();
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {

        List<EmbeddingApiResponse> embeddingRequests = textSegments.stream()
                .map(item -> {
                    EmbeddingRequest embeddingRequest = new EmbeddingRequest();
                    embeddingRequest.setInput(item.text());
                    embeddingRequest.setModel(this.model);
                    return embeddingRequest;
                })
                .map(request -> withRetry(() -> client.invokeEmbeddingsApi(request), maxRetries))
                .collect(Collectors.toList());

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
