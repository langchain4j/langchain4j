package dev.langchain4j.model.dashscope;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.aigc.generation.models.QwenParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.exception.UploadFileException;
import com.alibaba.dashscope.protocol.Protocol;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.dashscope.spi.QwenChatModelBuilderFactory;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.spi.ServiceHelper;
import lombok.Builder;

import java.util.List;

import static com.alibaba.dashscope.aigc.generation.models.QwenParam.ResultFormat.MESSAGE;
import static dev.langchain4j.model.dashscope.QwenHelper.*;

public class QwenChatModel implements ChatLanguageModel {
    private final String apiKey;
    private final String modelName;
    private final Double topP;
    private final Integer topK;
    private final Boolean enableSearch;
    private final Integer seed;
    private final Float repetitionPenalty;
    private final Float temperature;
    private final List<String> stops;
    private final Integer maxTokens;
    private final Generation generation;
    private final MultiModalConversation conv;
    private final boolean isMultimodalModel;

    @Builder
    protected QwenChatModel(String baseUrl,
                            String apiKey,
                            String modelName,
                            Double topP,
                            Integer topK,
                            Boolean enableSearch,
                            Integer seed,
                            Float repetitionPenalty,
                            Float temperature,
                            List<String> stops,
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
        this.repetitionPenalty = repetitionPenalty;
        this.temperature = temperature;
        this.stops = stops;
        this.maxTokens = maxTokens;
        this.isMultimodalModel = QwenHelper.isMultimodalModel(modelName);

        if (Utils.isNullOrBlank(baseUrl)) {
            this.conv = isMultimodalModel ? new MultiModalConversation() : null;
            this.generation = isMultimodalModel ? null : new Generation();
        } else if (baseUrl.startsWith("wss://")) {
            this.conv = isMultimodalModel ? new MultiModalConversation(Protocol.WEBSOCKET.getValue(), baseUrl) : null;
            this.generation = isMultimodalModel ? null : new Generation(Protocol.WEBSOCKET.getValue(), baseUrl);
        } else {
            this.conv = isMultimodalModel ? new MultiModalConversation(Protocol.HTTP.getValue(), baseUrl) : null;
            this.generation = isMultimodalModel ? null : new Generation(Protocol.HTTP.getValue(), baseUrl);
        }
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        return isMultimodalModel ? generateByMultimodalModel(messages) : generateByNonMultimodalModel(messages);
    }

    private Response<AiMessage> generateByNonMultimodalModel(List<ChatMessage> messages) {
        try {
            QwenParam.QwenParamBuilder<?, ?> builder = QwenParam.builder()
                    .apiKey(apiKey)
                    .model(modelName)
                    .topP(topP)
                    .topK(topK)
                    .enableSearch(enableSearch)
                    .seed(seed)
                    .repetitionPenalty(repetitionPenalty)
                    .temperature(temperature)
                    .maxTokens(maxTokens)
                    .messages(toQwenMessages(messages))
                    .resultFormat(MESSAGE);

            if (stops != null) {
                builder.stopStrings(stops);
            }

            GenerationResult generationResult = generation.call(builder.build());
            String answer = answerFrom(generationResult);

            return Response.from(AiMessage.from(answer),
                    tokenUsageFrom(generationResult), finishReasonFrom(generationResult));
        } catch (NoApiKeyException | InputRequiredException e) {
            throw new RuntimeException(e);
        }
    }

    private Response<AiMessage> generateByMultimodalModel(List<ChatMessage> messages) {
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

    public static QwenChatModelBuilder builder() {
        return ServiceHelper.loadFactoryService(
                QwenChatModelBuilderFactory.class,
                QwenChatModelBuilder::new
        );
    }

    public static class QwenChatModelBuilder {
        public QwenChatModelBuilder() {
            // This is public so it can be extended
            // By default with Lombok it becomes package private
        }
    }
}
