package dev.langchain4j.model.googleai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.googleai.GeminiGenerateContentResponse.GeminiCandidate;
import dev.langchain4j.model.googleai.GeminiGenerateContentResponse.GeminiCandidate.GeminiFinishReason;
import dev.langchain4j.model.googleai.GeminiGenerateContentResponse.GeminiUsageMetadata;
import dev.langchain4j.model.output.FinishReason;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GoogleAiGeminiChatModelTest {
    private static final String TEST_MODEL_NAME = "gemini-pro";

    @Mock
    GeminiService mockGeminiService;

    @Nested
    class ChatTest {

        @Test
        void shouldSendChatRequest() {
            // Given
            var expectedResponse = createGeminiResponse("Hello, how can I help you?");
            when(mockGeminiService.generateContent(eq(TEST_MODEL_NAME), any(GeminiGenerateContentRequest.class)))
                    .thenReturn(expectedResponse);

            var subject = GoogleAiGeminiChatModel.builder()
                    .apiKey("test-api-key")
                    .modelName(TEST_MODEL_NAME)
                    .build(mockGeminiService);

            var chatRequest =
                    ChatRequest.builder().messages(new UserMessage("Hi")).build();

            // When
            var chatResponse = subject.chat(chatRequest);

            // Then
            assertThat(chatResponse.aiMessage().text()).isEqualTo("Hello, how can I help you?");
            verify(mockGeminiService).generateContent(eq(TEST_MODEL_NAME), any(GeminiGenerateContentRequest.class));
        }

        @Test
        void shouldReturnCorrectChatResponse() {
            // Given
            var expectedResponse = createGeminiResponse("Test response");
            when(mockGeminiService.generateContent(eq(TEST_MODEL_NAME), any(GeminiGenerateContentRequest.class)))
                    .thenReturn(expectedResponse);

            var subject = GoogleAiGeminiChatModel.builder()
                    .apiKey("test-api-key")
                    .modelName(TEST_MODEL_NAME)
                    .build(mockGeminiService);

            var chatRequest = ChatRequest.builder()
                    .messages(new UserMessage("Test message"))
                    .build();

            // When
            var chatResponse = subject.chat(chatRequest);

            // Then
            assertThat(chatResponse.aiMessage()).isNotNull();
            assertThat(chatResponse.aiMessage().text()).isEqualTo("Test response");
            assertThat(chatResponse.metadata()).isNotNull();
            assertThat(chatResponse.metadata().id()).isEqualTo("response-id-123");
            assertThat(chatResponse.metadata().modelName()).isEqualTo("gemini-pro-v1");
            assertThat(chatResponse.metadata().finishReason()).isEqualTo(FinishReason.STOP);
            assertThat(chatResponse.metadata().tokenUsage()).isNotNull();
            assertThat(chatResponse.metadata().tokenUsage().inputTokenCount()).isEqualTo(10);
            assertThat(chatResponse.metadata().tokenUsage().outputTokenCount()).isEqualTo(20);
            assertThat(chatResponse.metadata().tokenUsage().totalTokenCount()).isEqualTo(30);
        }

        @Test
        void shouldSendCorrectRequestToGeminiService() {
            // Given
            var expectedResponse = createGeminiResponse("Response");
            when(mockGeminiService.generateContent(eq(TEST_MODEL_NAME), any(GeminiGenerateContentRequest.class)))
                    .thenReturn(expectedResponse);

            var subject = GoogleAiGeminiChatModel.builder()
                    .apiKey("test-api-key")
                    .modelName(TEST_MODEL_NAME)
                    .temperature(0.7)
                    .topK(40)
                    .topP(0.95)
                    .maxOutputTokens(1024)
                    .build(mockGeminiService);

            var chatRequest =
                    ChatRequest.builder().messages(new UserMessage("Hello")).build();

            // When
            subject.chat(chatRequest);

            // Then
            verify(mockGeminiService).generateContent(eq(TEST_MODEL_NAME), any(GeminiGenerateContentRequest.class));
        }

        @Test
        void shouldHandleMultipleMessages() {
            // Given
            var expectedResponse = createGeminiResponse("I understand your question");
            when(mockGeminiService.generateContent(eq(TEST_MODEL_NAME), any(GeminiGenerateContentRequest.class)))
                    .thenReturn(expectedResponse);

            var subject = GoogleAiGeminiChatModel.builder()
                    .apiKey("test-api-key")
                    .modelName(TEST_MODEL_NAME)
                    .build(mockGeminiService);

            var chatRequest = ChatRequest.builder()
                    .messages(
                            new UserMessage("First message"),
                            new AiMessage("First response"),
                            new UserMessage("Second message"))
                    .build();

            // When
            var chatResponse = subject.chat(chatRequest);

            // Then
            assertThat(chatResponse.aiMessage().text()).isEqualTo("I understand your question");
            verify(mockGeminiService).generateContent(eq(TEST_MODEL_NAME), any(GeminiGenerateContentRequest.class));
        }

        @Test
        void shouldHandleEmptyResponse() {
            // Given
            var expectedResponse = createGeminiResponse("");
            when(mockGeminiService.generateContent(eq(TEST_MODEL_NAME), any(GeminiGenerateContentRequest.class)))
                    .thenReturn(expectedResponse);

            var subject = GoogleAiGeminiChatModel.builder()
                    .apiKey("test-api-key")
                    .modelName(TEST_MODEL_NAME)
                    .build(mockGeminiService);

            var chatRequest =
                    ChatRequest.builder().messages(new UserMessage("Test")).build();

            // When
            var chatResponse = subject.chat(chatRequest);

            // Then
            assertThat(chatResponse.aiMessage())
                    .isEqualTo(AiMessage.builder()
                            .text(null)
                            .thinking(null)
                            .toolExecutionRequests(List.of())
                            .attributes(Map.of())
                            .build());
        }

        @Test
        void shouldIncludeTokenUsageInResponse() {
            // Given
            var usageMetadata = GeminiUsageMetadata.builder()
                    .promptTokenCount(15)
                    .candidatesTokenCount(25)
                    .totalTokenCount(40)
                    .build();

            var candidate = new GeminiCandidate(
                    new GeminiContent(
                            List.of(GeminiContent.GeminiPart.builder()
                                    .text("Response with tokens")
                                    .build()),
                            "model"),
                    GeminiFinishReason.STOP,
                    null);

            var geminiResponse = new GeminiGenerateContentResponse(
                    "token-response-id", "gemini-pro-v1", List.of(candidate), usageMetadata, null);

            when(mockGeminiService.generateContent(eq(TEST_MODEL_NAME), any(GeminiGenerateContentRequest.class)))
                    .thenReturn(geminiResponse);

            var subject = GoogleAiGeminiChatModel.builder()
                    .apiKey("test-api-key")
                    .modelName(TEST_MODEL_NAME)
                    .build(mockGeminiService);

            var chatRequest = ChatRequest.builder()
                    .messages(new UserMessage("Count tokens"))
                    .build();

            // When
            var chatResponse = subject.chat(chatRequest);

            // Then
            assertThat(chatResponse.metadata().tokenUsage().inputTokenCount()).isEqualTo(15);
            assertThat(chatResponse.metadata().tokenUsage().outputTokenCount()).isEqualTo(25);
            assertThat(chatResponse.metadata().tokenUsage().totalTokenCount()).isEqualTo(40);
        }

        @Test
        void shouldHandleDifferentFinishReasons() {
            // Given
            var candidate = new GeminiCandidate(
                    new GeminiContent(
                            List.of(GeminiContent.GeminiPart.builder()
                                    .text("Partial response")
                                    .build()),
                            "mode"),
                    GeminiFinishReason.MAX_TOKENS,
                    null);

            var geminiResponse = new GeminiGenerateContentResponse(
                    "finish-reason-id", "gemini-pro-v1", List.of(candidate), createUsageMetadata(10, 20, 30), null);

            when(mockGeminiService.generateContent(eq(TEST_MODEL_NAME), any(GeminiGenerateContentRequest.class)))
                    .thenReturn(geminiResponse);

            var subject = GoogleAiGeminiChatModel.builder()
                    .apiKey("test-api-key")
                    .modelName(TEST_MODEL_NAME)
                    .build(mockGeminiService);

            var chatRequest = ChatRequest.builder()
                    .messages(new UserMessage("Test finish reason"))
                    .build();

            // When
            var chatResponse = subject.chat(chatRequest);

            // Then
            assertThat(chatResponse.metadata().finishReason()).isEqualTo(FinishReason.LENGTH);
        }
    }

    @Nested
    class RequestVerificationTest {
        @Captor
        ArgumentCaptor<GeminiGenerateContentRequest> requestCaptor;

        @Test
        void shouldSendRequestWithAllParameters() {
            // Given
            var expectedResponse = createGeminiResponse("Response");
            when(mockGeminiService.generateContent(eq(TEST_MODEL_NAME), any(GeminiGenerateContentRequest.class)))
                    .thenReturn(expectedResponse);

            var subject = GoogleAiGeminiChatModel.builder()
                    .apiKey("test-api-key")
                    .modelName(TEST_MODEL_NAME)
                    .temperature(0.7)
                    .topK(40)
                    .topP(0.95)
                    .maxOutputTokens(1024)
                    .presencePenalty(0.5)
                    .frequencyPenalty(0.3)
                    .seed(42)
                    .build(mockGeminiService);

            var chatRequest = ChatRequest.builder()
                    .messages(new UserMessage("Test message"))
                    .parameters(ChatRequestParameters.builder()
                            .temperature(0.8)
                            .topK(50)
                            .topP(0.9)
                            .maxOutputTokens(2048)
                            .presencePenalty(0.6)
                            .frequencyPenalty(0.4)
                            .stopSequences(List.of("STOP", "END"))
                            .build())
                    .build();

            // When
            subject.chat(chatRequest);

            // Then
            verify(mockGeminiService).generateContent(eq(TEST_MODEL_NAME), requestCaptor.capture());

            var request = requestCaptor.getValue();
            assertThat(request.contents()).isNotEmpty();
            assertThat(request.generationConfig())
                    .isEqualTo(GeminiGenerationConfig.builder()
                            .temperature(0.8)
                            .responseMimeType("text/plain")
                            .topK(50)
                            .topP(0.9)
                            .maxOutputTokens(2048)
                            .presencePenalty(0.6)
                            .frequencyPenalty(0.4)
                            .stopSequences(List.of("STOP", "END"))
                            .seed(42)
                            .candidateCount(1)
                            .responseLogprobs(false)
                            .build());
        }
    }

    private static GeminiGenerateContentResponse createGeminiResponse(String text) {
        var candidate = new GeminiCandidate(
                new GeminiContent(
                        List.of(GeminiContent.GeminiPart.builder().text(text).build()), "model"),
                GeminiFinishReason.STOP,
                null);

        return new GeminiGenerateContentResponse(
                "response-id-123", "gemini-pro-v1", List.of(candidate), createUsageMetadata(10, 20, 30), null);
    }

    private static GeminiUsageMetadata createUsageMetadata(int promptTokens, int candidateTokens, int totalTokens) {
        return GeminiUsageMetadata.builder()
                .promptTokenCount(promptTokens)
                .candidatesTokenCount(candidateTokens)
                .totalTokenCount(totalTokens)
                .build();
    }
}
