package dev.langchain4j.openai.spring;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.language.LanguageModel;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.model.openai.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static dev.langchain4j.model.openai.OpenAiModelName.GPT_3_5_TURBO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = "sk.*")
class AutoConfigIT {

    private static final String CHAT_MODEL = "langchain4j.open-ai.chat-model";
    private static final String LANGUAGE_MODEL = "langchain4j.open-ai.language-model";
    private static final String EMBEDDING_MODEL = "langchain4j.open-ai.embedding-model";
    private static final String MODERATION_MODEL = "langchain4j.open-ai.moderation-model";
    private static final String API_KEY = ".api-key";
    private static final String API_KEY_VALUE = System.getenv("OPENAI_API_KEY");

    ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AutoConfig.class));

    @Test
    void should_fail_when_no_configuration() {
        contextRunner.run(context -> {

            assertThatThrownBy(() -> context.getBean(ChatLanguageModel.class)).hasMessageContaining(CHAT_MODEL);
            assertThatThrownBy(() -> context.getBean(OpenAiChatModel.class)).hasMessageContaining(CHAT_MODEL);

            assertThatThrownBy(() -> context.getBean(LanguageModel.class)).hasMessageContaining(LANGUAGE_MODEL);
            assertThatThrownBy(() -> context.getBean(OpenAiLanguageModel.class)).hasMessageContaining(LANGUAGE_MODEL);

            assertThatThrownBy(() -> context.getBean(EmbeddingModel.class)).hasMessageContaining(EMBEDDING_MODEL);
            assertThatThrownBy(() -> context.getBean(OpenAiEmbeddingModel.class)).hasMessageContaining(EMBEDDING_MODEL);

            assertThatThrownBy(() -> context.getBean(ModerationModel.class)).hasMessageContaining(MODERATION_MODEL);
            assertThatThrownBy(() -> context.getBean(OpenAiModerationModel.class)).hasMessageContaining(MODERATION_MODEL);
        });
    }

    @Test
    void should_fail_when_no_api_key() {
        contextRunner
                .withPropertyValues(
                        CHAT_MODEL + ".model-name = a",
                        LANGUAGE_MODEL + ".model-name = b",
                        EMBEDDING_MODEL + ".model-name = c",
                        MODERATION_MODEL + ".model-name = d"
                )
                .run(context -> {

                    assertThatThrownBy(() -> context.getBean(ChatLanguageModel.class))
                            .hasMessageContaining(CHAT_MODEL + API_KEY);
                    assertThatThrownBy(() -> context.getBean(OpenAiChatModel.class))
                            .hasMessageContaining(CHAT_MODEL + API_KEY);

                    assertThatThrownBy(() -> context.getBean(LanguageModel.class))
                            .hasMessageContaining(LANGUAGE_MODEL + API_KEY);
                    assertThatThrownBy(() -> context.getBean(OpenAiLanguageModel.class))
                            .hasMessageContaining(LANGUAGE_MODEL + API_KEY);

                    assertThatThrownBy(() -> context.getBean(EmbeddingModel.class))
                            .hasMessageContaining(EMBEDDING_MODEL + API_KEY);
                    assertThatThrownBy(() -> context.getBean(OpenAiEmbeddingModel.class))
                            .hasMessageContaining(EMBEDDING_MODEL + API_KEY);

                    assertThatThrownBy(() -> context.getBean(ModerationModel.class))
                            .hasMessageContaining(MODERATION_MODEL + API_KEY);
                    assertThatThrownBy(() -> context.getBean(OpenAiModerationModel.class))
                            .hasMessageContaining(MODERATION_MODEL + API_KEY);
                });
    }

    @Test
    void should_provide_chat_model() {
        contextRunner
                .withPropertyValues(
                        CHAT_MODEL + API_KEY + "=" + API_KEY_VALUE,
                        CHAT_MODEL + ".max-tokens = 20"
                )
                .run(context -> {

                    ChatLanguageModel chatLanguageModel = context.getBean(ChatLanguageModel.class);
                    assertThat(chatLanguageModel).isInstanceOf(OpenAiChatModel.class);
                    assertThat(chatLanguageModel.generate("What is the capital of Germany?")).contains("Berlin");

                    OpenAiChatModel openAiChatModel = context.getBean(OpenAiChatModel.class);
                    assertThat(openAiChatModel.generate("What is the capital of Germany?")).contains("Berlin");
                });
    }

    @Test
    void should_provide_language_model() {
        contextRunner
                .withPropertyValues(
                        LANGUAGE_MODEL + API_KEY + "=" + API_KEY_VALUE,
                        LANGUAGE_MODEL + ".max-tokens = 20"
                )
                .run(context -> {

                    LanguageModel languageModel = context.getBean(LanguageModel.class);
                    assertThat(languageModel).isInstanceOf(OpenAiLanguageModel.class);
                    assertThat(languageModel.generate("What is the capital of Germany?").content()).contains("Berlin");

                    OpenAiLanguageModel openAiLanguageModel = context.getBean(OpenAiLanguageModel.class);
                    assertThat(openAiLanguageModel.generate("What is the capital of Germany?").content()).contains("Berlin");
                });
    }

    @Test
    void should_provide_embedding_model() {
        contextRunner
                .withPropertyValues(EMBEDDING_MODEL + API_KEY + "=" + API_KEY_VALUE)
                .run(context -> {

                    EmbeddingModel embeddingModel = context.getBean(EmbeddingModel.class);
                    assertThat(embeddingModel).isInstanceOf(OpenAiEmbeddingModel.class);
                    assertThat(embeddingModel.embed("hi").content().dimension()).isEqualTo(1536);

                    OpenAiEmbeddingModel openAiEmbeddingModel = context.getBean(OpenAiEmbeddingModel.class);
                    assertThat(openAiEmbeddingModel.embed("hi").content().dimension()).isEqualTo(1536);
                });
    }

    @Test
    void should_provide_moderation_model() {
        contextRunner
                .withPropertyValues(MODERATION_MODEL + API_KEY + "=" + API_KEY_VALUE)
                .run(context -> {

                    ModerationModel moderationModel = context.getBean(ModerationModel.class);
                    assertThat(moderationModel).isInstanceOf(OpenAiModerationModel.class);
                    assertThat(moderationModel.moderate("He wants to kill them.").content().flagged()).isTrue();

                    OpenAiModerationModel openAiModerationModel = context.getBean(OpenAiModerationModel.class);
                    assertThat(openAiModerationModel.moderate("He wants to kill them.").content().flagged()).isTrue();
                });
    }

    @Test
    void should_provide_model_from_user_configuration() {
        contextRunner
                .withPropertyValues(CHAT_MODEL + API_KEY + "=" + API_KEY_VALUE)
                .withUserConfiguration(UserConfiguration.class)
                .run(context -> {

                    OpenAiChatModel openAiChatModel = context.getBean(OpenAiChatModel.class);

                    String response = openAiChatModel.generate("hi");

                    OpenAiTokenizer tokenizer = new OpenAiTokenizer(GPT_3_5_TURBO);
                    assertThat(tokenizer.estimateTokenCountInText(response)).isEqualTo(1);
                });
    }

    @Configuration
    static class UserConfiguration {

        @Bean
        OpenAiChatModel openAiChatModel() {
            return OpenAiChatModel.builder()
                    .apiKey(API_KEY_VALUE)
                    .maxTokens(1)
                    .build();
        }
    }
}