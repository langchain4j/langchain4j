package dev.langchain4j.model.dashscope;

import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.aigc.generation.models.QwenParam;
import com.alibaba.dashscope.common.ResultCallback;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.language.StreamingLanguageModel;

public class QwenStreamingLanguageModel extends QwenLanguageModel implements StreamingLanguageModel {

    public QwenStreamingLanguageModel(String apiKey,
                                      String modelName,
                                      Double topP,
                                      Double topK,
                                      Boolean enableSearch,
                                      Integer seed) {
        super(apiKey, modelName, topP, topK, enableSearch, seed);
    }

    @Override
    public void generate(String prompt, StreamingResponseHandler handler) {
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
            generation.call(param, new ResultCallback<GenerationResult>() {
                @Override
                public void onEvent(GenerationResult result) {
                    handler.onNext(result.getOutput().getText());
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

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends QwenLanguageModel.Builder {

        public Builder apiKey(String apiKey) {
            return (Builder) super.apiKey(apiKey);
        }

        public Builder modelName(String modelName) {
            return (Builder) super.modelName(modelName);
        }

        public Builder topP(Double topP) {
            return (Builder) super.topP(topP);
        }

        public Builder topK(Double topK) {
            return (Builder) super.topK(topK);
        }

        public Builder enableSearch(Boolean enableSearch) {
            return (Builder) super.enableSearch(enableSearch);
        }

        public Builder seed(Integer seed) {
            return (Builder) super.seed(seed);
        }

        public QwenStreamingLanguageModel build() {
            ensureOptions();
            return new QwenStreamingLanguageModel(apiKey, modelName, topP, topK, enableSearch, seed);
        }
    }
}
