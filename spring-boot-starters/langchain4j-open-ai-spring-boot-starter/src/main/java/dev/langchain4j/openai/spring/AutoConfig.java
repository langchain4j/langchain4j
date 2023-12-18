package dev.langchain4j.openai.spring;

import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiLanguageModel;
import dev.langchain4j.model.openai.OpenAiModerationModel;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;

import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.openai.spring.OpenAiProperties.PREFIX;

@AutoConfiguration
@EnableConfigurationProperties(OpenAiProperties.class)
public class AutoConfig {

    private static final String MISSING_CONFIG_ERROR = "You are requesting an instance of an %s, " +
            "but no '%s.%s' configuration properties are provided.";
    private static final String MISSING_API_KEY_ERROR = "Please provide an OpenAI API key through " +
            "the '%s.%s.api-key' property.";

    @Bean
    @Lazy
    @ConditionalOnMissingBean
    OpenAiChatModel openAiChatModel(OpenAiProperties openAiProperties) {

        ChatModelProperties properties = openAiProperties.chatModel;
        if (properties == null) {
            throw illegalArgument(MISSING_CONFIG_ERROR, "OpenAiChatModel", PREFIX, "chat-model");
        }

        if (isNullOrBlank(properties.apiKey)) {
            throw illegalArgument(MISSING_API_KEY_ERROR, PREFIX, "chat-model");
        }

        return OpenAiChatModel.builder()
                .baseUrl(properties.baseUrl)
                .apiKey(properties.apiKey)
                .organizationId(properties.organizationId)
                .modelName(properties.modelName)
                .temperature(properties.temperature)
                .topP(properties.topP)
                .stop(properties.stop)
                .maxTokens(properties.maxTokens)
                .presencePenalty(properties.presencePenalty)
                .frequencyPenalty(properties.frequencyPenalty)
                .timeout(properties.timeout)
                .maxRetries(properties.maxRetries)
                .logRequests(properties.logRequests)
                .logResponses(properties.logResponses)
                .build();
    }

    @Bean
    @Lazy
    @ConditionalOnMissingBean
    OpenAiLanguageModel openAiLanguageModel(OpenAiProperties openAiProperties) {

        LanguageModelProperties properties = openAiProperties.languageModel;
        if (properties == null) {
            throw illegalArgument(MISSING_CONFIG_ERROR, "OpenAiLanguageModel", PREFIX, "language-model");
        }

        if (isNullOrBlank(properties.apiKey)) {
            throw illegalArgument(MISSING_API_KEY_ERROR, PREFIX, "language-model");
        }

        return OpenAiLanguageModel.builder()
                .baseUrl(properties.baseUrl)
                .apiKey(properties.apiKey)
                .organizationId(properties.organizationId)
                .modelName(properties.modelName)
                .temperature(properties.temperature)
                .timeout(properties.timeout)
                .maxRetries(properties.maxRetries)
                .logRequests(properties.logRequests)
                .logResponses(properties.logResponses)
                .build();
    }

    @Bean
    @Lazy
    @ConditionalOnMissingBean
    OpenAiEmbeddingModel openAiEmbeddingModel(OpenAiProperties openAiProperties) {

        EmbeddingModelProperties properties = openAiProperties.embeddingModel;
        if (properties == null) {
            throw illegalArgument(MISSING_CONFIG_ERROR, "OpenAiEmbeddingModel", PREFIX, "embedding-model");
        }

        if (isNullOrBlank(properties.apiKey)) {
            throw illegalArgument(MISSING_API_KEY_ERROR, PREFIX, "embedding-model");
        }

        return OpenAiEmbeddingModel.builder()
                .baseUrl(properties.baseUrl)
                .apiKey(properties.apiKey)
                .organizationId(properties.organizationId)
                .modelName(properties.modelName)
                .timeout(properties.timeout)
                .maxRetries(properties.maxRetries)
                .logRequests(properties.logRequests)
                .logResponses(properties.logResponses)
                .build();
    }

    @Bean
    @Lazy
    @ConditionalOnMissingBean
    OpenAiModerationModel openAiModerationModel(OpenAiProperties openAiProperties) {

        ModerationModelProperties properties = openAiProperties.moderationModel;
        if (properties == null) {
            throw illegalArgument(MISSING_CONFIG_ERROR, "OpenAiModerationModel", PREFIX, "moderation-model");
        }

        if (isNullOrBlank(properties.apiKey)) {
            throw illegalArgument(MISSING_API_KEY_ERROR, PREFIX, "moderation-model");
        }

        return OpenAiModerationModel.builder()
                .baseUrl(properties.baseUrl)
                .apiKey(properties.apiKey)
                .organizationId(properties.organizationId)
                .modelName(properties.modelName)
                .timeout(properties.timeout)
                .maxRetries(properties.maxRetries)
                .logRequests(properties.logRequests)
                .logResponses(properties.logResponses)
                .build();
    }
}