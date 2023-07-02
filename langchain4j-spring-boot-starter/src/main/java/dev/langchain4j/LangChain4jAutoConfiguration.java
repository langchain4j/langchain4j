package dev.langchain4j;

import dev.langchain4j.LangChain4jProperties.HuggingFace;
import dev.langchain4j.LangChain4jProperties.OpenAi;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.huggingface.HuggingFaceChatModel;
import dev.langchain4j.model.huggingface.HuggingFaceEmbeddingModel;
import dev.langchain4j.model.huggingface.HuggingFaceLanguageModel;
import dev.langchain4j.model.language.LanguageModel;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiLanguageModel;
import dev.langchain4j.model.openai.OpenAiModerationModel;
import dev.langchain4j.service.IllegalConfigurationException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import static dev.langchain4j.LangChain4jProperties.ModelProvider.OPEN_AI;

@Configuration
@EnableConfigurationProperties(LangChain4jProperties.class)
public class LangChain4jAutoConfiguration {

    @Bean
    @Lazy
    ChatLanguageModel chatLanguageModel(LangChain4jProperties properties) {
        switch (properties.getChatModel().getProvider()) {
            case OPEN_AI:
                OpenAi openAi = properties.getChatModel().getOpenAi();
                return OpenAiChatModel.builder()
                        .apiKey(openAi.getApiKey())
                        .modelName(openAi.getModelName())
                        .temperature(openAi.getTemperature())
                        .timeout(openAi.getTimeout())
                        .logRequests(openAi.getLogRequests())
                        .logResponses(openAi.getLogResponses())
                        .build();
            case HUGGING_FACE:
                HuggingFace huggingFace = properties.getChatModel().getHuggingFace();
                return HuggingFaceChatModel.builder()
                        .accessToken(huggingFace.getAccessToken())
                        .modelId(huggingFace.getModelId())
                        .timeout(huggingFace.getTimeout())
                        .temperature(huggingFace.getTemperature())
                        .maxNewTokens(huggingFace.getMaxNewTokens())
                        .returnFullText(huggingFace.getReturnFullText())
                        .waitForModel(huggingFace.getWaitForModel())
                        .build();
            default:
                throw new IllegalConfigurationException("Unsupported chat model provider: " + properties.getChatModel().getProvider());
        }
    }

    @Bean
    @Lazy
    LanguageModel languageModel(LangChain4jProperties properties) {
        switch (properties.getLanguageModel().getProvider()) {
            case OPEN_AI:
                OpenAi openAi = properties.getLanguageModel().getOpenAi();
                return OpenAiLanguageModel.builder()
                        .apiKey(openAi.getApiKey())
                        .modelName(openAi.getModelName())
                        .temperature(openAi.getTemperature())
                        .timeout(openAi.getTimeout())
                        .logRequests(openAi.getLogRequests())
                        .logResponses(openAi.getLogResponses())
                        .build();
            case HUGGING_FACE:
                HuggingFace huggingFace = properties.getLanguageModel().getHuggingFace();
                return HuggingFaceLanguageModel.builder()
                        .accessToken(huggingFace.getAccessToken())
                        .modelId(huggingFace.getModelId())
                        .timeout(huggingFace.getTimeout())
                        .temperature(huggingFace.getTemperature())
                        .maxNewTokens(huggingFace.getMaxNewTokens())
                        .returnFullText(huggingFace.getReturnFullText())
                        .waitForModel(huggingFace.getWaitForModel())
                        .build();
            default:
                throw new IllegalConfigurationException("Unsupported language model provider: " + properties.getLanguageModel().getProvider());
        }
    }

    @Bean
    @Lazy
    EmbeddingModel embeddingModel(LangChain4jProperties properties) {
        switch (properties.getEmbeddingModel().getProvider()) {
            case OPEN_AI:
                OpenAi openAi = properties.getEmbeddingModel().getOpenAi();
                return OpenAiEmbeddingModel.builder()
                        .apiKey(openAi.getApiKey())
                        .modelName(openAi.getModelName())
                        .timeout(openAi.getTimeout())
                        .logRequests(openAi.getLogRequests())
                        .logResponses(openAi.getLogResponses())
                        .build();
            case HUGGING_FACE:
                HuggingFace huggingFace = properties.getEmbeddingModel().getHuggingFace();
                return HuggingFaceEmbeddingModel.builder()
                        .accessToken(huggingFace.getAccessToken())
                        .modelId(huggingFace.getModelId())
                        .waitForModel(huggingFace.getWaitForModel())
                        .timeout(huggingFace.getTimeout())
                        .build();
            default:
                throw new IllegalConfigurationException("Unsupported embedding model provider: " + properties.getEmbeddingModel().getProvider());
        }
    }

    @Bean
    @Lazy
    ModerationModel moderationModel(LangChain4jProperties properties) {
        if (properties.getModerationModel().getProvider() != OPEN_AI) {
            throw new IllegalConfigurationException("Unsupported moderation model provider: " + properties.getModerationModel().getProvider());
        }

        OpenAi openAi = properties.getModerationModel().getOpenAi();
        return OpenAiModerationModel.builder()
                .apiKey(openAi.getApiKey())
                .modelName(openAi.getModelName())
                .timeout(openAi.getTimeout())
                .logRequests(openAi.getLogRequests())
                .logResponses(openAi.getLogResponses())
                .build();
    }
}