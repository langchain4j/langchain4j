package dev.langchain4j.model.qianfan;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.qianfan.client.QianfanClient;
import dev.langchain4j.model.qianfan.client.embedding.EmbeddingResponse;
import dev.langchain4j.model.qianfan.client.embedding.EmbeddingRequest;
import dev.langchain4j.model.qianfan.spi.QianfanEmbeddingModelBuilderFactory;
import dev.langchain4j.spi.ServiceHelper;
import lombok.Builder;
import java.util.List;
import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.qianfan.InternalQianfanHelper.*;
import static java.util.stream.Collectors.toList;
/**
 *
 * see details here: https://cloud.baidu.com/doc/WENXINWORKSHOP/s/Nlks5zkzu
 */

public class QianfanEmbeddingModel implements EmbeddingModel {


    private final QianfanClient client;

    private final String baseUrl;

    private final String modelName;

    private final Integer maxRetries;

    private final String user;


    private final String endpoint;

    @Builder
    public QianfanEmbeddingModel(String baseUrl,
                                 String apiKey,
                                 String secretKey,
                                 Integer maxRetries,
                                 String modelName,
                                 String endpoint,
                                 String user,
                                 Boolean logRequests,
                                 Boolean logResponses
                             ) {
        if (Utils.isNullOrBlank(apiKey)||Utils.isNullOrBlank(secretKey)) {
            throw new IllegalArgumentException(" api key and secret key must be defined. It can be generated here: https://console.bce.baidu.com/qianfan/ais/console/applicationConsole/application");
        }


        this.modelName=modelName;
        this.endpoint=Utils.isNullOrBlank(endpoint)? QianfanEmbeddingModelNameEnum.getEndpoint(modelName):endpoint;

        if (Utils.isNullOrBlank(this.endpoint) ) {
            throw new IllegalArgumentException("Qianfan is no such model name. You can see model name here: https://cloud.baidu.com/doc/WENXINWORKSHOP/s/Nlks5zkzu");
        }

        this.baseUrl = getOrDefault(baseUrl,  "https://aip.baidubce.com");
        this.client = QianfanClient.builder()
                .baseUrl(this.baseUrl)
                .apiKey(apiKey)
                .secretKey(secretKey)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .build();
        this.maxRetries = getOrDefault(maxRetries, 3);
        this.user = user;
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
                .user(user)
                .build();

        EmbeddingResponse response = withRetry(() -> client.embedding(request, endpoint).execute(), maxRetries);

        List<Embedding> embeddings = response.data().stream()
                .map(openAiEmbedding -> Embedding.from(openAiEmbedding.embedding()))
                .collect(toList());

        return Response.from(
                embeddings,
                tokenUsageFrom(response)
        );
    }

    public static QianfanEmbeddingModelBuilder builder() {
        return ServiceHelper.loadFactoryService(
                QianfanEmbeddingModelBuilderFactory.class,
                QianfanEmbeddingModelBuilder::new
        );
    }

    public static class QianfanEmbeddingModelBuilder {
        public QianfanEmbeddingModelBuilder() {
            // This is public so it can be extended
            // By default with Lombok it becomes package private
        }
    }
}
