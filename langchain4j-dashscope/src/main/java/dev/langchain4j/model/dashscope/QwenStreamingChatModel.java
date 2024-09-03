package dev.langchain4j.model.dashscope;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
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
import dev.langchain4j.model.chat.listener.*;
import dev.langchain4j.model.dashscope.spi.QwenStreamingChatModelBuilderFactory;
import dev.langchain4j.model.output.Response;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static com.alibaba.dashscope.aigc.conversation.ConversationParam.ResultFormat.MESSAGE;
import static dev.langchain4j.internal.Utils.isNotNullOrBlank;
import static dev.langchain4j.model.dashscope.QwenHelper.*;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.util.Collections.emptyList;

/**
 * Represents a Qwen language model with a chat completion interface.
 * The model's response is streamed token by token and should be handled with {@link StreamingResponseHandler}.
 * <br>
 * More details are available <a href="https://help.aliyun.com/zh/dashscope/developer-reference/api-details">here</a>
 */
@Slf4j
public class QwenStreamingChatModel implements StreamingChatLanguageModel {
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
    public QwenStreamingChatModel(String baseUrl,
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
    public void generate(List<ChatMessage> messages, StreamingResponseHandler<AiMessage> handler) {
        if (isMultimodalModel) {
            generateByMultimodalModel(messages, handler);
        } else {
            generateByNonMultimodalModel(messages, handler);
        }
    }

    private void generateByNonMultimodalModel(List<ChatMessage> messages, StreamingResponseHandler<AiMessage> handler) {
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
                .incrementalOutput(true)
                .messages(toQwenMessages(messages))
                .resultFormat(MESSAGE);

        if (stops != null) {
            builder.stopStrings(stops);
        }

        GenerationParam param = builder.build();

        ChatModelRequest modelListenerRequest = createModelListenerRequest(param, messages, null);
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        ChatModelRequestContext requestContext = new ChatModelRequestContext(modelListenerRequest, attributes);
        listeners.forEach(listener -> {
            try {
                listener.onRequest(requestContext);
            } catch (Exception e) {
                log.warn("Exception while calling model listener", e);
            }
        });

        QwenStreamingResponseBuilder responseBuilder = new QwenStreamingResponseBuilder();
        AtomicReference<String> responseId = new AtomicReference<>();

        try {
            generation.streamCall(builder.build(), new ResultCallback<GenerationResult>() {
                @Override
                public void onEvent(GenerationResult result) {
                    String delta = responseBuilder.append(result);

                    if (isNotNullOrBlank(result.getRequestId())) {
                        responseId.set(result.getRequestId());
                    }
                    if (isNotNullOrBlank(delta)) {
                        handler.onNext(delta);
                    }
                }

                @Override
                public void onComplete() {
                    Response<AiMessage> response = responseBuilder.build();

                    ChatModelResponse modelListenerResponse = createModelListenerResponse(
                            responseId.get(),
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

                    handler.onComplete(response);
                }

                @Override
                public void onError(Exception e) {
                    Response<AiMessage> response = responseBuilder.build();

                    ChatModelResponse modelListenerPartialResponse = createModelListenerResponse(
                            responseId.get(),
                            param.getModel(),
                            response
                    );

                    ChatModelErrorContext errorContext = new ChatModelErrorContext(
                            e,
                            modelListenerRequest,
                            modelListenerPartialResponse,
                            attributes
                    );

                    listeners.forEach(listener -> {
                        try {
                            listener.onError(errorContext);
                        } catch (Exception ex) {
                            log.warn("Exception while calling model listener", ex);
                        }
                    });

                    handler.onError(e);
                }
            });
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

    private void generateByMultimodalModel(List<ChatMessage> messages, StreamingResponseHandler<AiMessage> handler) {
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

        ChatModelRequest modelListenerRequest = createModelListenerRequest(param, messages, null);
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        ChatModelRequestContext requestContext = new ChatModelRequestContext(modelListenerRequest, attributes);
        listeners.forEach(listener -> {
            try {
                listener.onRequest(requestContext);
            } catch (Exception e) {
                log.warn("Exception while calling model listener", e);
            }
        });

        QwenStreamingResponseBuilder responseBuilder = new QwenStreamingResponseBuilder();
        AtomicReference<String> responseId = new AtomicReference<>();

        try {
            conv.streamCall(param, new ResultCallback<MultiModalConversationResult>() {
                @Override
                public void onEvent(MultiModalConversationResult result) {
                    String delta = responseBuilder.append(result);

                    if (isNotNullOrBlank(result.getRequestId())) {
                        responseId.set(result.getRequestId());
                    }
                    if (isNotNullOrBlank(delta)) {
                        handler.onNext(delta);
                    }
                }

                @Override
                public void onComplete() {
                    Response<AiMessage> response = responseBuilder.build();

                    ChatModelResponse modelListenerResponse = createModelListenerResponse(
                            responseId.get(),
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

                    handler.onComplete(response);
                }

                @Override
                public void onError(Exception e) {
                    Response<AiMessage> response = responseBuilder.build();

                    ChatModelResponse modelListenerPartialResponse = createModelListenerResponse(
                            responseId.get(),
                            param.getModel(),
                            response
                    );

                    ChatModelErrorContext errorContext = new ChatModelErrorContext(
                            e,
                            modelListenerRequest,
                            modelListenerPartialResponse,
                            attributes
                    );

                    listeners.forEach(listener -> {
                        try {
                            listener.onError(errorContext);
                        } catch (Exception ex) {
                            log.warn("Exception while calling model listener", ex);
                        }
                    });

                    handler.onError(e);
                }
            });
        } catch (NoApiKeyException | UploadFileException | InputRequiredException | RuntimeException e) {
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

    public static QwenStreamingChatModelBuilder builder() {
        for (QwenStreamingChatModelBuilderFactory factory : loadFactories(QwenStreamingChatModelBuilderFactory.class)) {
            return factory.get();
        }
        return new QwenStreamingChatModelBuilder();
    }

    public static class QwenStreamingChatModelBuilder {
        public QwenStreamingChatModelBuilder() {
            // This is public so it can be extended
            // By default with Lombok it becomes package private
        }
    }
}
