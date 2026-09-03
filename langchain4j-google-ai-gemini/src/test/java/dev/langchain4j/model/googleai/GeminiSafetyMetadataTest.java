package dev.langchain4j.model.googleai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.googleai.GeminiContent.GeminiPart;
import dev.langchain4j.model.googleai.GeminiGenerateContentResponse.GeminiCandidate;
import dev.langchain4j.model.googleai.GeminiGenerateContentResponse.GeminiCandidate.GeminiFinishReason;
import dev.langchain4j.model.googleai.GeminiGenerateContentResponse.GeminiPromptFeedback;
import dev.langchain4j.model.googleai.GeminiGenerateContentResponse.GeminiUsageMetadata;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GeminiSafetyMetadataTest {

    private static final String TEST_MODEL_NAME = "gemini-pro";

    @Mock
    GeminiService mockGeminiService;

    private static final GeminiSafetyRating SAFETY_RATING =
            new GeminiSafetyRating(GeminiHarmCategory.HARM_CATEGORY_HARASSMENT, "HIGH", "HIGH", true);

    private static final GeminiPromptFeedback PROMPT_FEEDBACK =
            new GeminiPromptFeedback("PROHIBITED_CONTENT", List.of(SAFETY_RATING));

    @Test
    void should_expose_safety_ratings_and_block_reason_in_non_streaming_response() {
        // Given
        GeminiGenerateContentResponse geminiResponse = responseWith(
                new GeminiCandidate(
                        candidateContent("Hello"), GeminiFinishReason.SAFETY, null, null, List.of(SAFETY_RATING)),
                PROMPT_FEEDBACK);

        // When
        GoogleAiGeminiChatResponseMetadata metadata = chat(geminiResponse);

        // Then
        assertThat(metadata.safetyRatings()).containsExactly(SAFETY_RATING);
        assertThat(metadata.blockReason()).isEqualTo("PROHIBITED_CONTENT");
    }

    @Test
    void should_fall_back_to_prompt_feedback_ratings_when_candidate_has_none() {
        // Given
        GeminiGenerateContentResponse geminiResponse = responseWith(
                new GeminiCandidate(candidateContent("Hello"), GeminiFinishReason.SAFETY, null, null, null),
                PROMPT_FEEDBACK);

        // When
        GoogleAiGeminiChatResponseMetadata metadata = chat(geminiResponse);

        // Then
        assertThat(metadata.safetyRatings()).containsExactly(SAFETY_RATING);
        assertThat(metadata.blockReason()).isEqualTo("PROHIBITED_CONTENT");
    }

    @Test
    void should_expose_empty_collections_when_no_safety_data_is_present() {
        // Given
        GeminiGenerateContentResponse geminiResponse = responseWith(
                new GeminiCandidate(candidateContent("Hello"), GeminiFinishReason.STOP, null, null, null), null);

        // When
        GoogleAiGeminiChatResponseMetadata metadata = chat(geminiResponse);

        // Then
        assertThat(metadata.safetyRatings()).isEmpty();
        assertThat(metadata.blockReason()).isNull();
    }

    @Test
    void should_expose_safety_ratings_and_block_reason_in_streaming_response() {
        // Given
        GeminiStreamingResponseBuilder builder = new GeminiStreamingResponseBuilder(false, null);
        GeminiCandidate candidate = new GeminiCandidate(
                candidateContent("Hello"), GeminiFinishReason.SAFETY, null, null, List.of(SAFETY_RATING));

        // When
        builder.append(responseWith(candidate, PROMPT_FEEDBACK));
        ChatResponse chatResponse = builder.build();

        // Then
        GoogleAiGeminiChatResponseMetadata metadata = (GoogleAiGeminiChatResponseMetadata) chatResponse.metadata();
        assertThat(metadata.safetyRatings()).containsExactly(SAFETY_RATING);
        assertThat(metadata.blockReason()).isEqualTo("PROHIBITED_CONTENT");
    }

    @Test
    void should_expose_empty_collections_in_streaming_response_without_safety_data() {
        // Given
        GeminiStreamingResponseBuilder builder = new GeminiStreamingResponseBuilder(false, null);
        GeminiCandidate candidate =
                new GeminiCandidate(candidateContent("Hello"), GeminiFinishReason.STOP, null, null, null);

        // When
        builder.append(responseWith(candidate, null));
        ChatResponse chatResponse = builder.build();

        // Then
        GoogleAiGeminiChatResponseMetadata metadata = (GoogleAiGeminiChatResponseMetadata) chatResponse.metadata();
        assertThat(metadata.safetyRatings()).isEmpty();
        assertThat(metadata.blockReason()).isNull();
    }

    private GoogleAiGeminiChatResponseMetadata chat(GeminiGenerateContentResponse geminiResponse) {
        when(mockGeminiService.generateContent(eq(TEST_MODEL_NAME), any(GeminiGenerateContentRequest.class)))
                .thenReturn(geminiResponse);

        GoogleAiGeminiChatModel model = GoogleAiGeminiChatModel.builder()
                .apiKey("test-api-key")
                .modelName(TEST_MODEL_NAME)
                .build(mockGeminiService);

        ChatResponse chatResponse =
                model.chat(ChatRequest.builder().messages(new UserMessage("Hi")).build());
        return (GoogleAiGeminiChatResponseMetadata) chatResponse.metadata();
    }

    private static GeminiGenerateContentResponse responseWith(
            GeminiCandidate candidate, GeminiPromptFeedback promptFeedback) {
        return new GeminiGenerateContentResponse(
                "response-id-123",
                "gemini-pro-v1",
                List.of(candidate),
                GeminiUsageMetadata.builder()
                        .promptTokenCount(10)
                        .candidatesTokenCount(20)
                        .totalTokenCount(30)
                        .build(),
                null,
                promptFeedback);
    }

    private static GeminiContent candidateContent(String text) {
        return new GeminiContent(List.of(GeminiPart.builder().text(text).build()), "model");
    }
}
