package dev.langchain4j.openai.spring;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.model.language.LanguageModel;
import dev.langchain4j.model.language.StreamingLanguageModel;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.model.openai.*;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = "sk.*")
class AutoConfigIT {

    private static final String API_KEY = System.getenv("OPENAI_API_KEY");

    ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AutoConfig.class));

    @Test
    void should_provide_chat_model() {
        contextRunner
                .withPropertyValues(
                        "langchain4j.open-ai.chat-model.api-key=" + API_KEY,
                        "langchain4j.open-ai.chat-model.max-tokens=20"
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
    void should_provide_streaming_chat_model() {
        contextRunner
                .withPropertyValues(
                        "langchain4j.open-ai.streaming-chat-model.api-key=" + API_KEY,
                        "langchain4j.open-ai.streaming-chat-model.max-tokens=20"
                )
                .run(context -> {

                    StreamingChatLanguageModel streamingChatLanguageModel = context.getBean(StreamingChatLanguageModel.class);
                    assertThat(streamingChatLanguageModel).isInstanceOf(OpenAiStreamingChatModel.class);
                    CompletableFuture<Response<AiMessage>> future = new CompletableFuture<>();
                    streamingChatLanguageModel.generate("What is the capital of Germany?", new StreamingResponseHandler<AiMessage>() {

                        @Override
                        public void onNext(String token) {
                        }

                        @Override
                        public void onComplete(Response<AiMessage> response) {
                            future.complete(response);
                        }

                        @Override
                        public void onError(Throwable error) {
                        }
                    });
                    Response<AiMessage> response = future.get(30, TimeUnit.SECONDS);
                    assertThat(response.content().text()).contains("Berlin");

                    OpenAiStreamingChatModel openAiStreamingChatModel = context.getBean(OpenAiStreamingChatModel.class);
                    assertThat(openAiStreamingChatModel).isNotNull();
                });
    }

    @Test
    void should_provide_language_model() {
        contextRunner
                .withPropertyValues(
                        "langchain4j.open-ai.language-model.api-key=" + API_KEY,
                        "langchain4j.open-ai.language-model.max-tokens=20"
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
    void should_provide_streaming_language_model() {
        contextRunner
                .withPropertyValues(
                        "langchain4j.open-ai.streaming-language-model.api-key=" + API_KEY,
                        "langchain4j.open-ai.streaming-language-model.max-tokens=20"
                )
                .run(context -> {

                    StreamingLanguageModel streamingLanguageModel = context.getBean(StreamingLanguageModel.class);
                    assertThat(streamingLanguageModel).isInstanceOf(OpenAiStreamingLanguageModel.class);
                    CompletableFuture<Response<String>> future = new CompletableFuture<>();
                    streamingLanguageModel.generate("What is the capital of Germany?", new StreamingResponseHandler<String>() {

                        @Override
                        public void onNext(String token) {
                        }

                        @Override
                        public void onComplete(Response<String> response) {
                            future.complete(response);
                        }

                        @Override
                        public void onError(Throwable error) {
                        }
                    });
                    Response<String> response = future.get(30, TimeUnit.SECONDS);
                    assertThat(response.content()).contains("Berlin");

                    OpenAiStreamingLanguageModel openAiStreamingLanguageModel = context.getBean(OpenAiStreamingLanguageModel.class);
                    assertThat(openAiStreamingLanguageModel).isNotNull();
                });
    }

    @Test
    void should_provide_embedding_model() {
        contextRunner
                .withPropertyValues("langchain4j.open-ai.embedding-model.api-key=" + API_KEY)
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
                .withPropertyValues("langchain4j.open-ai.moderation-model.api-key=" + API_KEY)
                .run(context -> {

                    ModerationModel moderationModel = context.getBean(ModerationModel.class);
                    assertThat(moderationModel).isInstanceOf(OpenAiModerationModel.class);
                    assertThat(moderationModel.moderate("He wants to kill them.").content().flagged()).isTrue();

                    OpenAiModerationModel openAiModerationModel = context.getBean(OpenAiModerationModel.class);
                    assertThat(openAiModerationModel.moderate("He wants to kill them.").content().flagged()).isTrue();
                });
    }

    @Test
    void should_provide_image_model() {
        contextRunner
                .withPropertyValues(
                        "langchain4j.open-ai.image-model.api-key=" + API_KEY,
                        "langchain4j.open-ai.image-model.model-name=dall-e-2",
                        "langchain4j.open-ai.image-model.size=256x256"
                )
                .run(context -> {

                    ImageModel imageModel = context.getBean(ImageModel.class);
                    assertThat(imageModel).isInstanceOf(OpenAiImageModel.class);
                    assertThat(imageModel.generate("banana").content().url()).isNotNull();

                    OpenAiImageModel openAiImageModel = context.getBean(OpenAiImageModel.class);
                    assertThat(openAiImageModel.generate("banana").content().url()).isNotNull();
                });
    }
}