package dev.langchain4j.model.qianfan;


import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.*;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.qianfan.client.QianfanClient;
import dev.langchain4j.model.qianfan.client.chat.ChatCompletionResponse;
import dev.langchain4j.model.qianfan.spi.QianfanChatModelBuilderFactory;
import lombok.Builder;
import java.util.List;
import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.qianfan.InternalQianfanHelper.*;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

import dev.langchain4j.model.qianfan.client.chat.ChatCompletionRequest;

/**
 *
 * see details here: https://cloud.baidu.com/doc/WENXINWORKSHOP/s/Nlks5zkzu
 */

public class QianfanChatModel implements ChatLanguageModel {



    private final QianfanClient client;

    private final String baseUrl;

    private final Double temperature;
    private final Double topP;
    private final String modelName;

    private final String endpoint;
    private  final Double penaltyScore;
    private final Integer maxRetries;

    private final String responseFormat;

    private final String userId;
    private final List<String> stop;
    private final Integer maxOutputTokens;
    private final String system;

    @Builder
    public QianfanChatModel(String baseUrl,
            String apiKey,
            String secretKey,
            Double temperature,
            Integer maxRetries,
            Double topP,
            String modelName,
            String endpoint,
            String responseFormat,
            Double penaltyScore,
            Boolean logRequests,
            Boolean logResponses,
            String userId,
            List<String> stop,
            Integer maxOutputTokens,
            String system
    ) {
        if (Utils.isNullOrBlank(apiKey) || Utils.isNullOrBlank(secretKey)) {
            throw new IllegalArgumentException(
                    " api key and secret key must be defined. It can be generated here: https://console.bce.baidu.com/qianfan/ais/console/applicationConsole/application");
        }
     
        this.modelName=modelName;
        this.endpoint=Utils.isNullOrBlank(endpoint)? QianfanChatModelNameEnum.getEndpoint(modelName):endpoint;

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
        this.temperature = getOrDefault(temperature, 0.7);
        this.maxRetries = getOrDefault(maxRetries, 3);
        this.topP = topP;
        this.penaltyScore = penaltyScore;
        this.responseFormat = responseFormat;
        this.maxOutputTokens = maxOutputTokens;
        this.stop = stop;
        this.userId = userId;
        this.system = system;
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
                .topP(topP)
                .maxOutputTokens(maxOutputTokens)
                .stop(stop)
                .system(system)
                .userId(userId)
                .penaltyScore(penaltyScore)
                .responseFormat(responseFormat);
        if (system == null || system.length() == 0) {
            builder.system(getSystemMessage(messages));
        }

            if (toolSpecifications != null && !toolSpecifications.isEmpty()) {
                builder.functions(toFunctions(toolSpecifications));
            }


            ChatCompletionRequest param = builder.build();


           ChatCompletionResponse response = withRetry(() -> client.chatCompletion(param, endpoint).execute(), maxRetries);


          return  Response.from(aiMessageFrom(response),
                    tokenUsageFrom(response), finishReasonFrom(response.getFinishReason()));


    }


    public static QianfanChatModelBuilder builder() {
        for (QianfanChatModelBuilderFactory factory : loadFactories(QianfanChatModelBuilderFactory.class)) {
            return factory.get();
        }
        return new QianfanChatModelBuilder();
    }

    public static class QianfanChatModelBuilder {
        public QianfanChatModelBuilder() {
            // This is public so it can be extended
            // By default with Lombok it becomes package private
        }
    }
}
