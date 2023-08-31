package dev.langchain4j.model.dashscope;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationOutput;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.aigc.generation.models.QwenParam;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Result;

import java.util.List;
import java.util.Optional;

import static dev.langchain4j.model.dashscope.QwenParamHelper.toQwenPrompt;

public class QwenChatModel implements ChatLanguageModel {

    protected final String apiKey;
    protected final String modelName;
    protected final Double topP;
    protected final Double topK;
    protected final Boolean enableSearch;
    protected final Integer seed;
    protected final Generation generation;

    public QwenChatModel(String apiKey,
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
        this.generation = new Generation();
    }

    @Override
    public Result<AiMessage> generate(List<ChatMessage> messages) {
        String generatedText = generateWithFallback(toQwenPrompt(messages));
        return Result.from(AiMessage.from(generatedText));
    }

    private String generateWithFallback(String prompt) {
        GenerationResult result = doGenerate(prompt);
        return Optional.of(result)
                .map(GenerationResult::getOutput)
                .map(GenerationOutput::getText)
                .orElse("Oops, something wrong...[request id: " + result.getRequestId() + "]");
    }

    private GenerationResult doGenerate(String prompt) {
        QwenParam param = QwenParam.builder()
                .apiKey(apiKey)
                .model(modelName)
                .topP(topP)
                .topK(topK)
                .enableSearch(enableSearch)
                .seed(seed)
                .prompt(prompt)
                .build();
        try {
            return generation.call(param);
        } catch (NoApiKeyException | InputRequiredException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Result<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
        throw new IllegalArgumentException("Tools are currently not supported for qwen models");
    }

    @Override
    public Result<AiMessage> generate(List<ChatMessage> messages, ToolSpecification toolSpecification) {
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
            modelName = Utils.isNullOrBlank(modelName) ? QwenModelName.QWEN_V1 : modelName;
            topP = topP == null ? 0.8 : topP;
            topK = topK == null ? 100.0 : topK;
            enableSearch = enableSearch == null ? Boolean.FALSE : enableSearch;
            seed = seed == null ? 1234 : seed;
        }

        public QwenChatModel build() {
            ensureOptions();
            return new QwenChatModel(apiKey, modelName, topP, topK, enableSearch, seed);
        }
    }
}
