package dev.langchain4j.model.googleai;

import java.util.List;

public class GoogleAiGeminiLiveModel {

    private final String apiKey;
    private final String modelName;
    private final Integer thinkingBudget;
    private final List<String> responseModalities;
    private final String systemInstruction;

    private GoogleAiGeminiLiveModel(Builder builder) {
        this.apiKey = builder.apiKey;
        this.modelName =
                builder.modelName != null ? builder.modelName : "gemini-2.5-flash-native-audio-preview-12-2025";
        this.thinkingBudget = builder.thinkingBudget;
        this.responseModalities = builder.responseModalities;
        this.systemInstruction = builder.systemInstruction;
    }

    public LiveSession connect() {
        return new LiveSessionImpl(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String apiKey;
        private String modelName;
        private Integer thinkingBudget;
        private List<String> responseModalities;
        private String systemInstruction;

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public Builder thinkingBudget(Integer thinkingBudget) {
            this.thinkingBudget = thinkingBudget;
            return this;
        }

        public Builder responseModalities(List<String> responseModalities) {
            this.responseModalities = responseModalities;
            return this;
        }

        public Builder systemInstruction(String systemInstruction) {
            this.systemInstruction = systemInstruction;
            return this;
        }

        public GoogleAiGeminiLiveModel build() {
            if (apiKey == null || apiKey.trim().isEmpty()) {
                throw new IllegalArgumentException("apiKey must not be null or empty");
            }
            return new GoogleAiGeminiLiveModel(this);
        }
    }

    // Package-private getters for LiveSessionImpl
    String getApiKey() {
        return apiKey;
    }

    String getModelName() {
        return modelName;
    }

    Integer getThinkingBudget() {
        return thinkingBudget;
    }

    List<String> getResponseModalities() {
        return responseModalities;
    }

    String getSystemInstruction() {
        return systemInstruction;
    }
}
