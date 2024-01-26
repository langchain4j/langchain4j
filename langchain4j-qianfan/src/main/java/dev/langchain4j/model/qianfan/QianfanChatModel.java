package dev.langchain4j.model.qianfan;


import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.*;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.qianfan.client.chat.ChatCompletionResponse;
import lombok.Builder;
import java.util.List;
import java.util.Objects;
import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.qianfan.InternalQianfanHelper.*;
import static java.util.Collections.singletonList;
import dev.langchain4j.model.qianfan.client.chat.ChatCompletionRequest;

/**
 *
 * see details here: https://cloud.baidu.com/doc/WENXINWORKSHOP/s/Nlks5zkzu
 */

public class QianfanChatModel implements ChatLanguageModel {



    private final QianfanClient client;

    private final String baseUrl;

    private final Double temperature;
    private final Float topP;
    private final String modelName;

    private final String endpoint;
    private  final Float penaltyScore;
    private final Integer maxRetries;

    private final String responseFormat;


    @Builder
    public QianfanChatModel(String baseUrl,
                            String apiKey,
                            String secretKey,
                            Double temperature,
                            Integer maxRetries,
                            Float topP,
                            String modelName,
                            String endpoint,
                            String responseFormat,
                            Float penaltyScore
                             ) {
        if (Utils.isNullOrBlank(apiKey)||Utils.isNullOrBlank(secretKey)) {
            throw new IllegalArgumentException(" api key and secret key must be defined. It can be generated here: https://console.bce.baidu.com/qianfan/ais/console/applicationConsole/application");
        }
     
        this.modelName=modelName;
        this.endpoint=Utils.isNullOrBlank(endpoint)? QianfanModelEnum.getEndpoint(modelName):endpoint;

        if (Utils.isNullOrBlank(this.endpoint) ) {
            throw new IllegalArgumentException("Qianfan is no such model name. You can see model name here: https://cloud.baidu.com/doc/WENXINWORKSHOP/s/Nlks5zkzu");
        }

        this.baseUrl = getOrDefault(baseUrl,  "https://aip.baidubce.com");

        this.client = QianfanClient.builder().baseUrl(this.baseUrl).apiKey(apiKey).secretKey(secretKey).logStreamingResponses(true).build();
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
        throw new RuntimeException("Not supported");
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

            if(Utils.isNotNullOrBlank(responseFormat)){
                builder.response_format(responseFormat);
            }


            if (toolSpecifications != null && !toolSpecifications.isEmpty()) {
                builder.functions(toFunctions(toolSpecifications));
            }

            if (toolThatMustBeExecuted != null) {
                throw new RuntimeException("don't support");
            }

            ChatCompletionRequest param = builder.build();


           ChatCompletionResponse response = withRetry(() -> client.chatCompletion(param, endpoint).execute(), maxRetries);


          return  Response.from(aiMessageFrom(response),
                    tokenUsageFrom(response), finishReasonFrom(response.getFinish_reason()));


    }



}
