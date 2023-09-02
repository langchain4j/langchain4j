package dev.langchain4j.model.dashscope;

import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.aigc.generation.models.QwenParam;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.ResultCallback;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;

import java.util.List;

public class QwenStreamingChatModel extends QwenChatModel implements StreamingChatLanguageModel {
    protected QwenStreamingChatModel(String apiKey,
                                     String modelName,
                                     Double topP,
                                     Integer topK,
                                     Boolean enableSearch,
                                     Integer seed) {
        super(apiKey, modelName, topP, topK, enableSearch, seed);
    }

    @Override
    public void sendMessages(List<ChatMessage> messages, StreamingResponseHandler handler) {
        sendMessage(null, QwenHelper.toQwenMessages(messages), handler);
    }

    protected void sendMessage(String rawPrompt, List<Message> messages, StreamingResponseHandler handler) {
        QwenParam param = QwenParam.builder()
                .apiKey(apiKey)
                .model(modelName)
                .topP(topP)
                .topK(topK)
                .enableSearch(enableSearch)
                .seed(seed)
                .prompt(rawPrompt)
                .messages(messages)
                .resultFormat(QwenParam.ResultFormat.MESSAGE)
                .build();

        try {
            gen.streamCall(param, new ResultCallback<GenerationResult>() {
                @Override
                public void onEvent(GenerationResult result) {
                    handler.onNext(QwenHelper.answerFrom(result));
                }
                @Override
                public void onComplete() {
                    handler.onComplete();
                }
                @Override
                public void onError(Exception e) {
                    handler.onError(e);
                }
            });
        } catch (NoApiKeyException | InputRequiredException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void sendMessages(List<ChatMessage> messages,
                             List<ToolSpecification> toolSpecifications,
                             StreamingResponseHandler handler) {
        throw new IllegalArgumentException("Tools are currently not supported for qwen models");
    }

    @Override
    public void sendMessages(List<ChatMessage> messages,
                             ToolSpecification toolSpecification,
                             StreamingResponseHandler handler) {
        throw new IllegalArgumentException("Tools are currently not supported for qwen models");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends QwenChatModel.Builder {
        public Builder apiKey(String apiKey) {
            return (Builder) super.apiKey(apiKey);
        }

        public Builder modelName(String modelName) {
            return (Builder) super.modelName(modelName);
        }

        public Builder topP(Double topP) {
            return (Builder) super.topP(topP);
        }

        public Builder topK(Integer topK) {
            return (Builder) super.topK(topK);
        }

        public Builder enableSearch(Boolean enableSearch) {
            return (Builder) super.enableSearch(enableSearch);
        }

        public Builder seed(Integer seed) {
            return (Builder) super.seed(seed);
        }

        public QwenStreamingChatModel build() {
            ensureOptions();
            return new QwenStreamingChatModel(apiKey, modelName, topP, topK, enableSearch, seed);
        }
    }
}
