package dev.langchain4j.model.qianfan;


import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.qianfan.client.QianfanClient;
import dev.langchain4j.model.qianfan.client.QianfanStreamingResponseBuilder;
import dev.langchain4j.model.qianfan.client.SyncOrAsyncOrStreaming;
import dev.langchain4j.model.qianfan.client.chat.ChatCompletionRequest;
import dev.langchain4j.model.qianfan.client.chat.ChatCompletionResponse;
import dev.langchain4j.model.qianfan.spi.QianfanStreamingChatModelBuilderFactory;
import dev.langchain4j.spi.ServiceHelper;
import lombok.Builder;
import static dev.langchain4j.model.qianfan.InternalQianfanHelper.*;
import java.util.List;
import static dev.langchain4j.internal.Utils.getOrDefault;

/**
 *
 * see details here: https://cloud.baidu.com/doc/WENXINWORKSHOP/s/Nlks5zkzu
 */

public class QianfanStreamingChatModel implements StreamingChatLanguageModel  {

    private final QianfanClient client;

    private final String baseUrl;

    private final Double temperature;
    private final Double topP;
    private final String modelName;


    private final String endpoint;
    private  final Double penaltyScore;

    private final String responseFormat;

    @Builder
    public QianfanStreamingChatModel(String baseUrl,
                                     String apiKey,
                                     String secretKey,
                                     Double temperature,
                                     Double topP,
                                     String modelName,
                                     String endpoint,
                                     String responseFormat,
                                     Double penaltyScore,
                                     Boolean logRequests,
                                     Boolean logResponses
                             ) {
        if (Utils.isNullOrBlank(apiKey)||Utils.isNullOrBlank(secretKey)) {
            throw new IllegalArgumentException(" api key and secret key must be defined. It can be generated here: https://console.bce.baidu.com/qianfan/ais/console/applicationConsole/application");
        }
        this.modelName=modelName;
        this.endpoint=Utils.isNullOrBlank(endpoint)? QianfanChatModelNameEnum.getEndpoint(modelName):endpoint;

        if (Utils.isNullOrBlank(this.endpoint)) {
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
        this.topP = topP;
        this.penaltyScore = penaltyScore;
        this.responseFormat = responseFormat;

    }



    @Override
    public void generate(List<ChatMessage> messages, StreamingResponseHandler<AiMessage> handler) {
        generate(messages, null, null, handler);
    }

    @Override
    public void generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications, StreamingResponseHandler<AiMessage> handler) {
        generate(messages, toolSpecifications, null, handler);
    }

    @Override
    public void generate(List<ChatMessage> messages, ToolSpecification toolSpecification, StreamingResponseHandler<AiMessage> handler) {
        throw new RuntimeException("Not supported");
    }

    private void generate(List<ChatMessage> messages,
                          List<ToolSpecification> toolSpecifications,
                          ToolSpecification toolThatMustBeExecuted,
                          StreamingResponseHandler<AiMessage> handler
    ) {
        ChatCompletionRequest.Builder builder = ChatCompletionRequest.builder()
                .messages(InternalQianfanHelper.toOpenAiMessages(messages))
                .temperature(temperature)
                .topP(topP)
                .system(getSystemMessage(messages))
                .responseFormat(responseFormat)
                .penaltyScore(penaltyScore);


        if (toolSpecifications != null && !toolSpecifications.isEmpty()) {
            builder.functions(InternalQianfanHelper.toFunctions(toolSpecifications));
        }


        ChatCompletionRequest request = builder.build();

        QianfanStreamingResponseBuilder responseBuilder = new QianfanStreamingResponseBuilder(null);

        SyncOrAsyncOrStreaming<ChatCompletionResponse> response = client.chatCompletion(request, endpoint);

        response.onPartialResponse(partialResponse -> {
            responseBuilder.append(partialResponse);
            handle(partialResponse, handler);
        })
                .onComplete(() -> {
                    Response<AiMessage> messageResponse = responseBuilder.build();
                    handler.onComplete(messageResponse);
                })
                .onError(handler::onError
                )
                .execute();

    }

    private static void handle(ChatCompletionResponse partialResponse,
                               StreamingResponseHandler<AiMessage> handler) {
        String result = partialResponse.getResult();
        if (Utils.isNullOrBlank(result)) {
            return;
        }
        handler.onNext(result);
    }

    public static QianfanStreamingChatModelBuilder builder() {
        return ServiceHelper.loadFactoryService(
                QianfanStreamingChatModelBuilderFactory.class,
                QianfanStreamingChatModelBuilder::new
        );
    }

    public static class QianfanStreamingChatModelBuilder {
        public QianfanStreamingChatModelBuilder() {
            // This is public so it can be extended
            // By default with Lombok it becomes package private
        }
    }
}
