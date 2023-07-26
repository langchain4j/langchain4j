package dev.langchain4j;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.huggingface.HuggingFaceChatModel;
import dev.langchain4j.model.huggingface.HuggingFaceEmbeddingModel;
import dev.langchain4j.model.huggingface.HuggingFaceLanguageModel;
import dev.langchain4j.model.language.LanguageModel;
import dev.langchain4j.model.localai.LocalAiChatModel;
import dev.langchain4j.model.localai.LocalAiEmbeddingModel;
import dev.langchain4j.model.localai.LocalAiLanguageModel;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiLanguageModel;
import dev.langchain4j.model.openai.OpenAiModerationModel;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;

import static dev.langchain4j.ModelProvider.OPEN_AI;
import static dev.langchain4j.exception.IllegalConfigurationException.illegalConfiguration;
import static dev.langchain4j.internal.Utils.isNullOrBlank;

@AutoConfiguration
@EnableConfigurationProperties(LangChain4jProperties.class)
public class LangChain4jAutoConfiguration {

    @Bean
    @Lazy
    @ConditionalOnMissingBean
    ChatLanguageModel chatLanguageModel(LangChain4jProperties properties) {
        if (properties.getChatModel() == null) {
            throw illegalConfiguration("\n\nPlease define 'langchain4j.chat-model' properties, for example:\n"
                    + "langchain4j.chat-model.provider = openai\n"
                    + "langchain4j.chat-model.openai.api-key = sk-...\n");
        }

        switch (properties.getChatModel().getProvider()) {

            case OPEN_AI:
                OpenAi openAi = properties.getChatModel().getOpenAi();
                if (openAi == null || isNullOrBlank(openAi.getApiKey())) {
                    throw illegalConfiguration("\n\nPlease define 'langchain4j.chat-model.openai.api-key' property");
                }
                return OpenAiChatModel.builder()
                        .apiKey(openAi.getApiKey())
                        .modelName(openAi.getModelName())
                        .temperature(openAi.getTemperature())
                        .topP(openAi.getTopP())
                        .maxTokens(openAi.getMaxTokens())
                        .presencePenalty(openAi.getPresencePenalty())
                        .frequencyPenalty(openAi.getFrequencyPenalty())
                        .timeout(openAi.getTimeout())
                        .maxRetries(openAi.getMaxRetries())
                        .logRequests(openAi.getLogRequests())
                        .logResponses(openAi.getLogResponses())
                        .build();

            case HUGGING_FACE:
                HuggingFace huggingFace = properties.getChatModel().getHuggingFace();
                if (huggingFace == null || isNullOrBlank(huggingFace.getAccessToken())) {
                    throw illegalConfiguration("\n\nPlease define 'langchain4j.chat-model.huggingface.access-token' property");
                }
                return HuggingFaceChatModel.builder()
                        .accessToken(huggingFace.getAccessToken())
                        .modelId(huggingFace.getModelId())
                        .timeout(huggingFace.getTimeout())
                        .temperature(huggingFace.getTemperature())
                        .maxNewTokens(huggingFace.getMaxNewTokens())
                        .returnFullText(huggingFace.getReturnFullText())
                        .waitForModel(huggingFace.getWaitForModel())
                        .build();

            case LOCAL_AI:
                LocalAi localAi = properties.getChatModel().getLocalAi();
                if (localAi == null || isNullOrBlank(localAi.getBaseUrl())) {
                    throw illegalConfiguration("\n\nPlease define 'langchain4j.chat-model.localai.base-url' property");
                }
                if (isNullOrBlank(localAi.getModelName())) {
                    throw illegalConfiguration("\n\nPlease define 'langchain4j.chat-model.localai.model-name' property");
                }
                return LocalAiChatModel.builder()
                        .baseUrl(localAi.getBaseUrl())
                        .modelName(localAi.getModelName())
                        .temperature(localAi.getTemperature())
                        .topP(localAi.getTopP())
                        .maxTokens(localAi.getMaxTokens())
                        .timeout(localAi.getTimeout())
                        .maxRetries(localAi.getMaxRetries())
                        .logRequests(localAi.getLogRequests())
                        .logResponses(localAi.getLogResponses())
                        .build();

            default:
                throw illegalConfiguration("Unsupported chat model provider: %s", properties.getChatModel().getProvider());
        }
    }

