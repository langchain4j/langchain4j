package dev.langchain4j.model.dashscope;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.aigc.generation.models.QwenParam;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import dev.langchain4j.model.language.LanguageModel;
import dev.langchain4j.model.output.Response;

import static com.alibaba.dashscope.aigc.generation.models.QwenParam.ResultFormat.MESSAGE;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.model.dashscope.QwenHelper.*;
import static dev.langchain4j.model.dashscope.QwenModelName.QWEN_PLUS;

public class QwenLanguageModel implements LanguageModel {

    protected final String apiKey;
    protected final String modelName;
    protected final Double topP;
    protected final Integer topK;
    protected final Boolean enableSearch;
    protected final Integer seed;
    protected final Generation generation;

    public QwenLanguageModel(String apiKey,
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
    public Response<String> generate(String prompt) {
        try {
            QwenParam param = QwenParam.builder()
                    .apiKey(apiKey)
                    .model(modelName)
                    .topP(topP)
                    .topK(topK)
                    .enableSearch(enableSearch)
                    .seed(seed)
                    .prompt(prompt)
                    .resultFormat(MESSAGE)
                    .build();

            GenerationResult generationResult = generation.call(param);

            return Response.from(answerFrom(generationResult),
                    tokenUsageFrom(generationResult), finishReasonFrom(generationResult));
        } catch (NoApiKeyException | InputRequiredException e) {
            throw new RuntimeException(e);
        }
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
            if (isNullOrBlank(apiKey)) {
                throw new IllegalArgumentException("DashScope api key must be defined. It can be generated here: https://dashscope.console.aliyun.com/apiKey");
            }
            modelName = isNullOrBlank(modelName) ? QWEN_PLUS : modelName;
            enableSearch = enableSearch != null && enableSearch;
        }

        public QwenLanguageModel build() {
            ensureOptions();
            return new QwenLanguageModel(apiKey, modelName, topP, topK, enableSearch, seed);
        }
    }
}
