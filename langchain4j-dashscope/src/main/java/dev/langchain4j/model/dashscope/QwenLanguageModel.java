package dev.langchain4j.model.dashscope;

import dev.langchain4j.model.language.LanguageModel;

public class QwenLanguageModel extends QwenChatModel implements LanguageModel {
    protected QwenLanguageModel(String apiKey,
                                String modelName,
                                Double topP,
                                Integer topK,
                                Boolean enableSearch,
                                Integer seed) {
        super(apiKey, modelName, topP, topK, enableSearch, seed);
    }
    @Override
    public String process(String text) {
        return sendMessage(text, null);
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

        public QwenLanguageModel build() {
            ensureOptions();
            return new QwenLanguageModel(apiKey, modelName, topP, topK, enableSearch, seed);
        }
    }
}
