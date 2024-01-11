package dev.langchain4j.model.wenxin;


import cn.hutool.core.util.StrUtil;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.*;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.wenxin.client.chat.ChatCompletionResponse;
import lombok.Builder;
import java.util.List;
import java.util.Objects;
import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.wenxin.InternalWenxinHelper.*;
import static java.util.Collections.singletonList;
import  dev.langchain4j.model.wenxin.client.chat.ChatCompletionRequest;

/**
 *
 * see details here: https://cloud.baidu.com/doc/WENXINWORKSHOP/s/Nlks5zkzu
 */

public class WenxinChatModel implements ChatLanguageModel {



    private final BaiduClient client;

    private final String baseUrl;

    private final Double temperature;
    private final Float topP;
    private final String modelName;

    private final String serviceName;
    private  final Float penaltyScore;
    private final Integer maxRetries;

    private final String responseFormat;


    @Builder
    public WenxinChatModel(String baseUrl,
                           String clientId,
                           String secretKey,
                           Double temperature,
                           Integer maxRetries,
                           Float topP,
                           String modelName,
                           String serviceName,
                           String responseFormat,
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
        this.temperature = getOrDefault(temperature, 0.7);
        this.maxRetries = getOrDefault(maxRetries, 3);
        this.topP = topP;
        this.penaltyScore = penaltyScore;
        this.responseFormat = responseFormat;
    }


    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {

          return  generate(messages, null,null);
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
        return generate(messages, toolSpecifications,null);
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, ToolSpecification toolSpecification) {
        return generate(messages, singletonList(toolSpecification), toolSpecification);
    }



    private Response<AiMessage> generate(List<ChatMessage> messages,
                                         List<ToolSpecification> toolSpecifications,
                                         ToolSpecification toolThatMustBeExecuted
    ) {

        ChatCompletionRequest.Builder builder = ChatCompletionRequest.builder()
                    .messages(toOpenAiMessages(messages))
                    .temperature(temperature)
                    .top_p(topP)
                    .penalty_score(penaltyScore);

            if(Objects.nonNull(getSystenMessage(messages))){
                builder.system(getSystenMessage(messages));
            }

            if(StrUtil.isNotEmpty(responseFormat)){
                builder.response_format(responseFormat);
            }


            if (toolSpecifications != null && !toolSpecifications.isEmpty()) {
                builder.functions(toFunctions(toolSpecifications));
            }

            if (toolThatMustBeExecuted != null) {
                throw new RuntimeException("don't support");
            }

            ChatCompletionRequest param = builder.build();


           ChatCompletionResponse response = withRetry(() -> client.chatCompletion(param, serviceName).execute(), maxRetries);


          return  Response.from(aiMessageFrom(response),
                    tokenUsageFrom(response), finishReasonFrom(response));


    }



}
