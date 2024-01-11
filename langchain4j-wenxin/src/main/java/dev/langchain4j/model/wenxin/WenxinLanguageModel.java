package dev.langchain4j.model.wenxin;



import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.language.LanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.wenxin.client.completion.CompletionRequest;
import dev.langchain4j.model.wenxin.client.completion.CompletionResponse;
import lombok.Builder;
import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.wenxin.InternalWenxinHelper.*;


/**
 *
 * see details here: https://cloud.baidu.com/doc/WENXINWORKSHOP/s/Nlks5zkzu
 */
public class WenxinLanguageModel implements LanguageModel {



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
    public WenxinLanguageModel(String baseUrl,
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
    public Response<String> generate(String prompt) {

        CompletionRequest request = CompletionRequest.builder()
                .prompt(prompt)
                .top_k(topK)
                .top_p(topP)
                .temperature(temperature)
                .penalty_score(penaltyScore)
                .build();


        CompletionResponse response = withRetry(() -> client.completion(request,false,serviceName).execute(), maxRetries);

        return Response.from(
                response.getResult(),
                tokenUsageFrom(response),
                finishReasonFrom(response)
        );
    }


}
