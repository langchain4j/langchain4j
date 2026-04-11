package dev.langchain4j.model.googleai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.googleai.GeminiGenerateContentResponse.GeminiCandidate;
import dev.langchain4j.model.googleai.GeminiGenerateContentResponse.GeminiCandidate.GeminiFinishReason;
import dev.langchain4j.model.googleai.GeminiGenerateContentResponse.GeminiUrlContextMetadata;
import dev.langchain4j.model.googleai.GeminiGenerateContentResponse.GeminiUrlMetadata;
import dev.langchain4j.model.googleai.GeminiGenerateContentResponse.GeminiUrlRetrievalStatus;
import dev.langchain4j.model.googleai.GeminiGenerateContentResponse.GeminiUsageMetadata;
import dev.langchain4j.model.googleai.GeminiGenerationConfig.GeminiImageConfig;
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
                    null,
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
                    null,
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

        @Test
        void shouldReturnServerToolResults() {
            GroundingMetadata groundingMetadata = GroundingMetadata.builder()
                    .webSearchQueries(List.of("langchain4j"))
                    .googleMapsWidgetContextToken("widget-token")
                    .build();
            GeminiContent content = new GeminiContent(
                    List.of(
                            GeminiContent.GeminiPart.builder()
                                    .executableCode(new GeminiContent.GeminiPart.GeminiExecutableCode(
                                            GeminiContent.GeminiPart.GeminiExecutableCode.GeminiLanguage.PYTHON,
                                            "print(1)"))
                                    .build(),
                            GeminiContent.GeminiPart.builder()
                                    .codeExecutionResult(new GeminiContent.GeminiPart.GeminiCodeExecutionResult(
                                            GeminiContent.GeminiPart.GeminiCodeExecutionResult.GeminiOutcome.OUTCOME_OK,
                                            "1"))
                                    .build()),
                    "model");
            GeminiCandidate candidate = new GeminiCandidate(
                    content,
                    GeminiFinishReason.STOP,
                    new GeminiUrlContextMetadata(List.of(new GeminiUrlMetadata(
                            "https://example.com", GeminiUrlRetrievalStatus.URL_RETRIEVAL_STATUS_SUCCESS))),
                    groundingMetadata);
            GeminiGenerateContentResponse response = new GeminiGenerateContentResponse(
                    "server-tool-response-id", "gemini-pro-v1", List.of(candidate), createUsageMetadata(1, 2, 3), null);

            when(mockGeminiService.generateContent(eq(TEST_MODEL_NAME), any(GeminiGenerateContentRequest.class)))
                    .thenReturn(response);

            var subject = GoogleAiGeminiChatModel.builder()
                    .apiKey("test-api-key")
                    .modelName(TEST_MODEL_NAME)
                    .returnServerToolResults(true)
                    .build(mockGeminiService);

            var chatResponse = subject.chat(ChatRequest.builder()
                    .messages(new UserMessage("Test tools"))
                    .build());

            assertThat(chatResponse.aiMessage().attributes())
                    .containsKey(GeminiServerToolsMapper.SERVER_TOOL_RESULTS_KEY);
            List<GoogleAiGeminiServerToolResult> results = (List<GoogleAiGeminiServerToolResult>)
                    chatResponse.aiMessage().attributes().get(GeminiServerToolsMapper.SERVER_TOOL_RESULTS_KEY);
            assertThat(results)
                    .extracting(GoogleAiGeminiServerToolResult::type)
                    .contains("code_execution_tool_result", "url_context_tool_result", "google_search_tool_result");

            assertThat(results.stream()
                            .filter(result -> "url_context_tool_result".equals(result.type()))
                            .findFirst())
                    .isPresent()
                    .get()
                    .extracting(GoogleAiGeminiServerToolResult::content)
                    .satisfies(resultContent ->
                            assertThat((Map<String, Object>) resultContent).containsKey("url_metadata"));

            assertThat(results.stream()
                            .filter(result -> "google_search_tool_result".equals(result.type()))
                            .findFirst())
                    .isPresent()
                    .get()
                    .extracting(GoogleAiGeminiServerToolResult::content)
                    .satisfies(resultContent -> assertThat((Map<String, Object>) resultContent)
                            .containsEntry("web_search_queries", List.of("langchain4j")));
        }

        @Test
        void shouldNotReturnServerToolResultsWhenDisabled() {
            GroundingMetadata groundingMetadata = GroundingMetadata.builder()
                    .webSearchQueries(List.of("langchain4j"))
                    .build();
            GeminiGenerateContentResponse response = new GeminiGenerateContentResponse(
                    "server-tool-response-id",
                    "gemini-pro-v1",
                    List.of(new GeminiCandidate(
                            new GeminiContent(
                                    List.of(GeminiContent.GeminiPart.builder()
                                            .text("Response")
                                            .build()),
                                    "model"),
                            GeminiFinishReason.STOP,
                            null,
                            null)),
                    createUsageMetadata(1, 1, 2),
                    groundingMetadata);

            when(mockGeminiService.generateContent(eq(TEST_MODEL_NAME), any(GeminiGenerateContentRequest.class)))
                    .thenReturn(response);

            var subject = GoogleAiGeminiChatModel.builder()
                    .apiKey("test-api-key")
                    .modelName(TEST_MODEL_NAME)
                    .returnServerToolResults(false)
                    .build(mockGeminiService);

            var chatResponse = subject.chat(ChatRequest.builder()
                    .messages(new UserMessage("Test tools"))
                    .build());

            assertThat(chatResponse.aiMessage().attributes())
                    .doesNotContainKey(GeminiServerToolsMapper.SERVER_TOOL_RESULTS_KEY);
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

        @Test
        void shouldSendImageConfigWhenConfigured() {
            // Given
            var expectedResponse = createGeminiResponse("Response");
            when(mockGeminiService.generateContent(eq(TEST_MODEL_NAME), any(GeminiGenerateContentRequest.class)))
                    .thenReturn(expectedResponse);

            var subject = GoogleAiGeminiChatModel.builder()
                    .apiKey("test-api-key")
                    .modelName(TEST_MODEL_NAME)
                    .imageAspectRatio("16:9")
                    .imageSize("2K")
                    .build(mockGeminiService);

            var chatRequest = ChatRequest.builder()
                    .messages(new UserMessage("Generate image"))
                    .build();

            // When
            subject.chat(chatRequest);

            // Then
            verify(mockGeminiService).generateContent(eq(TEST_MODEL_NAME), requestCaptor.capture());
            var request = requestCaptor.getValue();

            assertThat(request.generationConfig()).isNotNull();
            assertThat(request.generationConfig().imageConfig())
                    .isEqualTo(GeminiImageConfig.builder()
                            .aspectRatio("16:9")
                            .imageSize("2K")
                            .build());
        }

        @Test
        void shouldIncludeServerToolsInRequest() {
            var expectedResponse = createGeminiResponse("Response");
            when(mockGeminiService.generateContent(eq(TEST_MODEL_NAME), any(GeminiGenerateContentRequest.class)))
                    .thenReturn(expectedResponse);

            var subject = GoogleAiGeminiChatModel.builder()
                    .apiKey("test-api-key")
                    .modelName(TEST_MODEL_NAME)
                    .serverTools(
                            GoogleAiGeminiServerTool.builder()
                                    .type("google_search")
                                    .build(),
                            GoogleAiGeminiServerTool.builder()
                                    .type("google_maps")
                                    .addAttribute("enable_widget", true)
                                    .build())
                    .build(mockGeminiService);

            subject.chat(ChatRequest.builder()
                    .messages(new UserMessage("Test message"))
                    .build());

            verify(mockGeminiService).generateContent(eq(TEST_MODEL_NAME), requestCaptor.capture());

            GeminiGenerateContentRequest request = requestCaptor.getValue();
            assertThat(request.tools()).hasSize(1);
            assertThat(request.tools().get(0).googleSearch()).isNotNull();
            assertThat(request.tools().get(0).googleMaps()).isNotNull();
            assertThat(request.tools().get(0).googleMaps().enableWidget()).isTrue();
        }

        @Test
        void should_merge_legacy_flags_and_explicit_server_tools_without_duplicates() {
            var expectedResponse = createGeminiResponse("Response");
            when(mockGeminiService.generateContent(eq(TEST_MODEL_NAME), any(GeminiGenerateContentRequest.class)))
                    .thenReturn(expectedResponse);

            var subject = GoogleAiGeminiChatModel.builder()
                    .apiKey("test-api-key")
                    .modelName(TEST_MODEL_NAME)
                    .allowGoogleSearch(true)
                    .allowGoogleMaps(true)
                    .retrieveGoogleMapsWidgetToken(true)
                    .allowUrlContext(true)
                    .serverTools(
                            GoogleAiGeminiServerTool.builder()
                                    .type("google_search")
                                    .build(),
                            GoogleAiGeminiServerTool.builder()
                                    .type("google_maps")
                                    .build())
                    .build(mockGeminiService);

            subject.chat(ChatRequest.builder()
                    .messages(new UserMessage("Test message"))
                    .build());

            verify(mockGeminiService).generateContent(eq(TEST_MODEL_NAME), requestCaptor.capture());

            GeminiGenerateContentRequest request = requestCaptor.getValue();
            assertThat(request.tools()).hasSize(1);
            assertThat(request.tools().get(0).googleSearch()).isNotNull();
            assertThat(request.tools().get(0).urlContext()).isNotNull();
            assertThat(request.tools().get(0).googleMaps()).isNotNull();
            assertThat(request.tools().get(0).googleMaps().enableWidget()).isFalse();
        }

        @Test
        void should_reject_unsupported_server_tool_type() {
            var subject = GoogleAiGeminiChatModel.builder()
                    .apiKey("test-api-key")
                    .modelName(TEST_MODEL_NAME)
                    .serverTools(GoogleAiGeminiServerTool.builder()
                            .type("custom_tool")
                            .build())
                    .build(mockGeminiService);

            assertThatThrownBy(() -> subject.chat(ChatRequest.builder()
                            .messages(new UserMessage("Test message"))
                            .build()))
                    .isInstanceOf(UnsupportedFeatureException.class)
                    .hasMessageContaining("Unsupported Google AI Gemini server tool type: custom_tool");
        }
    }

    @Test
    void should_extract_google_maps_server_tool_result_with_exact_shape() {
        GroundingMetadata groundingMetadata = GroundingMetadata.builder()
                .groundingChunks(List.of(new GroundingMetadata.GroundingChunk(
                        null,
                        null,
                        new GroundingMetadata.GroundingChunk.Maps(
                                "https://maps.example.com", "Paris", "Landmark", "place-1", null))))
                .groundingSupports(List.of(new GroundingMetadata.GroundingSupport(List.of(0), List.of(0.9), null)))
                .googleMapsWidgetContextToken("widget-token")
                .build();

        List<GoogleAiGeminiServerToolResult> results =
                GeminiServerToolsMapper.extractServerToolResults(List.of(), null, groundingMetadata);

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.type()).isEqualTo("google_maps_tool_result");
            assertThat((Map<String, Object>) result.content())
                    .containsKeys("grounding_chunks", "grounding_supports", "google_maps_widget_context_token")
                    .containsEntry("google_maps_widget_context_token", "widget-token");
        });
    }

    private static GeminiGenerateContentResponse createGeminiResponse(String text) {
        var candidate = new GeminiCandidate(
                new GeminiContent(
                        List.of(GeminiContent.GeminiPart.builder().text(text).build()), "model"),
                GeminiFinishReason.STOP,
                null,
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
