package dev.langchain4j.model.dashscope;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.aigc.generation.models.QwenParam;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.chat.ChatLanguageModel;

import java.util.List;

public class QwenChatModel implements ChatLanguageModel {
    protected final Generation gen;
    protected final String apiKey;
    protected final String modelName;
    protected final Double topP;
    protected final Double topK;
    protected final Boolean enableSearch;
    protected final Integer seed;

    protected QwenChatModel(String apiKey,
                            String modelName,
                            Double topP,
                            Double topK,
                            Boolean enableSearch,
                            Integer seed) {

        this.apiKey = apiKey;
        this.modelName = modelName;
        this.topP = topP;
        this.topK = topK;
        this.enableSearch = enableSearch;
        this.seed = seed;
        gen = new Generation();
    }

    @Override
    public AiMessage sendMessages(List<ChatMessage> messages) {
        return AiMessage.aiMessage(sendMessage(null, QwenHelper.toQwenMessages(messages)));
    }

    protected String sendMessage(String prompt, List<Message> messages) {
        return QwenHelper.answerFrom(doSendMessage(prompt, messages));
    }

    protected GenerationResult doSendMessage(String prompt, List<Message> messages) {
        QwenParam param = QwenParam.builder()
                .apiKey(apiKey)
                .model(modelName)
                .topP(topP)
                .topK(topK)
                .enableSearch(enableSearch)
                .seed(seed)
                .prompt(prompt)
                .messages(messages)
                .resultFormat(QwenParam.ResultFormat.MESSAGE)
                .build();

        try {
            return gen.call(param);
        } catch (NoApiKeyException | InputRequiredException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public AiMessage sendMessages(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
        throw new IllegalArgumentException("Tools are currently not supported for qwen models");
    }

    @Override
    public AiMessage sendMessages(List<ChatMessage> messages, ToolSpecification toolSpecification) {
        throw new IllegalArgumentException("Tools are currently not supported for qwen models");
    }

    public static Builder builder() {
        return new Builder();
    }
    public static class Builder {
        protected String apiKey;
        protected String modelName;
        protected Double topP;
        protected Double topK;
        protected Boolean enableSearch;
        protected Integer seed;

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public Builder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public Builder topK(Double topK) {
            this.topK = topK;
            return this;
        }

        public Builder enableSearch(Boolean enableSearch) {
            this.enableSearch = enableSearch;
            return this;
        }

        public Builder seed(Integer seed) {
            this.seed = seed;
            return this;
        }

        protected void ensureOptions() {
            if (Utils.isNullOrBlank(apiKey)) {
                throw new IllegalArgumentException("DashScope api key must be defined. It can be generated here: https://dashscope.console.aliyun.com/apiKey");
            }
            modelName = Utils.isNullOrBlank(modelName) ? QwenModelName.QWEN_PLUS_V1 : modelName;
            enableSearch = enableSearch != null && enableSearch;
        }

        public QwenChatModel build() {
            ensureOptions();
            return new QwenChatModel(apiKey, modelName, topP, topK, enableSearch, seed);
        }
    }
}
