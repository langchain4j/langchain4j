package dev.langchain4j.model.googleai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.googleai.GeminiGenerateContentRequest.GeminiTool;
import dev.langchain4j.model.googleai.GeminiGenerateContentResponse.GeminiCandidate;
import dev.langchain4j.model.googleai.GeminiGenerateContentResponse.GeminiCandidate.GeminiFinishReason;
import dev.langchain4j.model.googleai.GeminiGenerateContentResponse.GeminiUrlContextMetadata;
import dev.langchain4j.model.googleai.GeminiGenerateContentResponse.GeminiUrlMetadata;
import dev.langchain4j.model.googleai.GeminiGenerateContentResponse.GeminiUrlRetrievalStatus;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GoogleAiGeminiUrlContextTest {

    private static final String TEST_MODEL_NAME = "gemini-1.5-pro";

    @Mock
    GeminiService mockGeminiService;

    @Nested
    class BuilderTest {
        @Test
        void shouldDefaultAllowUrlContextToFalse() {
            var model = GoogleAiGeminiChatModel.builder()
                    .apiKey("test-key")
                    .modelName(TEST_MODEL_NAME)
                    .build();

            assertThat(model.allowUrlContext).isFalse();
        }

        @Test
        void shouldSetAllowUrlContext() {
            var model = GoogleAiGeminiChatModel.builder()
                    .apiKey("test-key")
                    .modelName(TEST_MODEL_NAME)
                    .allowUrlContext(true)
                    .build();

            assertThat(model.allowUrlContext).isTrue();
        }
    }

    @Nested
    class RequestTest {
        @Captor
        ArgumentCaptor<GeminiGenerateContentRequest> requestCaptor;

        @Test
        void shouldNotIncludeUrlContextToolWhenDisabled() {
            // Given
            var model = GoogleAiGeminiChatModel.builder()
                    .apiKey("test-key")
                    .modelName(TEST_MODEL_NAME)
                    .allowUrlContext(false)
                    .build(mockGeminiService);

            var chatRequest =
                    ChatRequest.builder().messages(UserMessage.from("Hello")).build();

            when(mockGeminiService.generateContent(any(), any())).thenReturn(createSimpleResponse("Hi"));

            // When
            model.chat(chatRequest);

            // Then
            verify(mockGeminiService).generateContent(eq(TEST_MODEL_NAME), requestCaptor.capture());
            var request = requestCaptor.getValue();
            assertThat(request.tools()).isNull();
        }

        @Test
        void shouldIncludeUrlContextToolWhenEnabled() {
            // Given
            var model = GoogleAiGeminiChatModel.builder()
                    .apiKey("test-key")
                    .modelName(TEST_MODEL_NAME)
                    .allowUrlContext(true)
                    .build(mockGeminiService);

            var chatRequest = ChatRequest.builder()
                    .messages(UserMessage.from("Search for something"))
                    .build();

            when(mockGeminiService.generateContent(any(), any())).thenReturn(createSimpleResponse("Here is result"));

            // When
            model.chat(chatRequest);

            // Then
            verify(mockGeminiService).generateContent(eq(TEST_MODEL_NAME), requestCaptor.capture());
            var request = requestCaptor.getValue();
            assertThat(request.tools()).isNotNull();

            GeminiTool tool = request.tools();
            assertThat(tool.urlContext()).isNotNull();
        }
    }

    @Nested
    class ResponseTest {
        @Test
        void shouldHandleResponseWithUrlContext() {
            // Given
            var model = GoogleAiGeminiChatModel.builder()
                    .apiKey("test-key")
                    .modelName(TEST_MODEL_NAME)
                    .allowUrlContext(true)
                    .build(mockGeminiService);

            var urlMetadata = new GeminiUrlMetadata(
                    "https://example.com/context", GeminiUrlRetrievalStatus.URL_RETRIEVAL_STATUS_SUCCESS);
            var urlContextMetadata = new GeminiUrlContextMetadata(List.of(urlMetadata));

            GeminiCandidate candidate = new GeminiCandidate(
                    new GeminiContent(
                            List.of(GeminiContent.GeminiPart.builder()
                                    .text("Context found")
                                    .build()),
                            "model"),
                    GeminiFinishReason.STOP,
                    urlContextMetadata);

            var usageMetadata = new GeminiGenerateContentResponse.GeminiUsageMetadata(0, 0, 0);
            var response = new GeminiGenerateContentResponse("id", "model", List.of(candidate), usageMetadata, null);

            when(mockGeminiService.generateContent(any(), any())).thenReturn(response);

            // When
            var chatResponse = model.chat("query");

            // Then
            assertThat(chatResponse).isNotNull();
            assertThat(chatResponse).isEqualTo("Context found");
        }
    }

    private GeminiGenerateContentResponse createSimpleResponse(String text) {
        var candidate = new GeminiCandidate(
                new GeminiContent(
                        List.of(GeminiContent.GeminiPart.builder().text(text).build()), "model"),
                GeminiFinishReason.STOP,
                null);
        var usageMetadata = new GeminiGenerateContentResponse.GeminiUsageMetadata(0, 0, 0);
        return new GeminiGenerateContentResponse("id", "model", List.of(candidate), usageMetadata, null);
    }
}
