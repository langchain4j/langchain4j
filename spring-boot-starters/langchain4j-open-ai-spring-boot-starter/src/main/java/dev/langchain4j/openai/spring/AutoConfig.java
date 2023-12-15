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

        ChatModelProperties properties = openAiProperties.getChatModel();
        if (properties == null) {
            throw illegalArgument(MISSING_CONFIG_ERROR, "OpenAiChatModel", PREFIX, "chat-model");
        }

        if (isNullOrBlank(properties.getApiKey())) {
            throw illegalArgument(MISSING_API_KEY_ERROR, PREFIX, "chat-model");
        }

        return OpenAiChatModel.builder()
                .baseUrl(properties.getBaseUrl())
                .apiKey(properties.getApiKey())
                .modelName(properties.getModelName())
                .temperature(properties.getTemperature())
                .topP(properties.getTopP())
                .stop(properties.getStop())
                .maxTokens(properties.getMaxTokens())
                .presencePenalty(properties.getPresencePenalty())
                .frequencyPenalty(properties.getFrequencyPenalty())
                .timeout(properties.getTimeout())
                .maxRetries(properties.getMaxRetries())
                .logRequests(properties.getLogRequests())
                .logResponses(properties.getLogResponses())
                .build();
    }

    @Bean
    @Lazy
    @ConditionalOnMissingBean
    OpenAiLanguageModel openAiLanguageModel(OpenAiProperties openAiProperties) {

        LanguageModelProperties properties = openAiProperties.getLanguageModel();
        if (properties == null) {
            throw illegalArgument(MISSING_CONFIG_ERROR, "OpenAiLanguageModel", PREFIX, "language-model");
        }

        if (isNullOrBlank(properties.getApiKey())) {
            throw illegalArgument(MISSING_API_KEY_ERROR, PREFIX, "language-model");
        }

        return OpenAiLanguageModel.builder()
                .baseUrl(properties.getBaseUrl())
                .apiKey(properties.getApiKey())
                .modelName(properties.getModelName())
                .temperature(properties.getTemperature())
                .timeout(properties.getTimeout())
                .maxRetries(properties.getMaxRetries())
                .logRequests(properties.getLogRequests())
                .logResponses(properties.getLogResponses())
                .build();
    }

    @Bean
    @Lazy
    @ConditionalOnMissingBean
    OpenAiEmbeddingModel openAiEmbeddingModel(OpenAiProperties openAiProperties) {

        EmbeddingModelProperties properties = openAiProperties.getEmbeddingModel();
        if (properties == null) {
            throw illegalArgument(MISSING_CONFIG_ERROR, "OpenAiEmbeddingModel", PREFIX, "embedding-model");
        }

        if (isNullOrBlank(properties.getApiKey())) {
            throw illegalArgument(MISSING_API_KEY_ERROR, PREFIX, "embedding-model");
        }

        return OpenAiEmbeddingModel.builder()
                .baseUrl(properties.getBaseUrl())
                .apiKey(properties.getApiKey())
                .modelName(properties.getModelName())
                .timeout(properties.getTimeout())
                .maxRetries(properties.getMaxRetries())
                .logRequests(properties.getLogRequests())
                .logResponses(properties.getLogResponses())
                .build();
    }

    @Bean
    @Lazy
    @ConditionalOnMissingBean
    OpenAiModerationModel openAiModerationModel(OpenAiProperties openAiProperties) {

        ModerationModelProperties properties = openAiProperties.getModerationModel();
        if (properties == null) {
            throw illegalArgument(MISSING_CONFIG_ERROR, "OpenAiModerationModel", PREFIX, "moderation-model");
        }

        if (isNullOrBlank(properties.getApiKey())) {
            throw illegalArgument(MISSING_API_KEY_ERROR, PREFIX, "moderation-model");
        }

        return OpenAiModerationModel.builder()
                .baseUrl(properties.getBaseUrl())
                .apiKey(properties.getApiKey())
                .modelName(properties.getModelName())
                .timeout(properties.getTimeout())
                .maxRetries(properties.getMaxRetries())
                .logRequests(properties.getLogRequests())
                .logResponses(properties.getLogResponses())
                .build();
    }
}