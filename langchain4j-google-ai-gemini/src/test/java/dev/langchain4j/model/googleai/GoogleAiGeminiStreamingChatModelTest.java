package dev.langchain4j.model.googleai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCharSequence;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.googleai.GeminiGenerationConfig.GeminiImageConfig;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GoogleAiGeminiStreamingChatModelTest {
    private static final ChatRequest DEFAULT_REQUEST = ChatRequest.builder()
            .messages(new UserMessage("Hi"))
            .parameters(GoogleAiGeminiChatRequestParameters.builder().build())
            .build();

    @Mock
    GeminiService geminiService;

    @Mock
    StreamingChatResponseHandler handler;

    @Captor
    ArgumentCaptor<GeminiGenerateContentRequest> requestCaptor;

    @Test
    void should_fail_when_empty_messages_provided() {
        // when/then
        assertThatThrownBy(() -> ChatRequest.builder().messages().build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("messages cannot be null or empty");
    }

    @Nested
    class GoogleAiGeminiStreamingChatModelBuilder {

        @Test
        void seedParameterInContentRequest() {
            GoogleAiGeminiStreamingChatModel chatModel = GoogleAiGeminiStreamingChatModel.builder()
                    .apiKey("ApiKey")
                    .modelName("ModelName")
                    .seed(42)
                    .build();
            GeminiGenerateContentRequest result = chatModel.createGenerateContentRequest(DEFAULT_REQUEST);

            assertThatCharSequence(Json.toJson(result.generationConfig())).contains("\"seed\" : 42");
        }

        @Test
        void defaultSeedInContentRequest() {
            GoogleAiGeminiStreamingChatModel chatModel = GoogleAiGeminiStreamingChatModel.builder()
                    .apiKey("ApiKey")
                    .modelName("ModelName")
                    .build();
            GeminiGenerateContentRequest result = chatModel.createGenerateContentRequest(DEFAULT_REQUEST);

            assertThatCharSequence(Json.toJson(result.generationConfig())).doesNotContain("\"seed\"");
        }

        @Test
        void shouldSendImageConfigWhenConfigured() {
            GoogleAiGeminiStreamingChatModel.GoogleAiGeminiStreamingChatModelBuilder builder =
                    GoogleAiGeminiStreamingChatModel.builder()
                            .apiKey("ApiKey")
                            .modelName("ModelName")
                            .imageAspectRatio("16:9")
                            .imageSize("2K");
            GoogleAiGeminiStreamingChatModel chatModel = new GoogleAiGeminiStreamingChatModel(builder, geminiService);

            chatModel.chat(
                    ChatRequest.builder()
                            .messages(new UserMessage("Generate image"))
                            .build(),
                    handler);

            verify(geminiService)
                    .generateContentStream(eq("ModelName"), requestCaptor.capture(), eq(false), eq(null), any());

            assertThat(requestCaptor.getValue().generationConfig().imageConfig())
                    .isEqualTo(GeminiImageConfig.builder()
                            .aspectRatio("16:9")
                            .imageSize("2K")
                            .build());
        }

        @Test
        void shouldUseRequestLevelImageConfigWhenProvided() {
            GoogleAiGeminiStreamingChatModel.GoogleAiGeminiStreamingChatModelBuilder builder =
                    GoogleAiGeminiStreamingChatModel.builder()
                            .apiKey("ApiKey")
                            .modelName("ModelName")
                            .imageAspectRatio("16:9")
                            .imageSize("2K");
            GoogleAiGeminiStreamingChatModel chatModel = new GoogleAiGeminiStreamingChatModel(builder, geminiService);

            ChatRequest chatRequest = ChatRequest.builder()
                    .messages(new UserMessage("Generate image"))
                    .parameters(GoogleAiGeminiChatRequestParameters.builder()
                            .imageAspectRatio("1:1")
                            .imageSize("1K")
                            .build())
                    .build();

            chatModel.chat(chatRequest, handler);

            verify(geminiService)
                    .generateContentStream(eq("ModelName"), requestCaptor.capture(), eq(false), eq(null), any());

            assertThat(requestCaptor.getValue().generationConfig().imageConfig())
                    .isEqualTo(GeminiImageConfig.builder()
                            .aspectRatio("1:1")
                            .imageSize("1K")
                            .build());
        }

        @Test
        void cachedContentNameInContentRequest() {
            GoogleAiGeminiStreamingChatModel chatModel = GoogleAiGeminiStreamingChatModel.builder()
                    .apiKey("ApiKey")
                    .modelName("ModelName")
                    .cachedContentName("cachedContents/abc123")
                    .build();
            GeminiGenerateContentRequest result = chatModel.createGenerateContentRequest(DEFAULT_REQUEST);

            assertThat(result.cachedContent()).isEqualTo("cachedContents/abc123");
            assertThatCharSequence(Json.toJson(result)).contains("\"cachedContent\" : \"cachedContents/abc123\"");
        }

        @Test
        void defaultCachedContentNameInContentRequest() {
            GoogleAiGeminiStreamingChatModel chatModel = GoogleAiGeminiStreamingChatModel.builder()
                    .apiKey("ApiKey")
                    .modelName("ModelName")
                    .build();
            GeminiGenerateContentRequest result = chatModel.createGenerateContentRequest(DEFAULT_REQUEST);

            assertThat(result.cachedContent()).isNull();
            assertThatCharSequence(Json.toJson(result)).doesNotContain("\"cachedContent\"");
        }

        @Test
        void enableEnhancedCivicAnswersInContentRequest() {
            GoogleAiGeminiStreamingChatModel chatModel = GoogleAiGeminiStreamingChatModel.builder()
                    .apiKey("ApiKey")
                    .modelName("ModelName")
                    .enableEnhancedCivicAnswers(true)
                    .build();
            GeminiGenerateContentRequest result = chatModel.createGenerateContentRequest(DEFAULT_REQUEST);

            assertThat(result.generationConfig().enableEnhancedCivicAnswers()).isTrue();
        }

        @Test
        void defaultEnableEnhancedCivicAnswersInContentRequest() {
            GoogleAiGeminiStreamingChatModel chatModel = GoogleAiGeminiStreamingChatModel.builder()
                    .apiKey("ApiKey")
                    .modelName("ModelName")
                    .build();
            GeminiGenerateContentRequest result = chatModel.createGenerateContentRequest(DEFAULT_REQUEST);

            assertThat(result.generationConfig().enableEnhancedCivicAnswers()).isFalse();
        }
    }
}
