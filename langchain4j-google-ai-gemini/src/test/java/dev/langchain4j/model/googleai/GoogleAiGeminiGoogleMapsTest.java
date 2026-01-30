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
import dev.langchain4j.model.googleai.GroundingMetadata.GroundingChunk;
import dev.langchain4j.model.googleai.GroundingMetadata.GroundingChunk.Maps;
import dev.langchain4j.model.googleai.GroundingMetadata.GroundingChunk.Web;
import dev.langchain4j.model.googleai.GroundingMetadata.GroundingSupport;
import dev.langchain4j.model.googleai.GroundingMetadata.RetrievalMetadata;
import dev.langchain4j.model.googleai.GroundingMetadata.SearchEntryPoint;
import dev.langchain4j.model.googleai.GroundingMetadata.Segment;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GoogleAiGeminiGoogleMapsTest {

    private static final String TEST_MODEL_NAME = "gemini-1.5-pro";

    @Mock
    GeminiService mockGeminiService;

    @Nested
    class BuilderTest {
        @Test
        void shouldDefaultAllowGoogleMapsToFalse() {
            var model = GoogleAiGeminiChatModel.builder()
                    .apiKey("test-key")
                    .modelName(TEST_MODEL_NAME)
                    .build();

            assertThat(model.allowGoogleMaps).isFalse();
            assertThat(model.retrieveGoogleMapsWidgetToken).isFalse();
        }

        @Test
        void shouldSetAllowGoogleMapsAndRetrieveWidgetToken() {
            var model = GoogleAiGeminiChatModel.builder()
                    .apiKey("test-key")
                    .modelName(TEST_MODEL_NAME)
                    .allowGoogleMaps(true)
                    .retrieveGoogleMapsWidgetToken(true)
                    .build();

            assertThat(model.allowGoogleMaps).isTrue();
            assertThat(model.retrieveGoogleMapsWidgetToken).isTrue();
        }
    }

    @Nested
    class RequestTest {
        @Captor
        ArgumentCaptor<GeminiGenerateContentRequest> requestCaptor;

        @Test
        void shouldNotIncludeGoogleMapsToolWhenDisabled() {
            // Given
            var model = GoogleAiGeminiChatModel.builder()
                    .apiKey("test-key")
                    .modelName(TEST_MODEL_NAME)
                    .allowGoogleMaps(false)
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
        void shouldIncludeGoogleMapsToolWhenEnabled() {
            // Given
            var model = GoogleAiGeminiChatModel.builder()
                    .apiKey("test-key")
                    .modelName(TEST_MODEL_NAME)
                    .allowGoogleMaps(true)
                    .retrieveGoogleMapsWidgetToken(false)
                    .build(mockGeminiService);

            var chatRequest = ChatRequest.builder()
                    .messages(UserMessage.from("Find coffee shops"))
                    .build();

            when(mockGeminiService.generateContent(any(), any())).thenReturn(createSimpleResponse("Here is result"));

            // When
            model.chat(chatRequest);

            // Then
            verify(mockGeminiService).generateContent(eq(TEST_MODEL_NAME), requestCaptor.capture());
            var request = requestCaptor.getValue();
            assertThat(request.tools()).isNotNull();

            GeminiTool tool = request.tools();
            assertThat(tool.googleMaps()).isNotNull();
            assertThat(tool.googleMaps().enableWidget()).isFalse();
        }

        @Test
        void shouldIncludeGoogleMapsToolWithWidgetTokenWhenEnabled() {
            // Given
            var model = GoogleAiGeminiChatModel.builder()
                    .apiKey("test-key")
                    .modelName(TEST_MODEL_NAME)
                    .allowGoogleMaps(true)
                    .retrieveGoogleMapsWidgetToken(true)
                    .build(mockGeminiService);

            var chatRequest = ChatRequest.builder()
                    .messages(UserMessage.from("Map of coffee shops"))
                    .build();

            when(mockGeminiService.generateContent(any(), any())).thenReturn(createSimpleResponse("Here is result"));

            // When
            model.chat(chatRequest);

            // Then
            verify(mockGeminiService).generateContent(eq(TEST_MODEL_NAME), requestCaptor.capture());
            var request = requestCaptor.getValue();
            assertThat(request.tools()).isNotNull();

            GeminiTool tool = request.tools();
            assertThat(tool.googleMaps()).isNotNull();
            assertThat(tool.googleMaps().enableWidget()).isTrue();
        }
    }

    @Nested
    class ResponseTest {
        @Test
        void shouldHandleResponseWithGroundingMetadata() {
            // Given
            var model = GoogleAiGeminiChatModel.builder()
                    .apiKey("test-key")
                    .modelName(TEST_MODEL_NAME)
                    .allowGoogleMaps(true)
                    .build(mockGeminiService);

            // Create GroundingMetadata with all fields populated
            var maps = new Maps(
                    "https://maps.google.com/...",
                    "Coffee Shop",
                    "A great place for coffee",
                    "PLACE_ID_123",
                    new Maps.PlaceAnswerSources(List.of(new Maps.ReviewSnippet(
                            "REVIEW_ID_1", "https://maps.google.com/reviews/1", "Great coffee"))));

            var groundingChunk = new GroundingChunk(
                    new Web("https://example.com", "Example Site"),
                    new GroundingChunk.RetrievedContext("https://context.com", "Context Title", "Context Text"),
                    maps);

            var segment = new Segment(0, 0, 10, "Coffee Shop");
            var groundingSupport = new GroundingSupport(List.of(0), List.of(0.95), segment);

            var searchEntryPoint = new SearchEntryPoint("Rendered Content", "SDK Blob");
            var retrievalMetadata = new RetrievalMetadata(0.85);

            var groundingMetadata = GroundingMetadata.builder()
                    .groundingChunks(List.of(groundingChunk))
                    .groundingSupports(List.of(groundingSupport))
                    .webSearchQueries(List.of("best coffee shops"))
                    .searchEntryPoint(searchEntryPoint)
                    .retrievalMetadata(retrievalMetadata)
                    .googleMapsWidgetContextToken("WIDGET_TOKEN_XYZ")
                    .build();

            var candidate = new GeminiCandidate(
                    new GeminiContent(
                            List.of(GeminiContent.GeminiPart.builder()
                                    .text("Found a nice coffee shop.")
                                    .build()),
                            "model"),
                    GeminiFinishReason.STOP,
                    null);

            var usageMetadata = new GeminiGenerateContentResponse.GeminiUsageMetadata(10, 10, 20);
            var response = new GeminiGenerateContentResponse(
                    "id", "model", List.of(candidate), usageMetadata, groundingMetadata);

            when(mockGeminiService.generateContent(any(), any())).thenReturn(response);

            // When
            var chatRequest = ChatRequest.builder()
                    .messages(UserMessage.from("Find me a coffee shop"))
                    .build();
            var chatResponse = model.chat(chatRequest);

            // Then
            assertThat(chatResponse).isNotNull();
            assertThat(chatResponse.aiMessage().text()).isEqualTo("Found a nice coffee shop.");

            assertThat(chatResponse.metadata()).isInstanceOf(GoogleAiGeminiChatResponseMetadata.class);
            GoogleAiGeminiChatResponseMetadata metadata = (GoogleAiGeminiChatResponseMetadata) chatResponse.metadata();
            assertThat(metadata.groundingMetadata()).isNotNull();
            assertThat(metadata.groundingMetadata().groundingChunks()).hasSize(1);
            assertThat(metadata.groundingMetadata()
                            .groundingChunks()
                            .get(0)
                            .maps()
                            .placeId())
                    .isEqualTo("PLACE_ID_123");
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
