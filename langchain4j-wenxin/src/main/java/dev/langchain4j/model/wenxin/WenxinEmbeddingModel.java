package dev.langchain4j.model.wenxin;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.wenxin.client.embedding.EmbeddingRequest;
import dev.langchain4j.model.wenxin.client.embedding.EmbeddingResponse;
import lombok.Builder;
import java.util.List;
import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.wenxin.InternalWenxinHelper.*;
import static java.util.stream.Collectors.toList;
/**
 *
 * see details here: https://cloud.baidu.com/doc/WENXINWORKSHOP/s/Nlks5zkzu
 */

public class WenxinEmbeddingModel implements EmbeddingModel {


    private final BaiduClient client;

    private final String baseUrl;

    private final String modelName;

    private final Integer maxRetries;

    private final String user;


    private final String serviceName;

    @Builder
    public WenxinEmbeddingModel(String baseUrl,
                                String clientId,
                                String secretKey,
                                Integer maxRetries,
                                String modelName,
                                String serviceName,
                                String user
                             ) {
        if (Utils.isNullOrBlank(clientId)||Utils.isNullOrBlank(secretKey)) {
            throw new IllegalArgumentException(" client id and secret key must be defined. It can be generated here: https://console.bce.baidu.com/qianfan/ais/console/applicationConsole/application");
        }


        this.modelName=modelName;
        this.serviceName=Utils.isNullOrBlank(serviceName)?WenxinModelEnum.getServiceName(modelName):serviceName;

        if (Utils.isNullOrBlank(this.serviceName) ) {
            throw new IllegalArgumentException("baidu is no such model name. You can see model name here: https://cloud.baidu.com/doc/WENXINWORKSHOP/s/Nlks5zkzu");
        }

        this.baseUrl = getOrDefault(baseUrl,  "https://aip.baidubce.com");
        this.client = BaiduClient.builder().baseUrl(this.baseUrl).wenXinApiKey(clientId).wenXinSecretKey(secretKey).logStreamingResponses(true).build();
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

        EmbeddingResponse response = withRetry(() -> client.embedding(request, serviceName).execute(), maxRetries);

        List<Embedding> embeddings = response.data().stream()
                .map(openAiEmbedding -> Embedding.from(openAiEmbedding.embedding()))
                .collect(toList());

        return Response.from(
                embeddings,
                tokenUsageFrom(response)
        );
    }


}
