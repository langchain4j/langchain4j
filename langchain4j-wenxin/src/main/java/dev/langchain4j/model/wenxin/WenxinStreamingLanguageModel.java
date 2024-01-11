package dev.langchain4j.model.wenxin;


import cn.hutool.core.util.StrUtil;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.language.StreamingLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.wenxin.client.SyncOrAsyncOrStreaming;
import dev.langchain4j.model.wenxin.client.completion.CompletionRequest;
import dev.langchain4j.model.wenxin.client.completion.CompletionResponse;
import lombok.Builder;
import static dev.langchain4j.internal.Utils.getOrDefault;

/**
 *
 * see details here: https://cloud.baidu.com/doc/WENXINWORKSHOP/s/Nlks5zkzu
 */
public class WenxinStreamingLanguageModel implements StreamingLanguageModel {



    private final BaiduClient client;

    private final String baseUrl;

    private final Float temperature;
    private final Float topP;
    private final String modelName;


    private  final Float penaltyScore;
    private final Integer maxRetries;

    private final Integer topK;

    private final String serviceName;
    @Builder
    public WenxinStreamingLanguageModel(String baseUrl,
                                        String clientId,
                                        String secretKey,
                                        Float temperature,
                                        Integer maxRetries,
                                        Integer topK,
                                        Float topP,
                                        String modelName,
                                        String serviceName,
                                        Float penaltyScore
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
        this.temperature = getOrDefault(temperature, 0.7f);
        this.maxRetries = getOrDefault(maxRetries, 3);
        this.topP = topP;
        this.topK = topK;
        this.penaltyScore = penaltyScore;
    }

    @Override
    public void generate(String prompt, StreamingResponseHandler<String> handler) {

        dev.langchain4j.model.wenxin.client.completion.CompletionRequest request = CompletionRequest.builder()
                .prompt(prompt)
                .top_k(topK)
                .top_p(topP)
                .temperature(temperature)
                .penalty_score(penaltyScore)
                .build();


        WenxinStreamingResponseBuilder responseBuilder = new WenxinStreamingResponseBuilder(null);


        SyncOrAsyncOrStreaming<CompletionResponse> response = client.completion(request, true, serviceName);


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
        if (StrUtil.isEmpty(result)) {
            return;
        }
        handler.onNext(result);
    }

}
