package dev.langchain4j.model.googleai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GoogleAiGeminiEnhancedCivicAnswersTest {

    private static final String TEST_MODEL_NAME = "gemini-1.5-pro";

    @Mock
    GeminiService mockGeminiService;

    @Captor
    ArgumentCaptor<GeminiGenerateContentRequest> requestCaptor;

    @Test
    void shouldForwardEnableEnhancedCivicAnswersWhenEnabled() {
        var model = GoogleAiGeminiChatModel.builder()
                .apiKey("test-key")
                .modelName(TEST_MODEL_NAME)
                .enableEnhancedCivicAnswers(true)
                .build(mockGeminiService);

        var chatRequest =
                ChatRequest.builder().messages(UserMessage.from("Hello")).build();
        when(mockGeminiService.generateContent(any(), any())).thenReturn(createSimpleResponse("Hi"));

        model.chat(chatRequest);

        verify(mockGeminiService).generateContent(eq(TEST_MODEL_NAME), requestCaptor.capture());
        assertThat(requestCaptor.getValue().generationConfig().enableEnhancedCivicAnswers())
                .isTrue();
    }

    @Test
    void shouldDefaultEnableEnhancedCivicAnswersToFalse() {
        var model = GoogleAiGeminiChatModel.builder()
                .apiKey("test-key")
                .modelName(TEST_MODEL_NAME)
                .build(mockGeminiService);

        var chatRequest =
                ChatRequest.builder().messages(UserMessage.from("Hello")).build();
        when(mockGeminiService.generateContent(any(), any())).thenReturn(createSimpleResponse("Hi"));

        model.chat(chatRequest);

        verify(mockGeminiService).generateContent(eq(TEST_MODEL_NAME), requestCaptor.capture());
        assertThat(requestCaptor.getValue().generationConfig().enableEnhancedCivicAnswers())
                .isFalse();
    }

    private GeminiGenerateContentResponse createSimpleResponse(String text) {
        var candidate = new GeminiGenerateContentResponse.GeminiCandidate(
                new GeminiContent(
                        List.of(GeminiContent.GeminiPart.builder().text(text).build()), "model"),
                GeminiGenerateContentResponse.GeminiCandidate.GeminiFinishReason.STOP,
                null,
                null);
        var usageMetadata = new GeminiGenerateContentResponse.GeminiUsageMetadata(0, 0, 0, null, null);
        return new GeminiGenerateContentResponse("id", "model", List.of(candidate), usageMetadata, null);
    }
}
