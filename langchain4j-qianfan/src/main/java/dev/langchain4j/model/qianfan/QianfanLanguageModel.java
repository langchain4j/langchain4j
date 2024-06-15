package dev.langchain4j.model.qianfan;


import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.language.LanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.qianfan.client.QianfanClient;
import dev.langchain4j.model.qianfan.client.completion.CompletionRequest;
import dev.langchain4j.model.qianfan.client.completion.CompletionResponse;
import dev.langchain4j.model.qianfan.spi.QianfanLanguageModelBuilderFactory;
import lombok.Builder;

import java.net.Proxy;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.qianfan.InternalQianfanHelper.finishReasonFrom;
import static dev.langchain4j.model.qianfan.InternalQianfanHelper.tokenUsageFrom;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;


/**
 *
 * see details here: https://cloud.baidu.com/doc/WENXINWORKSHOP/s/Nlks5zkzu
 */
public class QianfanLanguageModel implements LanguageModel {



    private final QianfanClient client;

    private final String baseUrl;

    private final Double temperature;
    private final Double topP;
    private final String modelName;


    private  final Double penaltyScore;
    private final Integer maxRetries;

    private final Integer topK;

    private final String endpoint;
    @Builder
    public QianfanLanguageModel(String baseUrl,
                                String apiKey,
                                String secretKey,
                                Double temperature,
                                Integer maxRetries,
                                Integer topK,
                                Double topP,
                                String modelName,
                                String endpoint,
                                Double penaltyScore,
                                Boolean logRequests,
                                Boolean logResponses,
                                Proxy proxy
                             ) {
        if (Utils.isNullOrBlank(apiKey)||Utils.isNullOrBlank(secretKey)) {
            throw new IllegalArgumentException(" api key and secret key must be defined. It can be generated here: https://console.bce.baidu.com/qianfan/ais/console/applicationConsole/application");
        }

        this.modelName=modelName;
        this.endpoint=Utils.isNullOrBlank(endpoint)? QianfanLanguageModelNameEnum.getEndpoint(modelName):endpoint;

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
                .proxy(proxy)
                .build();
        this.temperature = getOrDefault(temperature, 0.7);
        this.maxRetries = getOrDefault(maxRetries, 3);
        this.topP = topP;
        this.topK = topK;
        this.penaltyScore = penaltyScore;
    }

    @Override
    public Response<String> generate(String prompt) {

        CompletionRequest request = CompletionRequest.builder()
                .prompt(prompt)
                .topK(topK)
                .topP(topP)
                .temperature(temperature)
                .penaltyScore(penaltyScore)
                .build();


        CompletionResponse response = withRetry(() -> client.completion(request,false,endpoint).execute(), maxRetries);

        return Response.from(
                response.getResult(),
                tokenUsageFrom(response),
                finishReasonFrom(response.getFinishReason())
        );
    }

    public static QianfanLanguageModelBuilder builder() {
        for (QianfanLanguageModelBuilderFactory factory : loadFactories(QianfanLanguageModelBuilderFactory.class)) {
            return factory.get();
        }
        return new QianfanLanguageModelBuilder();
    }

    public static class QianfanLanguageModelBuilder {
        public QianfanLanguageModelBuilder() {
            // This is public so it can be extended
            // By default with Lombok it becomes package private
        }
    }
}
