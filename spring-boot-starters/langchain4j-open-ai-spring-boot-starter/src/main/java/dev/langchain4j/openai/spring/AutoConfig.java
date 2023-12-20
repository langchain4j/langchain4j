package dev.langchain4j.openai.spring;

import dev.langchain4j.model.openai.*;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import static dev.langchain4j.openai.spring.Properties.PREFIX;

@AutoConfiguration
@EnableConfigurationProperties(Properties.class)
public class AutoConfig {

    @Bean
    @ConditionalOnProperty(PREFIX + ".chat-model.api-key")
    OpenAiChatModel openAiChatModel(Properties properties) {
        ChatModelProperties chatModelProperties = properties.getChatModel();
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
    @ConditionalOnProperty(PREFIX + ".streaming-chat-model.api-key")
    OpenAiStreamingChatModel openAiStreamingChatModel(Properties properties) {
        ChatModelProperties chatModelProperties = properties.getStreamingChatModel();
        return OpenAiStreamingChatModel.builder()
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
                .logRequests(chatModelProperties.getLogRequests())
                .logResponses(chatModelProperties.getLogResponses())
                .build();
    }

    @Bean
    @ConditionalOnProperty(PREFIX + ".language-model.api-key")
    OpenAiLanguageModel openAiLanguageModel(Properties properties) {
        LanguageModelProperties languageModelProperties = properties.getLanguageModel();
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
    @ConditionalOnProperty(PREFIX + ".streaming-language-model.api-key")
    OpenAiStreamingLanguageModel openAiStreamingLanguageModel(Properties properties) {
        LanguageModelProperties languageModelProperties = properties.getStreamingLanguageModel();
        return OpenAiStreamingLanguageModel.builder()
                .baseUrl(languageModelProperties.getBaseUrl())
                .apiKey(languageModelProperties.getApiKey())
                .organizationId(languageModelProperties.getOrganizationId())
                .modelName(languageModelProperties.getModelName())
                .temperature(languageModelProperties.getTemperature())
                .timeout(languageModelProperties.getTimeout())
                .logRequests(languageModelProperties.getLogRequests())
                .logResponses(languageModelProperties.getLogResponses())
                .build();
    }

    @Bean
    @ConditionalOnProperty(PREFIX + ".embedding-model.api-key")
    OpenAiEmbeddingModel openAiEmbeddingModel(Properties properties) {
        EmbeddingModelProperties embeddingModelProperties = properties.getEmbeddingModel();
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
    @ConditionalOnProperty(PREFIX + ".moderation-model.api-key")
    OpenAiModerationModel openAiModerationModel(Properties properties) {
        ModerationModelProperties moderationModelProperties = properties.getModerationModel();
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

    @Bean
    @ConditionalOnProperty(PREFIX + ".image-model.api-key")
    OpenAiImageModel openAiImageModel(Properties properties) {
        ImageModelProperties imageModelProperties = properties.getImageModel();
        return OpenAiImageModel.builder()
                .baseUrl(imageModelProperties.getBaseUrl())
                .apiKey(imageModelProperties.getApiKey())
                .organizationId(imageModelProperties.getOrganizationId())
                .modelName(imageModelProperties.getModelName())
                .size(imageModelProperties.getSize())
                .quality(imageModelProperties.getQuality())
                .style(imageModelProperties.getStyle())
                .user(imageModelProperties.getUser())
                .responseFormat(imageModelProperties.getResponseFormat())
                .timeout(imageModelProperties.getTimeout())
                .maxRetries(imageModelProperties.getMaxRetries())
                .logRequests(imageModelProperties.getLogRequests())
                .logResponses(imageModelProperties.getLogResponses())
                .withPersisting(imageModelProperties.getWithPersisting())
                .persistTo(imageModelProperties.getPersistTo())
                .build();
    }
}