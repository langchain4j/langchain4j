package dev.langchain4j;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties(prefix = "langchain4j")
public class LangChain4jProperties {

    @NestedConfigurationProperty
    private ChatModel chatModel;
    @NestedConfigurationProperty
    private LanguageModel languageModel;
    @NestedConfigurationProperty
    private EmbeddingModel embeddingModel;
    @NestedConfigurationProperty
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
}
