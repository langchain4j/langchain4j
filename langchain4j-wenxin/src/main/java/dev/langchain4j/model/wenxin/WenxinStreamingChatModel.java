package dev.langchain4j.model.wenxin;


import cn.hutool.core.util.StrUtil;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.wenxin.client.SyncOrAsyncOrStreaming;
import dev.langchain4j.model.wenxin.client.chat.ChatCompletionRequest;
import dev.langchain4j.model.wenxin.client.chat.ChatCompletionResponse;
import lombok.Builder;
import static dev.langchain4j.model.wenxin.InternalWenxinHelper.*;
import java.util.List;
import java.util.Objects;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static java.util.Collections.singletonList;

/**
 *
 * see details here: https://cloud.baidu.com/doc/WENXINWORKSHOP/s/Nlks5zkzu
 */

public class WenxinStreamingChatModel implements StreamingChatLanguageModel  {

    private final BaiduClient client;

    private final String baseUrl;

    private final Double temperature;
    private final Float topP;
    private final String modelName;


    private final String serviceName;
    private  final Float penaltyScore;

    private final String responseFormat;

    @Builder
    public WenxinStreamingChatModel(String baseUrl,
                                    String clientId,
                                    String secretKey,
                                    Double temperature,
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

        if (Utils.isNullOrBlank(this.serviceName)) {
            throw new IllegalArgumentException("baidu is no such model name. You can see model name here: https://cloud.baidu.com/doc/WENXINWORKSHOP/s/Nlks5zkzu");
        }

        this.baseUrl = getOrDefault(baseUrl,  "https://aip.baidubce.com");
        this.client = BaiduClient.builder().baseUrl(this.baseUrl).wenXinApiKey(clientId).wenXinSecretKey(secretKey).logStreamingResponses(true).build();
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
        generate(messages, singletonList(toolSpecification), toolSpecification, handler);
    }

    private void generate(List<ChatMessage> messages,
                          List<ToolSpecification> toolSpecifications,
                          ToolSpecification toolThatMustBeExecuted,
                          StreamingResponseHandler<AiMessage> handler
    ) {
        dev.langchain4j.model.wenxin.client.chat.ChatCompletionRequest.Builder builder = dev.langchain4j.model.wenxin.client.chat.ChatCompletionRequest.builder()
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
            builder.functions(InternalWenxinHelper.toFunctions(toolSpecifications));
        }

        if (toolThatMustBeExecuted != null) {
            throw new RuntimeException("don't support");
        }

        ChatCompletionRequest request = builder.build();

        WenxinStreamingResponseBuilder responseBuilder = new WenxinStreamingResponseBuilder(null);

        SyncOrAsyncOrStreaming<ChatCompletionResponse> response = client.chatCompletion(request, serviceName);

//handler::onError
        response.onPartialResponse(partialResponse -> {
            responseBuilder.append(partialResponse);
            handle(partialResponse, handler);
        })
                .onComplete(() -> {
                    Response<AiMessage> messageResponse = responseBuilder.build();
                    handler.onComplete(messageResponse);
                })
                .onError(throwable-> System.out.println("111"+throwable.getMessage())


                )
                .execute();

    }

    private static void handle(ChatCompletionResponse partialResponse,
                               StreamingResponseHandler<AiMessage> handler) {
        String result = partialResponse.getResult();
        if (StrUtil.isEmpty(result)) {
            return;
        }
        handler.onNext(result);
    }

}
