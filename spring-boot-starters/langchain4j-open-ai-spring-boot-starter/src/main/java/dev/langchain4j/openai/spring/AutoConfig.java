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
import static dev.langchain4j.openai.spring.Properties.PREFIX;

@AutoConfiguration
@EnableConfigurationProperties(Properties.class)
public class AutoConfig {

    private static final String MISSING_CONFIG_ERROR = "You are requesting an instance of an %s, " +
            "but no '%s.%s' configuration properties are provided.";
    private static final String MISSING_API_KEY_ERROR = "Please provide an OpenAI API key through " +
            "the '%s.%s.api-key' property.";

    @Bean
    @Lazy
    @ConditionalOnMissingBean
    OpenAiChatModel openAiChatModel(Properties properties) {

        ChatModelProperties chatModelProperties = properties.getChatModel();

        if (chatModelProperties == null) {
            throw illegalArgument(MISSING_CONFIG_ERROR, "OpenAiChatModel", PREFIX, "chat-model");
        }
        if (isNullOrBlank(chatModelProperties.apiKey)) {
            throw illegalArgument(MISSING_API_KEY_ERROR, PREFIX, "chat-model");
        }

        return OpenAiChatModel.builder()
                .baseUrl(chatModelProperties.getBaseUrl())
                .apiKey(chatModelProperties.getApiKey())
                .organizationId(chatModelProperties.getOrganizationId())
                .modelName(chatModelProperties.getModelName())
                .temperature(chatModelProperties.getTemperature())
                .topP(chatModelProperties.getTopP())
                .stop(chatModelProperties.getStop())
                .maxTokens(chatModelProperties.getMaxTokens())
                .presencePenalty(chatModelProperties.getPresencePenalty())
                .frequencyPenalty(chatModelProperties.getFrequencyPenalty())
                .timeout(chatModelProperties.getTimeout())
                .maxRetries(chatModelProperties.getMaxRetries())
                .logRequests(chatModelProperties.getLogRequests())
                .logResponses(chatModelProperties.getLogResponses())
                .build();
    }

    @Bean
    @Lazy
    @ConditionalOnMissingBean
    OpenAiLanguageModel openAiLanguageModel(Properties properties) {

        LanguageModelProperties languageModelProperties = properties.getLanguageModel();

        if (languageModelProperties == null) {
            throw illegalArgument(MISSING_CONFIG_ERROR, "OpenAiLanguageModel", PREFIX, "language-model");
        }
        if (isNullOrBlank(languageModelProperties.apiKey)) {
            throw illegalArgument(MISSING_API_KEY_ERROR, PREFIX, "language-model");
        }

        return OpenAiLanguageModel.builder()
                .baseUrl(languageModelProperties.getBaseUrl())
                .apiKey(languageModelProperties.getApiKey())
                .organizationId(languageModelProperties.getOrganizationId())
                .modelName(languageModelProperties.getModelName())
                .temperature(languageModelProperties.getTemperature())
                .timeout(languageModelProperties.getTimeout())
                .maxRetries(languageModelProperties.getMaxRetries())
                .logRequests(languageModelProperties.getLogRequests())
                .logResponses(languageModelProperties.getLogResponses())
                .build();
    }

    @Bean
    @Lazy
    @ConditionalOnMissingBean
    OpenAiEmbeddingModel openAiEmbeddingModel(Properties properties) {

        EmbeddingModelProperties embeddingModelProperties = properties.getEmbeddingModel();

        if (embeddingModelProperties == null) {
            throw illegalArgument(MISSING_CONFIG_ERROR, "OpenAiEmbeddingModel", PREFIX, "embedding-model");
        }
        if (isNullOrBlank(embeddingModelProperties.apiKey)) {
            throw illegalArgument(MISSING_API_KEY_ERROR, PREFIX, "embedding-model");
        }

        return OpenAiEmbeddingModel.builder()
                .baseUrl(embeddingModelProperties.getBaseUrl())
                .apiKey(embeddingModelProperties.getApiKey())
                .organizationId(embeddingModelProperties.getOrganizationId())
                .modelName(embeddingModelProperties.getModelName())
                .timeout(embeddingModelProperties.getTimeout())
                .maxRetries(embeddingModelProperties.getMaxRetries())
                .logRequests(embeddingModelProperties.getLogRequests())
                .logResponses(embeddingModelProperties.getLogResponses())
                .build();
    }

    @Bean
    @Lazy
    @ConditionalOnMissingBean
    OpenAiModerationModel openAiModerationModel(Properties properties) {

        ModerationModelProperties moderationModelProperties = properties.getModerationModel();

        if (moderationModelProperties == null) {
            throw illegalArgument(MISSING_CONFIG_ERROR, "OpenAiModerationModel", PREFIX, "moderation-model");
        }
        if (isNullOrBlank(moderationModelProperties.apiKey)) {
            throw illegalArgument(MISSING_API_KEY_ERROR, PREFIX, "moderation-model");
        }

        return OpenAiModerationModel.builder()
                .baseUrl(moderationModelProperties.getBaseUrl())
                .apiKey(moderationModelProperties.getApiKey())
                .organizationId(moderationModelProperties.getOrganizationId())
                .modelName(moderationModelProperties.getModelName())
                .timeout(moderationModelProperties.getTimeout())
                .maxRetries(moderationModelProperties.getMaxRetries())
                .logRequests(moderationModelProperties.getLogRequests())
                .logResponses(moderationModelProperties.getLogResponses())
                .build();
    }
}