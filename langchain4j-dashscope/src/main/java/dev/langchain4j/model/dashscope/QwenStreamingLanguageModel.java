package dev.langchain4j.model.dashscope;

import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.language.StreamingLanguageModel;

public class QwenStreamingLanguageModel extends QwenStreamingChatModel implements StreamingLanguageModel {
    protected QwenStreamingLanguageModel(String apiKey,
                                         String modelName,
                                         Double topP,
                                         Integer topK,
                                         Boolean enableSearch,
                                         Integer seed) {
        super(apiKey, modelName, topP, topK, enableSearch, seed);
    }

    @Override
    public void process(String text, StreamingResponseHandler handler) {
        sendMessage(text, null, handler);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends QwenStreamingChatModel.Builder {
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
        public QwenStreamingLanguageModel build() {
            ensureOptions();
            return new QwenStreamingLanguageModel(apiKey, modelName, topP, topK, enableSearch, seed);
        }
    }
}
