package dev.langchain4j.model.dashscope;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.exception.UploadFileException;
import com.alibaba.dashscope.protocol.Protocol;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.listener.*;
import dev.langchain4j.model.dashscope.spi.QwenChatModelBuilderFactory;
import dev.langchain4j.model.output.Response;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.alibaba.dashscope.aigc.conversation.ConversationParam.ResultFormat.MESSAGE;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.model.dashscope.QwenHelper.*;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.util.Collections.emptyList;

/**
 * Represents a Qwen language model with a chat completion interface.
 * More details are available <a href="https://help.aliyun.com/zh/dashscope/developer-reference/api-details">here</a>.
 */
@Slf4j
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
    private final List<ChatModelListener> listeners;

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
                            Integer maxTokens,
                            List<ChatModelListener> listeners) {
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
        this.listeners = listeners == null ? emptyList() : new ArrayList<>(listeners);
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
        return isMultimodalModel ?
                generateByMultimodalModel(messages, null, null) :
                generateByNonMultimodalModel(messages, null, null);
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
        return isMultimodalModel ?
                generateByMultimodalModel(messages, toolSpecifications, null) :
                generateByNonMultimodalModel(messages, toolSpecifications, null);
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, ToolSpecification toolSpecification) {
        return isMultimodalModel ?
                generateByMultimodalModel(messages, null, toolSpecification) :
                generateByNonMultimodalModel(messages, null, toolSpecification);
    }

    private Response<AiMessage> generateByNonMultimodalModel(List<ChatMessage> messages,
                                                             List<ToolSpecification> toolSpecifications,
                                                             ToolSpecification toolThatMustBeExecuted) {

        GenerationParam.GenerationParamBuilder<?, ?> builder = GenerationParam.builder()
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

        if (!isNullOrEmpty(toolSpecifications)) {
            builder.tools(toToolFunctions(toolSpecifications));
        } else if (toolThatMustBeExecuted != null) {
            builder.tools(toToolFunctions(Collections.singleton(toolThatMustBeExecuted)));
            builder.toolChoice(toToolFunction(toolThatMustBeExecuted));
        }

        GenerationParam param = builder.build();

        ChatModelRequest modelListenerRequest = createModelListenerRequest(param, messages, toolSpecifications);
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        ChatModelRequestContext requestContext = new ChatModelRequestContext(modelListenerRequest, attributes);
        listeners.forEach(listener -> {
            try {
                listener.onRequest(requestContext);
            } catch (Exception e) {
                log.warn("Exception while calling model listener", e);
            }
        });

        try {
            GenerationResult result = generation.call(param);
            Response<AiMessage> response = Response.from(
                    aiMessageFrom(result),
                    tokenUsageFrom(result),
                    finishReasonFrom(result)
            );

            ChatModelResponse modelListenerResponse = createModelListenerResponse(
                    result.getRequestId(),
                    param.getModel(),
                    response
            );
            ChatModelResponseContext responseContext = new ChatModelResponseContext(
                    modelListenerResponse,
                    modelListenerRequest,
                    attributes
            );
            listeners.forEach(listener -> {
                try {
                    listener.onResponse(responseContext);
                } catch (Exception e) {
                    log.warn("Exception while calling model listener", e);
                }
            });

            return response;
        } catch (NoApiKeyException | InputRequiredException | RuntimeException e) {
            ChatModelErrorContext errorContext = new ChatModelErrorContext(
                    e,
                    modelListenerRequest,
                    null,
                    attributes
            );

            listeners.forEach(listener -> {
                try {
                    listener.onError(errorContext);
                } catch (Exception e2) {
                    log.warn("Exception while calling model listener", e2);
                }
            });

            throw e instanceof RuntimeException ?
                    (RuntimeException) e : new RuntimeException(e);
        }
    }

    private Response<AiMessage> generateByMultimodalModel(List<ChatMessage> messages,
                                                          List<ToolSpecification> toolSpecifications,
                                                          ToolSpecification toolThatMustBeExecuted) {
        if (toolThatMustBeExecuted != null || !isNullOrEmpty(toolSpecifications)) {
            throw new IllegalArgumentException("Tools are currently not supported by this model");
        }

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

        ChatModelRequest modelListenerRequest = createModelListenerRequest(param, messages, toolSpecifications);
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        ChatModelRequestContext requestContext = new ChatModelRequestContext(modelListenerRequest, attributes);
        listeners.forEach(listener -> {
            try {
                listener.onRequest(requestContext);
            } catch (Exception e) {
                log.warn("Exception while calling model listener", e);
            }
        });

        try {
            MultiModalConversationResult result = conv.call(param);
            String answer = answerFrom(result);

            Response<AiMessage> response = Response.from(AiMessage.from(answer),
                    tokenUsageFrom(result), finishReasonFrom(result));

            ChatModelResponse modelListenerResponse = createModelListenerResponse(
                    result.getRequestId(),
                    param.getModel(),
                    response
            );
            ChatModelResponseContext responseContext = new ChatModelResponseContext(
                    modelListenerResponse,
                    modelListenerRequest,
                    attributes
            );
            listeners.forEach(listener -> {
                try {
                    listener.onResponse(responseContext);
                } catch (Exception e) {
                    log.warn("Exception while calling model listener", e);
                }
            });

            return response;
        } catch (NoApiKeyException | UploadFileException | RuntimeException e) {
            ChatModelErrorContext errorContext = new ChatModelErrorContext(
                    e,
                    modelListenerRequest,
                    null,
                    attributes
            );

            listeners.forEach(listener -> {
                try {
                    listener.onError(errorContext);
                } catch (Exception e2) {
                    log.warn("Exception while calling model listener", e2);
                }
            });

            throw e instanceof RuntimeException ?
                    (RuntimeException) e : new RuntimeException(e);
        }
    }

    public static QwenChatModelBuilder builder() {
        for (QwenChatModelBuilderFactory factory : loadFactories(QwenChatModelBuilderFactory.class)) {
            return factory.get();
        }
        return new QwenChatModelBuilder();
    }

    public static class QwenChatModelBuilder {
        public QwenChatModelBuilder() {
            // This is public so it can be extended
            // By default with Lombok it becomes package private
        }
    }
}
