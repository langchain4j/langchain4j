package dev.langchain4j;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "langchain4j")
public class LangChain4jProperties {

    private ChatModel chatModel;
    private LanguageModel languageModel;
    private EmbeddingModel embeddingModel;
    private ModerationModel moderationModel;

    public ChatModel getChatModel() {
        return chatModel;
    }

    public void setChatModel(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public LanguageModel getLanguageModel() {
        return languageModel;
    }

    public void setLanguageModel(LanguageModel languageModel) {
        this.languageModel = languageModel;
    }

    public EmbeddingModel getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public ModerationModel getModerationModel() {
        return moderationModel;
    }

    public void setModerationModel(ModerationModel moderationModel) {
        this.moderationModel = moderationModel;
    }

    static class ChatModel {

        private ModelProvider provider;
        private OpenAi openAi;

        public ModelProvider getProvider() {
            return provider;
        }

        public void setProvider(ModelProvider provider) {
            this.provider = provider;
        }

        public OpenAi getOpenAi() {
            return openAi;
        }

        public void setOpenAi(OpenAi openAi) {
            this.openAi = openAi;
        }
    }

    static class LanguageModel {

        private ModelProvider provider;
        private OpenAi openAi;

        public ModelProvider getProvider() {
            return provider;
        }

        public void setProvider(ModelProvider provider) {
            this.provider = provider;
        }

        public OpenAi getOpenAi() {
            return openAi;
        }

        public void setOpenAi(OpenAi openAi) {
            this.openAi = openAi;
        }
    }

    static class EmbeddingModel {

        private ModelProvider provider;
        private OpenAi openAi;
        private HuggingFace huggingFace;

        public ModelProvider getProvider() {
            return provider;
        }

        public void setProvider(ModelProvider provider) {
            this.provider = provider;
        }

        public OpenAi getOpenAi() {
            return openAi;
        }

        public void setOpenAi(OpenAi openAi) {
            this.openAi = openAi;
        }

        public HuggingFace getHuggingFace() {
            return huggingFace;
        }

        public void setHuggingFace(HuggingFace huggingFace) {
            this.huggingFace = huggingFace;
        }
    }

    static class ModerationModel {

        private ModelProvider provider;
        private OpenAi openAi;

        public ModelProvider getProvider() {
            return provider;
        }

        public void setProvider(ModelProvider provider) {
            this.provider = provider;
        }

        public OpenAi getOpenAi() {
            return openAi;
        }

        public void setOpenAi(OpenAi openAi) {
            this.openAi = openAi;
        }
    }

    enum ModelProvider {
        OPEN_AI, HUGGING_FACE
    }

    static class OpenAi {

        private String apiKey;
        private String modelName;
        private Double temperature;
        private Duration timeout;
        private Boolean logRequests;
        private Boolean logResponses;

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModelName() {
            return modelName;
        }

        public void setModelName(String modelName) {
            this.modelName = modelName;
        }

        public Double getTemperature() {
            return temperature;
        }

        public void setTemperature(Double temperature) {
            this.temperature = temperature;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }

        public Boolean getLogRequests() {
            return logRequests;
        }

        public void setLogRequests(Boolean logRequests) {
            this.logRequests = logRequests;
        }

        public Boolean getLogResponses() {
            return logResponses;
        }

        public void setLogResponses(Boolean logResponses) {
            this.logResponses = logResponses;
        }
    }

    static class HuggingFace {

        private String accessToken;
        private String modelId;
        private Boolean waitForModel;
        private Duration timeout;

        public String getAccessToken() {
            return accessToken;
        }

        public void setAccessToken(String accessToken) {
            this.accessToken = accessToken;
        }

        public String getModelId() {
            return modelId;
        }

        public void setModelId(String modelId) {
            this.modelId = modelId;
        }

        public Boolean getWaitForModel() {
            return waitForModel;
        }

        public void setWaitForModel(Boolean waitForModel) {
            this.waitForModel = waitForModel;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }
    }
}
