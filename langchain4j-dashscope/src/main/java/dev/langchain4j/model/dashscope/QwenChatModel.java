package dev.langchain4j.model.dashscope;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.aigc.generation.models.QwenParam;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;

import java.util.List;

import static com.alibaba.dashscope.aigc.generation.models.QwenParam.ResultFormat.MESSAGE;
import static dev.langchain4j.model.dashscope.QwenHelper.*;

public class QwenChatModel implements ChatLanguageModel {

    protected final String apiKey;
    protected final String modelName;
    protected final Double topP;
    protected final Integer topK;
    protected final Boolean enableSearch;
    protected final Integer seed;
    protected final Generation generation;

    protected QwenChatModel(String apiKey,
                            String modelName,
                            Double topP,
                            Integer topK,
                            Boolean enableSearch,
                            Integer seed) {
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.topP = topP;
        this.topK = topK;
        this.enableSearch = enableSearch;
        this.seed = seed;
        this.generation = new Generation();
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        try {
            QwenParam param = QwenParam.builder()
                    .apiKey(apiKey)
                    .model(modelName)
                    .topP(topP)
                    .topK(topK)
                    .enableSearch(enableSearch)
                    .seed(seed)
                    .messages(toQwenMessages(messages))
                    .resultFormat(MESSAGE)
                    .build();

            GenerationResult generationResult = generation.call(param);
            String answer = answerFrom(generationResult);

            return Response.from(AiMessage.from(answer),
                    tokenUsageFrom(generationResult), finishReasonFrom(generationResult));
        } catch (NoApiKeyException | InputRequiredException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
        throw new IllegalArgumentException("Tools are currently not supported for qwen models");
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, ToolSpecification toolSpecification) {
        throw new IllegalArgumentException("Tools are currently not supported for qwen models");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        protected String apiKey;
        protected String modelName;
        protected Double topP;
        protected Integer topK;
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

        public Builder topK(Integer topK) {
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
            modelName = Utils.isNullOrBlank(modelName) ? QwenModelName.QWEN_PLUS : modelName;
            enableSearch = enableSearch != null && enableSearch;
        }

        public QwenChatModel build() {
            ensureOptions();
            return new QwenChatModel(apiKey, modelName, topP, topK, enableSearch, seed);
        }
    }
}
