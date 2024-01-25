package dev.langchain4j.model.dashscope;

import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.ResultCallback;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.exception.UploadFileException;
import com.alibaba.dashscope.protocol.Protocol;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.dashscope.spi.QwenMultiModalStreamingChatModelBuilderFactory;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.spi.ServiceHelper;
import lombok.Builder;

import java.util.List;

import static dev.langchain4j.model.dashscope.QwenHelper.*;

public class QwenMultiModalStreamingChatModel implements StreamingChatLanguageModel {
    private final String apiKey;
    private final String modelName;
    private final Double topP;
    private final Integer topK;
    private final Boolean enableSearch;
    private final Integer seed;
    private final Float temperature;
    private final Integer maxTokens;
    private final MultiModalConversation conv;

    @Builder
    protected QwenMultiModalStreamingChatModel(String baseUrl,
                                               String apiKey,
                                               String modelName,
                                               Double topP,
                                               Integer topK,
                                               Boolean enableSearch,
                                               Integer seed,
                                               Float temperature,
                                               Integer maxTokens) {
        if (Utils.isNullOrBlank(apiKey)) {
            throw new IllegalArgumentException("DashScope api key must be defined. It can be generated here: https://dashscope.console.aliyun.com/apiKey");
        }
        this.modelName = Utils.isNullOrBlank(modelName) ? QwenModelName.QWEN_PLUS : modelName;
        this.enableSearch = enableSearch != null && enableSearch;
        this.apiKey = apiKey;
        this.topP = topP;
        this.topK = topK;
        this.seed = seed;
        this.temperature = temperature;
        this.maxTokens = maxTokens;

        if (Utils.isNullOrBlank(baseUrl)) {
            this.conv = new MultiModalConversation();
        } else if (baseUrl.startsWith("wss://")) {
            this.conv = new MultiModalConversation(Protocol.WEBSOCKET.getValue(), baseUrl);
        } else {
            this.conv = new MultiModalConversation(Protocol.HTTP.getValue(), baseUrl);
        }

    }

    public Response<AiMessage> generate(List<ChatMessage> messages) {
        try {
            MultiModalConversationParam param = MultiModalConversationParam.builder()
                    .apiKey(apiKey)
                    .model(modelName)
                    .topP(topP)
                    .topK(topK)
                    .enableSearch(enableSearch)
                    .seed(seed)
                    .temperature(temperature)
                    .maxLength(maxTokens)
                    .messages(toQwenMultiModalMessages(messages))
                    .build();

            MultiModalConversationResult result = conv.call(param);
            String answer = answerFrom(result);

            return Response.from(AiMessage.from(answer),
                    tokenUsageFrom(result), finishReasonFrom(result));
        } catch (NoApiKeyException | UploadFileException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void generate(List<ChatMessage> messages, StreamingResponseHandler<AiMessage> handler) {
        try {
            MultiModalConversationParam param = MultiModalConversationParam.builder()
                    .apiKey(apiKey)
                    .model(modelName)
                    .topP(topP)
                    .topK(topK)
                    .enableSearch(enableSearch)
                    .seed(seed)
                    .temperature(temperature)
                    .maxLength(maxTokens)
                    .incrementalOutput(true)
                    .messages(toQwenMultiModalMessages(messages))
                    .build();

            QwenStreamingResponseBuilder responseBuilder = new QwenStreamingResponseBuilder();

            conv.streamCall(param, new ResultCallback<MultiModalConversationResult>() {
                @Override
                public void onEvent(MultiModalConversationResult result) {
                    String delta = responseBuilder.append(result);
                    if (Utils.isNotNullOrBlank(delta)) {
                        handler.onNext(delta);
                    }
                }

                @Override
                public void onComplete() {
                    handler.onComplete(responseBuilder.build());
                }

                @Override
                public void onError(Exception e) {
                    handler.onError(e);
                }
            });
        } catch (NoApiKeyException | UploadFileException | InputRequiredException e) {
            throw new RuntimeException(e);
        }
    }


    public static QwenMultiModalStreamingChatModel.QwenMultiModalStreamingChatModelBuilder builder() {
        return ServiceHelper.loadFactoryService(
                QwenMultiModalStreamingChatModelBuilderFactory.class,
                QwenMultiModalStreamingChatModel.QwenMultiModalStreamingChatModelBuilder::new
        );
    }

    public static class QwenMultiModalStreamingChatModelBuilder {
        public QwenMultiModalStreamingChatModelBuilder() {
            // This is public so it can be extended
            // By default with Lombok it becomes package private
        }
    }
}
