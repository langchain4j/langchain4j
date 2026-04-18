package dev.langchain4j.model.googleai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.googleai.GeminiGenerateContentResponse.GeminiCandidate;
import dev.langchain4j.model.googleai.GeminiGenerateContentResponse.GeminiCandidate.GeminiFinishReason;
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
            assertThat(chatResponse.metadata().tokenUsage()).isInstanceOf(GoogleAiGeminiTokenUsage.class);
            assertThat(chatResponse.metadata().tokenUsage().inputTokenCount()).isEqualTo(15);
            assertThat(chatResponse.metadata().tokenUsage().outputTokenCount()).isEqualTo(25);
            assertThat(chatResponse.metadata().tokenUsage().totalTokenCount()).isEqualTo(40);
            GoogleAiGeminiTokenUsage geminiTokenUsage =
                    (GoogleAiGeminiTokenUsage) chatResponse.metadata().tokenUsage();
            assertThat(geminiTokenUsage.cachedContentTokenCount()).isNull();
            assertThat(geminiTokenUsage.thoughtsTokenCount()).isNull();
        }

        @Test
        void shouldExposeCachedAndThinkingTokenCountsInResponse() {
            GeminiUsageMetadata usageMetadata = GeminiUsageMetadata.builder()
                    .promptTokenCount(120)
                    .candidatesTokenCount(30)
                    .totalTokenCount(200)
                    .cachedContentTokenCount(80)
                    .thoughtsTokenCount(50)
                    .build();

            GeminiCandidate candidate = new GeminiCandidate(
                    new GeminiContent(
                            List.of(GeminiContent.GeminiPart.builder()
                                    .text("Cached + thinking response")
                                    .build()),
                            "model"),
                    GeminiFinishReason.STOP,
                    null,
                    null);

            GeminiGenerateContentResponse geminiResponse = new GeminiGenerateContentResponse(
                    "cache-thinking-id", "gemini-2.5-pro", List.of(candidate), usageMetadata, null);

            when(mockGeminiService.generateContent(eq(TEST_MODEL_NAME), any(GeminiGenerateContentRequest.class)))
                    .thenReturn(geminiResponse);

            GoogleAiGeminiChatModel subject = GoogleAiGeminiChatModel.builder()
                    .apiKey("test-api-key")
                    .modelName(TEST_MODEL_NAME)
                    .build(mockGeminiService);

            ChatRequest chatRequest =
                    ChatRequest.builder().messages(new UserMessage("Hello")).build();

            ChatResponse chatResponse = subject.chat(chatRequest);

            assertThat(chatResponse.metadata().tokenUsage()).isInstanceOf(GoogleAiGeminiTokenUsage.class);
            GoogleAiGeminiTokenUsage tokenUsage =
                    (GoogleAiGeminiTokenUsage) chatResponse.metadata().tokenUsage();
            assertThat(tokenUsage.inputTokenCount()).isEqualTo(120);
            assertThat(tokenUsage.outputTokenCount()).isEqualTo(30);
            assertThat(tokenUsage.totalTokenCount()).isEqualTo(200);
            assertThat(tokenUsage.cachedContentTokenCount()).isEqualTo(80);
            assertThat(tokenUsage.thoughtsTokenCount()).isEqualTo(50);
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
    }

    @Nested
    class ToolConfigTest {
        @Captor
        ArgumentCaptor<GeminiGenerateContentRequest> requestCaptor;

        @Test
        void shouldSendValidatedModeInRequest() {
            // Given
            var expectedResponse = createGeminiResponse("Response");
            when(mockGeminiService.generateContent(eq(TEST_MODEL_NAME), any(GeminiGenerateContentRequest.class)))
                    .thenReturn(expectedResponse);

            var subject = GoogleAiGeminiChatModel.builder()
                    .apiKey("test-api-key")
                    .modelName(TEST_MODEL_NAME)
                    .toolConfig(GeminiMode.VALIDATED)
                    .build(mockGeminiService);

            var chatRequest = ChatRequest.builder()
                    .messages(new UserMessage("Call a function"))
                    .toolSpecifications(
                            ToolSpecification.builder().name("myTool").build())
                    .build();

            // When
            subject.chat(chatRequest);

            // Then
            verify(mockGeminiService).generateContent(eq(TEST_MODEL_NAME), requestCaptor.capture());
            var request = requestCaptor.getValue();
            assertThat(request.toolConfig()).isNotNull();
            assertThat(request.toolConfig().functionCallingConfig().getMode()).isEqualTo(GeminiMode.VALIDATED);
        }

        @Test
        void shouldSendValidatedModeWithAllowedFunctionNames() {
            // Given
            var expectedResponse = createGeminiResponse("Response");
            when(mockGeminiService.generateContent(eq(TEST_MODEL_NAME), any(GeminiGenerateContentRequest.class)))
                    .thenReturn(expectedResponse);

            var subject = GoogleAiGeminiChatModel.builder()
                    .apiKey("test-api-key")
                    .modelName(TEST_MODEL_NAME)
                    .toolConfig(GeminiMode.VALIDATED, "func1")
                    .build(mockGeminiService);

            var chatRequest = ChatRequest.builder()
                    .messages(new UserMessage("Call a function"))
                    .toolSpecifications(
                            ToolSpecification.builder().name("func1").build())
                    .build();

            // When
            subject.chat(chatRequest);

            // Then
            verify(mockGeminiService).generateContent(eq(TEST_MODEL_NAME), requestCaptor.capture());
            var request = requestCaptor.getValue();
            assertThat(request.toolConfig()).isNotNull();
            assertThat(request.toolConfig().functionCallingConfig().getMode()).isEqualTo(GeminiMode.VALIDATED);
            assertThat(request.toolConfig().functionCallingConfig().getAllowedFunctionNames())
                    .containsExactly("func1");
        }

        @Test
        void shouldSendAutoModeInRequest() {
            // Given
            var expectedResponse = createGeminiResponse("Response");
            when(mockGeminiService.generateContent(eq(TEST_MODEL_NAME), any(GeminiGenerateContentRequest.class)))
                    .thenReturn(expectedResponse);

            var subject = GoogleAiGeminiChatModel.builder()
                    .apiKey("test-api-key")
                    .modelName(TEST_MODEL_NAME)
                    .toolConfig(GeminiMode.AUTO)
                    .build(mockGeminiService);

            var chatRequest = ChatRequest.builder()
                    .messages(new UserMessage("Call a function"))
                    .toolSpecifications(
                            ToolSpecification.builder().name("myTool").build())
                    .build();

            // When
            subject.chat(chatRequest);

            // Then
            verify(mockGeminiService).generateContent(eq(TEST_MODEL_NAME), requestCaptor.capture());
            var request = requestCaptor.getValue();
            assertThat(request.toolConfig()).isNotNull();
            assertThat(request.toolConfig().functionCallingConfig().getMode()).isEqualTo(GeminiMode.AUTO);
        }

        @Test
        void shouldSendAnyModeWithAllowedFunctionNamesInRequest() {
            // Given
            var expectedResponse = createGeminiResponse("Response");
            when(mockGeminiService.generateContent(eq(TEST_MODEL_NAME), any(GeminiGenerateContentRequest.class)))
                    .thenReturn(expectedResponse);

            var subject = GoogleAiGeminiChatModel.builder()
                    .apiKey("test-api-key")
                    .modelName(TEST_MODEL_NAME)
                    .toolConfig(GeminiMode.ANY, "func1", "func2")
                    .build(mockGeminiService);

            var chatRequest = ChatRequest.builder()
                    .messages(new UserMessage("Call a function"))
                    .toolSpecifications(
                            ToolSpecification.builder().name("func1").build(),
                            ToolSpecification.builder().name("func2").build())
                    .build();

            // When
            subject.chat(chatRequest);

            // Then
            verify(mockGeminiService).generateContent(eq(TEST_MODEL_NAME), requestCaptor.capture());
            var request = requestCaptor.getValue();
            assertThat(request.toolConfig()).isNotNull();
            assertThat(request.toolConfig().functionCallingConfig().getMode()).isEqualTo(GeminiMode.ANY);
            assertThat(request.toolConfig().functionCallingConfig().getAllowedFunctionNames())
                    .containsExactly("func1", "func2");
        }

        @Test
        void shouldSendNoneModeInRequest() {
            // Given
            var expectedResponse = createGeminiResponse("Response");
            when(mockGeminiService.generateContent(eq(TEST_MODEL_NAME), any(GeminiGenerateContentRequest.class)))
                    .thenReturn(expectedResponse);

            var subject = GoogleAiGeminiChatModel.builder()
                    .apiKey("test-api-key")
                    .modelName(TEST_MODEL_NAME)
                    .toolConfig(GeminiMode.NONE)
                    .build(mockGeminiService);

            var chatRequest = ChatRequest.builder()
                    .messages(new UserMessage("Call a function"))
                    .toolSpecifications(
                            ToolSpecification.builder().name("myTool").build())
                    .build();

            // When
            subject.chat(chatRequest);

            // Then
            verify(mockGeminiService).generateContent(eq(TEST_MODEL_NAME), requestCaptor.capture());
            var request = requestCaptor.getValue();
            assertThat(request.toolConfig()).isNotNull();
            assertThat(request.toolConfig().functionCallingConfig().getMode()).isEqualTo(GeminiMode.NONE);
        }

        @Test
        void shouldEnableServerSideToolInvocationsAutomaticallyForMixedTools() {
            var expectedResponse = createGeminiResponse("Response");
            when(mockGeminiService.generateContent(eq(TEST_MODEL_NAME), any(GeminiGenerateContentRequest.class)))
                    .thenReturn(expectedResponse);

            var subject = GoogleAiGeminiChatModel.builder()
                    .apiKey("test-api-key")
                    .modelName(TEST_MODEL_NAME)
                    .serverTools(GoogleAiGeminiServerTool.builder()
                            .type("google_search")
                            .build())
                    .build(mockGeminiService);

            var chatRequest = ChatRequest.builder()
                    .messages(new UserMessage("Call a function after searching"))
                    .toolSpecifications(
                            ToolSpecification.builder().name("myTool").build())
                    .build();

            subject.chat(chatRequest);

            verify(mockGeminiService).generateContent(eq(TEST_MODEL_NAME), requestCaptor.capture());
            var request = requestCaptor.getValue();
            assertThat(request.toolConfig()).isNotNull();
            assertThat(request.toolConfig().includeServerSideToolInvocations()).isTrue();
            assertThat(request.toolConfig().functionCallingConfig()).isNotNull();
            assertThat(request.toolConfig().functionCallingConfig().getMode()).isEqualTo(GeminiMode.VALIDATED);
        }

        @Test
        void shouldAllowExplicitServerSideToolInvocationsForBuiltInToolsOnly() {
            var expectedResponse = createGeminiResponse("Response");
            when(mockGeminiService.generateContent(eq(TEST_MODEL_NAME), any(GeminiGenerateContentRequest.class)))
                    .thenReturn(expectedResponse);

            var subject = GoogleAiGeminiChatModel.builder()
                    .apiKey("test-api-key")
                    .modelName(TEST_MODEL_NAME)
                    .serverTools(GoogleAiGeminiServerTool.builder()
                            .type("google_search")
                            .build())
                    .includeServerSideToolInvocations(true)
                    .build(mockGeminiService);

            var chatRequest =
                    ChatRequest.builder().messages(new UserMessage("Search")).build();

            subject.chat(chatRequest);

            verify(mockGeminiService).generateContent(eq(TEST_MODEL_NAME), requestCaptor.capture());
            var request = requestCaptor.getValue();
            assertThat(request.toolConfig()).isNotNull();
            assertThat(request.toolConfig().includeServerSideToolInvocations()).isTrue();
            assertThat(request.toolConfig().functionCallingConfig()).isNull();
        }

        @Test
        void shouldIgnoreFunctionCallingConfigWhenOnlyBuiltInToolsAreConfigured() {
            var expectedResponse = createGeminiResponse("Response");
            when(mockGeminiService.generateContent(eq(TEST_MODEL_NAME), any(GeminiGenerateContentRequest.class)))
                    .thenReturn(expectedResponse);

            var subject = GoogleAiGeminiChatModel.builder()
                    .apiKey("test-api-key")
                    .modelName(TEST_MODEL_NAME)
                    .serverTools(GoogleAiGeminiServerTool.builder()
                            .type("google_search")
                            .build())
                    .includeServerSideToolInvocations(true)
                    .toolConfig(GeminiMode.AUTO)
                    .build(mockGeminiService);

            subject.chat(
                    ChatRequest.builder().messages(new UserMessage("Search")).build());

            verify(mockGeminiService).generateContent(eq(TEST_MODEL_NAME), requestCaptor.capture());
            var request = requestCaptor.getValue();
            assertThat(request.toolConfig()).isNotNull();
            assertThat(request.toolConfig().includeServerSideToolInvocations()).isTrue();
            assertThat(request.toolConfig().functionCallingConfig()).isNull();
        }

        @Test
        void shouldNormalizeAutoModeToValidatedWhenIncludingServerSideToolInvocationsForMixedTools() {
            var expectedResponse = createGeminiResponse("Response");
            when(mockGeminiService.generateContent(eq(TEST_MODEL_NAME), any(GeminiGenerateContentRequest.class)))
                    .thenReturn(expectedResponse);

            var subject = GoogleAiGeminiChatModel.builder()
                    .apiKey("test-api-key")
                    .modelName(TEST_MODEL_NAME)
                    .serverTools(GoogleAiGeminiServerTool.builder()
                            .type("google_search")
                            .build())
                    .includeServerSideToolInvocations(true)
                    .toolConfig(GeminiMode.AUTO)
                    .build(mockGeminiService);

            subject.chat(ChatRequest.builder()
                    .messages(new UserMessage("Search and call a function"))
                    .toolSpecifications(
                            ToolSpecification.builder().name("myTool").build())
                    .build());

            verify(mockGeminiService).generateContent(eq(TEST_MODEL_NAME), requestCaptor.capture());
            var request = requestCaptor.getValue();
            assertThat(request.toolConfig()).isNotNull();
            assertThat(request.toolConfig().includeServerSideToolInvocations()).isTrue();
            assertThat(request.toolConfig().functionCallingConfig()).isNotNull();
            assertThat(request.toolConfig().functionCallingConfig().getMode()).isEqualTo(GeminiMode.VALIDATED);
        }
    }

    @Nested
    class ToolCirculationTest {
        @Captor
        ArgumentCaptor<GeminiGenerateContentRequest> requestCaptor;

        @Test
        void shouldReplayRawToolCirculationPartsAndFunctionResponseIdOnFollowUpTurn() {
            ToolSpecification toolSpecification =
                    ToolSpecification.builder().name("getWeather").build();

            GeminiContent turnOneContent = new GeminiContent(
                    List.of(
                            GeminiContent.GeminiPart.builder()
                                    .thoughtSignature("sig-tool")
                                    .toolCall(new GeminiContent.GeminiPart.GeminiToolCall(
                                            "GOOGLE_SEARCH_WEB",
                                            Map.of("queries", List.of("northernmost city in the United States")),
                                            "search-1"))
                                    .build(),
                            GeminiContent.GeminiPart.builder()
                                    .thoughtSignature("sig-response")
                                    .toolResponse(new GeminiContent.GeminiPart.GeminiToolResponse(
                                            "GOOGLE_SEARCH_WEB",
                                            Map.of("search_suggestions", "Utqiagvik weather"),
                                            "search-1"))
                                    .build(),
                            GeminiContent.GeminiPart.builder()
                                    .thoughtSignature("sig-function")
                                    .functionCall(new GeminiContent.GeminiPart.GeminiFunctionCall(
                                            "getWeather", Map.of("location", "Utqiagvik, Alaska"), "function-1"))
                                    .build()),
                    "model");

            GeminiGenerateContentResponse firstResponse = new GeminiGenerateContentResponse(
                    "response-1",
                    "gemini-3-flash-preview",
                    List.of(new GeminiCandidate(turnOneContent, GeminiFinishReason.STOP, null, null)),
                    createUsageMetadata(10, 5, 15),
                    null);
            GeminiGenerateContentResponse secondResponse = createGeminiResponse("It is cold.");

            when(mockGeminiService.generateContent(eq(TEST_MODEL_NAME), any(GeminiGenerateContentRequest.class)))
                    .thenReturn(firstResponse, secondResponse);

            GoogleAiGeminiChatModel subject = GoogleAiGeminiChatModel.builder()
                    .apiKey("test-api-key")
                    .modelName(TEST_MODEL_NAME)
                    .serverTools(GoogleAiGeminiServerTool.builder()
                            .type("google_search")
                            .build())
                    .defaultRequestParameters(ChatRequestParameters.builder()
                            .toolSpecifications(toolSpecification)
                            .build())
                    .build(mockGeminiService);

            UserMessage userMessage = UserMessage.from("Search, then check the weather.");

            ChatResponse firstChatResponse = subject.chat(userMessage);
            ToolExecutionResultMessage toolExecutionResultMessage = ToolExecutionResultMessage.from(
                    firstChatResponse.aiMessage().toolExecutionRequests().get(0), "Very cold. 22 degrees Fahrenheit.");

            subject.chat(userMessage, firstChatResponse.aiMessage(), toolExecutionResultMessage);

            verify(mockGeminiService, times(2)).generateContent(eq(TEST_MODEL_NAME), requestCaptor.capture());
            List<GeminiGenerateContentRequest> requests = requestCaptor.getAllValues();

            GeminiGenerateContentRequest secondRequest = requests.get(1);
            assertThat(secondRequest.toolConfig()).isNotNull();
            assertThat(secondRequest.toolConfig().includeServerSideToolInvocations())
                    .isTrue();
            assertThat(secondRequest.contents()).hasSize(3);

            GeminiContent replayedModelContent = secondRequest.contents().get(1);
            assertThat(replayedModelContent.role()).isEqualTo("model");
            assertThat(replayedModelContent.parts()).hasSize(3);
            assertThat(replayedModelContent.parts().get(0).toolCall())
                    .isEqualTo(turnOneContent.parts().get(0).toolCall());
            assertThat(replayedModelContent.parts().get(1).toolResponse())
                    .isEqualTo(turnOneContent.parts().get(1).toolResponse());
            assertThat(replayedModelContent.parts().get(2).functionCall())
                    .isEqualTo(turnOneContent.parts().get(2).functionCall());

            GeminiContent functionResultContent = secondRequest.contents().get(2);
            assertThat(functionResultContent.role()).isEqualTo("user");
            assertThat(functionResultContent.parts())
                    .singleElement()
                    .satisfies(part -> assertThat(part.functionResponse().id()).isEqualTo("function-1"));
        }

        @Test
        void shouldPreservePlainFunctionCallIdOnFollowUpTurn() {
            ToolSpecification toolSpecification =
                    ToolSpecification.builder().name("getWeather").build();

            GeminiContent turnOneContent = new GeminiContent(
                    List.of(GeminiContent.GeminiPart.builder()
                            .functionCall(new GeminiContent.GeminiPart.GeminiFunctionCall(
                                    "getWeather", Map.of("location", "Paris"), "function-plain-1"))
                            .build()),
                    "model");

            GeminiGenerateContentResponse firstResponse = new GeminiGenerateContentResponse(
                    "response-1",
                    "gemini-2.5-flash",
                    List.of(new GeminiCandidate(turnOneContent, GeminiFinishReason.STOP, null, null)),
                    createUsageMetadata(10, 5, 15),
                    null);
            GeminiGenerateContentResponse secondResponse = createGeminiResponse("It is mild.");

            when(mockGeminiService.generateContent(eq(TEST_MODEL_NAME), any(GeminiGenerateContentRequest.class)))
                    .thenReturn(firstResponse, secondResponse);

            GoogleAiGeminiChatModel subject = GoogleAiGeminiChatModel.builder()
                    .apiKey("test-api-key")
                    .modelName(TEST_MODEL_NAME)
                    .defaultRequestParameters(ChatRequestParameters.builder()
                            .toolSpecifications(toolSpecification)
                            .build())
                    .build(mockGeminiService);

            UserMessage userMessage = UserMessage.from("Check the weather.");

            ChatResponse firstChatResponse = subject.chat(userMessage);
            assertThat(firstChatResponse.aiMessage().toolExecutionRequests())
                    .singleElement()
                    .satisfies(toolRequest -> assertThat(toolRequest.id()).isEqualTo("function-plain-1"));
            ToolExecutionResultMessage toolExecutionResultMessage = ToolExecutionResultMessage.from(
                    firstChatResponse.aiMessage().toolExecutionRequests().get(0), "Mild. 18 degrees Celsius.");

            subject.chat(userMessage, firstChatResponse.aiMessage(), toolExecutionResultMessage);

            verify(mockGeminiService, times(2)).generateContent(eq(TEST_MODEL_NAME), requestCaptor.capture());
            List<GeminiGenerateContentRequest> requests = requestCaptor.getAllValues();

            GeminiContent functionResultContent = requests.get(1).contents().get(2);
            assertThat(functionResultContent.role()).isEqualTo("user");
            assertThat(functionResultContent.parts())
                    .singleElement()
                    .satisfies(part -> assertThat(part.functionResponse().id()).isEqualTo("function-plain-1"));
        }

        @Test
        void shouldReplayFunctionOnlyPartsWithDistinctThoughtSignaturesOnFollowUpTurn() {
            ToolSpecification toolSpecification =
                    ToolSpecification.builder().name("getWeather").build();

            GeminiContent turnOneContent = new GeminiContent(
                    List.of(
                            GeminiContent.GeminiPart.builder()
                                    .thoughtSignature("sig-function-1")
                                    .functionCall(new GeminiContent.GeminiPart.GeminiFunctionCall(
                                            "getWeather", Map.of("location", "Paris"), "function-1"))
                                    .build(),
                            GeminiContent.GeminiPart.builder()
                                    .thoughtSignature("sig-function-2")
                                    .functionCall(new GeminiContent.GeminiPart.GeminiFunctionCall(
                                            "getWeather", Map.of("location", "Berlin"), "function-2"))
                                    .build()),
                    "model");

            GeminiGenerateContentResponse firstResponse = new GeminiGenerateContentResponse(
                    "response-1",
                    "gemini-3-flash-preview",
                    List.of(new GeminiCandidate(turnOneContent, GeminiFinishReason.STOP, null, null)),
                    createUsageMetadata(10, 5, 15),
                    null);
            GeminiGenerateContentResponse secondResponse = createGeminiResponse("Both are mild.");

            when(mockGeminiService.generateContent(eq(TEST_MODEL_NAME), any(GeminiGenerateContentRequest.class)))
                    .thenReturn(firstResponse, secondResponse);

            GoogleAiGeminiChatModel subject = GoogleAiGeminiChatModel.builder()
                    .apiKey("test-api-key")
                    .modelName(TEST_MODEL_NAME)
                    .serverTools(GoogleAiGeminiServerTool.builder()
                            .type("google_search")
                            .build())
                    .defaultRequestParameters(ChatRequestParameters.builder()
                            .toolSpecifications(toolSpecification)
                            .build())
                    .build(mockGeminiService);

            UserMessage userMessage = UserMessage.from("Search, then check both cities.");

            ChatResponse firstChatResponse = subject.chat(userMessage);
            ToolExecutionResultMessage firstToolResult = ToolExecutionResultMessage.from(
                    firstChatResponse.aiMessage().toolExecutionRequests().get(0), "Paris is mild.");
            ToolExecutionResultMessage secondToolResult = ToolExecutionResultMessage.from(
                    firstChatResponse.aiMessage().toolExecutionRequests().get(1), "Berlin is cool.");

            subject.chat(userMessage, firstChatResponse.aiMessage(), firstToolResult, secondToolResult);

            verify(mockGeminiService, times(2)).generateContent(eq(TEST_MODEL_NAME), requestCaptor.capture());
            GeminiGenerateContentRequest secondRequest =
                    requestCaptor.getAllValues().get(1);

            assertThat(secondRequest.contents()).hasSize(4);
            assertThat(secondRequest.contents().get(1).parts()).isEqualTo(turnOneContent.parts());
        }
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