    @Bean
    @Lazy
    @ConditionalOnMissingBean
    LanguageModel languageModel(LangChain4jProperties properties) {
        if (properties.getLanguageModel() == null) {
            throw illegalConfiguration("\n\nPlease define 'langchain4j.language-model' properties, for example:\n"
                    + "langchain4j.language-model.provider = openai\n"
                    + "langchain4j.language-model.openai.api-key = sk-...\n");
        }

        switch (properties.getLanguageModel().getProvider()) {

            case OPEN_AI:
                OpenAi openAi = properties.getLanguageModel().getOpenAi();
                if (openAi == null || isNullOrBlank(openAi.getApiKey())) {
                    throw illegalConfiguration("\n\nPlease define 'langchain4j.language-model.openai.api-key' property");
                }
                return OpenAiLanguageModel.builder()
                        .apiKey(openAi.getApiKey())
                        .modelName(openAi.getModelName())
                        .temperature(openAi.getTemperature())
                        .timeout(openAi.getTimeout())
                        .maxRetries(openAi.getMaxRetries())
                        .logRequests(openAi.getLogRequests())
                        .logResponses(openAi.getLogResponses())
                        .build();

            case HUGGING_FACE:
                HuggingFace huggingFace = properties.getLanguageModel().getHuggingFace();
                if (huggingFace == null || isNullOrBlank(huggingFace.getAccessToken())) {
                    throw illegalConfiguration("\n\nPlease define 'langchain4j.language-model.huggingface.access-token' property");
                }
                return HuggingFaceLanguageModel.builder()
                        .accessToken(huggingFace.getAccessToken())
                        .modelId(huggingFace.getModelId())
                        .timeout(huggingFace.getTimeout())
                        .temperature(huggingFace.getTemperature())
                        .maxNewTokens(huggingFace.getMaxNewTokens())
                        .returnFullText(huggingFace.getReturnFullText())
                        .waitForModel(huggingFace.getWaitForModel())
                        .build();

            case LOCAL_AI:
                LocalAi localAi = properties.getLanguageModel().getLocalAi();
                if (localAi == null || isNullOrBlank(localAi.getBaseUrl())) {
                    throw illegalConfiguration("\n\nPlease define 'langchain4j.language-model.localai.base-url' property");
                }
                if (isNullOrBlank(localAi.getModelName())) {
                    throw illegalConfiguration("\n\nPlease define 'langchain4j.language-model.localai.model-name' property");
                }
                return LocalAiLanguageModel.builder()
                        .baseUrl(localAi.getBaseUrl())
                        .modelName(localAi.getModelName())
                        .temperature(localAi.getTemperature())
                        .topP(localAi.getTopP())
                        .maxTokens(localAi.getMaxTokens())
                        .timeout(localAi.getTimeout())
                        .maxRetries(localAi.getMaxRetries())
                        .logRequests(localAi.getLogRequests())
                        .logResponses(localAi.getLogResponses())
                        .build();

            default:
                throw illegalConfiguration("Unsupported language model provider: %s", properties.getLanguageModel().getProvider());
        }
    }

    @Bean
    @Lazy
    @ConditionalOnMissingBean
    EmbeddingModel embeddingModel(LangChain4jProperties properties) {
        if (properties.getEmbeddingModel() == null || properties.getEmbeddingModel().getProvider() == null) {
            throw illegalConfiguration("\n\nPlease define 'langchain4j.embedding-model' properties, for example:\n"
                    + "langchain4j.embedding-model.provider = openai\n"
                    + "langchain4j.embedding-model.openai.api-key = sk-...\n");
        }

        switch (properties.getEmbeddingModel().getProvider()) {

            case OPEN_AI:
                OpenAi openAi = properties.getEmbeddingModel().getOpenAi();
                if (openAi == null || isNullOrBlank(openAi.getApiKey())) {
                    throw illegalConfiguration("\n\nPlease define 'langchain4j.embedding-model.openai.api-key' property"); // TODO exception type
                }
                return OpenAiEmbeddingModel.builder()
                        .apiKey(openAi.getApiKey())
                        .modelName(openAi.getModelName())
                        .timeout(openAi.getTimeout())
                        .maxRetries(openAi.getMaxRetries())
                        .logRequests(openAi.getLogRequests())
                        .logResponses(openAi.getLogResponses())
                        .build();

            case HUGGING_FACE:
                HuggingFace huggingFace = properties.getEmbeddingModel().getHuggingFace();
                if (huggingFace == null || isNullOrBlank(huggingFace.getAccessToken())) {
                    throw illegalConfiguration("\n\nPlease define 'langchain4j.embedding-model.huggingface.access-token' property");
                }
                return HuggingFaceEmbeddingModel.builder()
                        .accessToken(huggingFace.getAccessToken())
                        .modelId(huggingFace.getModelId())
                        .waitForModel(huggingFace.getWaitForModel())
                        .timeout(huggingFace.getTimeout())
                        .build();

            case LOCAL_AI:
                LocalAi localAi = properties.getEmbeddingModel().getLocalAi();
                if (localAi == null || isNullOrBlank(localAi.getBaseUrl())) {
                    throw illegalConfiguration("\n\nPlease define 'langchain4j.embedding-model.localai.base-url' property");
                }
                if (isNullOrBlank(localAi.getModelName())) {
                    throw illegalConfiguration("\n\nPlease define 'langchain4j.embedding-model.localai.model-name' property");
                }
                return LocalAiEmbeddingModel.builder()
                        .baseUrl(localAi.getBaseUrl())
                        .modelName(localAi.getModelName())
                        .timeout(localAi.getTimeout())
                        .maxRetries(localAi.getMaxRetries())
                        .logRequests(localAi.getLogRequests())
                        .logResponses(localAi.getLogResponses())
                        .build();

            default:
                throw illegalConfiguration("Unsupported embedding model provider: %s", properties.getEmbeddingModel().getProvider());
        }
    }

    @Bean
    @Lazy
    @ConditionalOnMissingBean
    ModerationModel moderationModel(LangChain4jProperties properties) {
        if (properties.getModerationModel() == null) {
            throw illegalConfiguration("\n\nPlease define 'langchain4j.moderation-model' properties, for example:\n"
                    + "langchain4j.moderation-model.provider = openai\n"
                    + "langchain4j.moderation-model.openai.api-key = sk-...\n");
        }

        if (properties.getModerationModel().getProvider() != OPEN_AI) {
            throw illegalConfiguration("Unsupported moderation model provider: %s", properties.getModerationModel().getProvider());
        }

        OpenAi openAi = properties.getModerationModel().getOpenAi();
        if (openAi == null || isNullOrBlank(openAi.getApiKey())) {
            throw illegalConfiguration("\n\nPlease define 'langchain4j.moderation-model.openai.api-key' property");
        }

        return OpenAiModerationModel.builder()
                .apiKey(openAi.getApiKey())
                .modelName(openAi.getModelName())
                .timeout(openAi.getTimeout())
                .maxRetries(openAi.getMaxRetries())
                .logRequests(openAi.getLogRequests())
                .logResponses(openAi.getLogResponses())
                .build();
    }
}