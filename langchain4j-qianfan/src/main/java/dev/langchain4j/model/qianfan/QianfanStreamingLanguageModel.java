package dev.langchain4j.model.qianfan;


import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.language.StreamingLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.qianfan.client.QianfanClient;
import dev.langchain4j.model.qianfan.client.QianfanStreamingResponseBuilder;
import dev.langchain4j.model.qianfan.client.completion.CompletionRequest;
import dev.langchain4j.model.qianfan.client.SyncOrAsyncOrStreaming;
import dev.langchain4j.model.qianfan.client.completion.CompletionResponse;
import dev.langchain4j.model.qianfan.spi.QianfanStreamingLanguageModelBuilderFactory;
import lombok.Builder;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

/**
 *
 * see details here: https://cloud.baidu.com/doc/WENXINWORKSHOP/s/Nlks5zkzu
 */
public class QianfanStreamingLanguageModel implements StreamingLanguageModel {



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
    public QianfanStreamingLanguageModel(String baseUrl,
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
                                         Boolean logResponses
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
                .logStreamingResponses(logResponses)
                .build();
        this.temperature = getOrDefault(temperature, 0.7);
        this.maxRetries = getOrDefault(maxRetries, 3);
        this.topP = topP;
        this.topK = topK;
        this.penaltyScore = penaltyScore;
    }

    @Override
    public void generate(String prompt, StreamingResponseHandler<String> handler) {

        CompletionRequest request = CompletionRequest.builder()
                .prompt(prompt)
                .topK(topK)
                .topP(topP)
                .temperature(temperature)
                .penaltyScore(penaltyScore)
                .build();


        QianfanStreamingResponseBuilder responseBuilder = new QianfanStreamingResponseBuilder(null);


        SyncOrAsyncOrStreaming<CompletionResponse> response = client.completion(request, true, endpoint);


        response.onPartialResponse(partialResponse -> {
                    responseBuilder.append(partialResponse);
                    handle(partialResponse, handler);
                })
                .onComplete(() -> {
                    Response<String> response1 = responseBuilder.build(null);
                    handler.onComplete(response1);
                })
                .onError(handler::onError)
                .execute();


    }
    private static void handle(CompletionResponse partialResponse,
                               StreamingResponseHandler<String> handler) {
        String result = partialResponse.getResult();
        if (Utils.isNullOrBlank(result)) {
            return;
        }
        handler.onNext(result);
    }

    public static QianfanStreamingLanguageModelBuilder builder() {
        for (QianfanStreamingLanguageModelBuilderFactory factory : loadFactories(QianfanStreamingLanguageModelBuilderFactory.class)) {
            return factory.get();
        }
        return new QianfanStreamingLanguageModelBuilder();
    }

    public static class QianfanStreamingLanguageModelBuilder {
        public QianfanStreamingLanguageModelBuilder() {
            // This is public so it can be extended
            // By default with Lombok it becomes package private
        }
    }
}
