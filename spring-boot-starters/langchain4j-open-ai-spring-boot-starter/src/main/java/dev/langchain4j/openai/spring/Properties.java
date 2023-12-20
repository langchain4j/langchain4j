package dev.langchain4j.openai.spring;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@Getter
@Setter
@ConfigurationProperties(prefix = Properties.PREFIX)
public class Properties {

    static final String PREFIX = "langchain4j.open-ai";

    @NestedConfigurationProperty
    ChatModelProperties chatModel;

    @NestedConfigurationProperty
    ChatModelProperties streamingChatModel;

    @NestedConfigurationProperty
    LanguageModelProperties languageModel;

    @NestedConfigurationProperty
    LanguageModelProperties streamingLanguageModel;

    @NestedConfigurationProperty
    EmbeddingModelProperties embeddingModel;

    @NestedConfigurationProperty
    ModerationModelProperties moderationModel;

    @NestedConfigurationProperty
    ImageModelProperties imageModel;
}
