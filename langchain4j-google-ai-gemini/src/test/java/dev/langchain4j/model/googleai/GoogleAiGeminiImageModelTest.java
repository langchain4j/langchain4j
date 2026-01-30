package dev.langchain4j.model.googleai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.googleai.GeminiContent.GeminiPart;
import dev.langchain4j.model.googleai.GeminiContent.GeminiPart.GeminiBlob;
import dev.langchain4j.model.googleai.GeminiGenerateContentResponse.GeminiCandidate;
import dev.langchain4j.model.googleai.GeminiGenerateContentResponse.GeminiCandidate.GeminiFinishReason;
import dev.langchain4j.model.googleai.GoogleAiGeminiImageModel.GeminiImageGenerationException;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GoogleAiGeminiImageModelTest {

    private static final String TEST_MODEL_NAME = "gemini-2.5-flash-image";
    private static final String TEST_IMAGE_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";
    private static final String TEST_MIME_TYPE = "image/png";

    @Mock
    GeminiService mockGeminiService;

    @Nested
    class GenerateTest {

        @Test
        void shouldGenerateImageFromPrompt() {
            // Given
            var expectedResponse = createImageResponse(TEST_IMAGE_BASE64, TEST_MIME_TYPE);
            when(mockGeminiService.generateContent(eq(TEST_MODEL_NAME), any(GeminiGenerateContentRequest.class)))
                    .thenReturn(expectedResponse);

            var subject = GoogleAiGeminiImageModel.builder()
                    .apiKey("test-api-key")
                    .modelName(TEST_MODEL_NAME)
                    .build();

            // Inject mock service via reflection or package-private method
            setGeminiService(subject, mockGeminiService);

            // When
            var response = subject.generate("A serene mountain landscape at sunset");

            // Then
            assertThat(response.content()).isNotNull();
            assertThat(response.content().base64Data()).isEqualTo(TEST_IMAGE_BASE64);
            assertThat(response.content().mimeType()).isEqualTo(TEST_MIME_TYPE);
            verify(mockGeminiService).generateContent(eq(TEST_MODEL_NAME), any(GeminiGenerateContentRequest.class));
        }

        @Test
        void shouldThrowExceptionWhenNoCandidatesInResponse() {
            // Given
            var emptyResponse =
                    new GeminiGenerateContentResponse("response-id", "gemini-pro-v1", List.of(), null, null);
            when(mockGeminiService.generateContent(eq(TEST_MODEL_NAME), any(GeminiGenerateContentRequest.class)))
                    .thenReturn(emptyResponse);

            var subject = GoogleAiGeminiImageModel.builder()
                    .apiKey("test-api-key")
                    .modelName(TEST_MODEL_NAME)
                    .build();

            setGeminiService(subject, mockGeminiService);

            // When & Then
            assertThatThrownBy(() -> subject.generate("A simple red circle"))
                    .isInstanceOf(GeminiImageGenerationException.class)
                    .hasMessage("No image generated in response");
        }

        @Test
        void shouldThrowExceptionWhenNoContentInCandidate() {
            // Given
            var candidate = new GeminiCandidate(null, GeminiFinishReason.STOP, null);
            var responseWithNullContent =
                    new GeminiGenerateContentResponse("response-id", "gemini-pro-v1", List.of(candidate), null, null);
            when(mockGeminiService.generateContent(eq(TEST_MODEL_NAME), any(GeminiGenerateContentRequest.class)))
                    .thenReturn(responseWithNullContent);

            var subject = GoogleAiGeminiImageModel.builder()
                    .apiKey("test-api-key")
                    .modelName(TEST_MODEL_NAME)
                    .build();

            setGeminiService(subject, mockGeminiService);

            // When & Then
            assertThatThrownBy(() -> subject.generate("A simple blue square"))
                    .isInstanceOf(GeminiImageGenerationException.class)
                    .hasMessage("No content in response candidate");
        }

        @Test
        void shouldThrowExceptionWhenNoImageDataInParts() {
            // Given
            var textOnlyCandidate = new GeminiCandidate(
                    new GeminiContent(
                            List.of(GeminiPart.builder()
                                    .text("Just text, no image")
                                    .build()),
                            "model"),
                    GeminiFinishReason.STOP,
                    null);
            var textOnlyResponse = new GeminiGenerateContentResponse(
                    "response-id", "gemini-pro-v1", List.of(textOnlyCandidate), null, null);
            when(mockGeminiService.generateContent(eq(TEST_MODEL_NAME), any(GeminiGenerateContentRequest.class)))
                    .thenReturn(textOnlyResponse);

            var subject = GoogleAiGeminiImageModel.builder()
                    .apiKey("test-api-key")
                    .modelName(TEST_MODEL_NAME)
                    .build();

            setGeminiService(subject, mockGeminiService);

            // When & Then
            assertThatThrownBy(() -> subject.generate("A green triangle"))
                    .isInstanceOf(GeminiImageGenerationException.class)
                    .hasMessage("No image data found in response");
        }
    }

    @Nested
    class EditTest {

        @Test
        void shouldEditImageWithPrompt() {
            // Given
            var expectedResponse = createImageResponse("editedImageData", TEST_MIME_TYPE);
            when(mockGeminiService.generateContent(eq(TEST_MODEL_NAME), any(GeminiGenerateContentRequest.class)))
                    .thenReturn(expectedResponse);

            var subject = GoogleAiGeminiImageModel.builder()
                    .apiKey("test-api-key")
                    .modelName(TEST_MODEL_NAME)
                    .build();

            setGeminiService(subject, mockGeminiService);

            var originalImage = Image.builder()
                    .base64Data(TEST_IMAGE_BASE64)
                    .mimeType(TEST_MIME_TYPE)
                    .build();

            // When
            var response = subject.edit(originalImage, "Add a hot air balloon to the sky");

            // Then
            assertThat(response.content()).isNotNull();
            assertThat(response.content().base64Data()).isEqualTo("editedImageData");
            verify(mockGeminiService).generateContent(eq(TEST_MODEL_NAME), any(GeminiGenerateContentRequest.class));
        }

        @Test
        void shouldEditImageWithMask() {
            // Given
            var expectedResponse = createImageResponse("maskedEditData", TEST_MIME_TYPE);
            when(mockGeminiService.generateContent(eq(TEST_MODEL_NAME), any(GeminiGenerateContentRequest.class)))
                    .thenReturn(expectedResponse);

            var subject = GoogleAiGeminiImageModel.builder()
                    .apiKey("test-api-key")
                    .modelName(TEST_MODEL_NAME)
                    .build();

            setGeminiService(subject, mockGeminiService);

            var originalImage = Image.builder()
                    .base64Data(TEST_IMAGE_BASE64)
                    .mimeType(TEST_MIME_TYPE)
                    .build();

            var maskImage = Image.builder()
                    .base64Data("maskBase64Data")
                    .mimeType(TEST_MIME_TYPE)
                    .build();

            // When
            var response = subject.edit(originalImage, maskImage, "Change the background to blue");

            // Then
            assertThat(response.content()).isNotNull();
            assertThat(response.content().base64Data()).isEqualTo("maskedEditData");
            verify(mockGeminiService).generateContent(eq(TEST_MODEL_NAME), any(GeminiGenerateContentRequest.class));
        }

        @Test
        void shouldThrowExceptionWhenImageIsNull() {
            // Given
            var subject = GoogleAiGeminiImageModel.builder()
                    .apiKey("test-api-key")
                    .modelName(TEST_MODEL_NAME)
                    .build();

            // When & Then
            assertThatThrownBy(() -> subject.edit(null, "Add something"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("image");
        }

        @Test
        void shouldThrowExceptionWhenPromptIsBlank() {
            // Given
            var subject = GoogleAiGeminiImageModel.builder()
                    .apiKey("test-api-key")
                    .modelName(TEST_MODEL_NAME)
                    .build();

            var image = Image.builder()
                    .base64Data(TEST_IMAGE_BASE64)
                    .mimeType(TEST_MIME_TYPE)
                    .build();

            // When & Then
            assertThatThrownBy(() -> subject.edit(image, ""))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("prompt");
        }

        @Test
        void shouldThrowExceptionWhenMaskIsNull() {
            // Given
            var subject = GoogleAiGeminiImageModel.builder()
                    .apiKey("test-api-key")
                    .modelName(TEST_MODEL_NAME)
                    .build();

            var image = Image.builder()
                    .base64Data(TEST_IMAGE_BASE64)
                    .mimeType(TEST_MIME_TYPE)
                    .build();

            // When & Then
            assertThatThrownBy(() -> subject.edit(image, null, "Edit something"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("mask");
        }

        @Test
        void shouldHandleImageWithUrlFallback() {
            // Given
            var expectedResponse = createImageResponse("urlEditedData", TEST_MIME_TYPE);
            when(mockGeminiService.generateContent(eq(TEST_MODEL_NAME), any(GeminiGenerateContentRequest.class)))
                    .thenReturn(expectedResponse);

            var subject = GoogleAiGeminiImageModel.builder()
                    .apiKey("test-api-key")
                    .modelName(TEST_MODEL_NAME)
                    .build();

            setGeminiService(subject, mockGeminiService);

            var imageWithUrl = Image.builder()
                    .url(URI.create("https://example.com/image.png"))
                    .build();

            // When
            var response = subject.edit(imageWithUrl, "Modify this image");

            // Then
            assertThat(response.content()).isNotNull();
            assertThat(response.content().base64Data()).isEqualTo("urlEditedData");
        }

        @Test
        void shouldDefaultMimeTypeToPngWhenNotProvided() {
            // Given
            var expectedResponse = createImageResponse("defaultMimeData", TEST_MIME_TYPE);
            when(mockGeminiService.generateContent(eq(TEST_MODEL_NAME), any(GeminiGenerateContentRequest.class)))
                    .thenReturn(expectedResponse);

            var subject = GoogleAiGeminiImageModel.builder()
                    .apiKey("test-api-key")
                    .modelName(TEST_MODEL_NAME)
                    .build();

            setGeminiService(subject, mockGeminiService);

            var imageWithoutMimeType =
                    Image.builder().base64Data(TEST_IMAGE_BASE64).build();

            // When
            var response = subject.edit(imageWithoutMimeType, "Edit without mime type");

            // Then
            assertThat(response.content()).isNotNull();
            verify(mockGeminiService).generateContent(eq(TEST_MODEL_NAME), any(GeminiGenerateContentRequest.class));
        }
    }

    @Nested
    class RequestVerificationTest {

        @Captor
        ArgumentCaptor<GeminiGenerateContentRequest> requestCaptor;

        @Test
        void shouldSendRequestWithImageConfig() {
            // Given
            var expectedResponse = createImageResponse(TEST_IMAGE_BASE64, TEST_MIME_TYPE);
            when(mockGeminiService.generateContent(eq(TEST_MODEL_NAME), any(GeminiGenerateContentRequest.class)))
                    .thenReturn(expectedResponse);

            var subject = GoogleAiGeminiImageModel.builder()
                    .apiKey("test-api-key")
                    .modelName(TEST_MODEL_NAME)
                    .aspectRatio("16:9")
                    .imageSize("2K")
                    .build();

            setGeminiService(subject, mockGeminiService);

            // When
            subject.generate("A landscape image");

            // Then
            verify(mockGeminiService).generateContent(eq(TEST_MODEL_NAME), requestCaptor.capture());

            var request = requestCaptor.getValue();
            assertThat(request.contents()).hasSize(1);
            assertThat(request.generationConfig()).isNotNull();
            assertThat(request.generationConfig().responseModalities()).containsExactly(GeminiResponseModality.IMAGE);
            assertThat(request.generationConfig().imageConfig()).isNotNull();
            assertThat(request.generationConfig().imageConfig().aspectRatio()).isEqualTo("16:9");
            assertThat(request.generationConfig().imageConfig().imageSize()).isEqualTo("2K");
        }

        @Test
        void shouldSendRequestWithoutImageConfigWhenNotSpecified() {
            // Given
            var expectedResponse = createImageResponse(TEST_IMAGE_BASE64, TEST_MIME_TYPE);
            when(mockGeminiService.generateContent(eq(TEST_MODEL_NAME), any(GeminiGenerateContentRequest.class)))
                    .thenReturn(expectedResponse);

            var subject = GoogleAiGeminiImageModel.builder()
                    .apiKey("test-api-key")
                    .modelName(TEST_MODEL_NAME)
                    .build();

            setGeminiService(subject, mockGeminiService);

            // When
            subject.generate("A simple image");

            // Then
            verify(mockGeminiService).generateContent(eq(TEST_MODEL_NAME), requestCaptor.capture());

            var request = requestCaptor.getValue();
            assertThat(request.generationConfig().imageConfig()).isNull();
        }

        @Test
        void shouldIncludeImagePartsInEditRequest() {
            // Given
            var expectedResponse = createImageResponse(TEST_IMAGE_BASE64, TEST_MIME_TYPE);
            when(mockGeminiService.generateContent(eq(TEST_MODEL_NAME), any(GeminiGenerateContentRequest.class)))
                    .thenReturn(expectedResponse);

            var subject = GoogleAiGeminiImageModel.builder()
                    .apiKey("test-api-key")
                    .modelName(TEST_MODEL_NAME)
                    .build();

            setGeminiService(subject, mockGeminiService);

            var image = Image.builder()
                    .base64Data(TEST_IMAGE_BASE64)
                    .mimeType(TEST_MIME_TYPE)
                    .build();

            // When
            subject.edit(image, "Add a cat");

            // Then
            verify(mockGeminiService).generateContent(eq(TEST_MODEL_NAME), requestCaptor.capture());

            var request = requestCaptor.getValue();
            assertThat(request.contents()).hasSize(1);
            var content = request.contents().get(0);
            assertThat(content.parts()).hasSize(2); // text + image
            assertThat(content.parts().get(0).text()).isEqualTo("Add a cat");
            assertThat(content.parts().get(1).inlineData()).isNotNull();
            assertThat(content.parts().get(1).inlineData().data()).isEqualTo(TEST_IMAGE_BASE64);
        }

        @Test
        void shouldIncludeMaskInEditRequestWhenProvided() {
            // Given
            var expectedResponse = createImageResponse(TEST_IMAGE_BASE64, TEST_MIME_TYPE);
            when(mockGeminiService.generateContent(eq(TEST_MODEL_NAME), any(GeminiGenerateContentRequest.class)))
                    .thenReturn(expectedResponse);

            var subject = GoogleAiGeminiImageModel.builder()
                    .apiKey("test-api-key")
                    .modelName(TEST_MODEL_NAME)
                    .build();

            setGeminiService(subject, mockGeminiService);

            var image = Image.builder()
                    .base64Data(TEST_IMAGE_BASE64)
                    .mimeType(TEST_MIME_TYPE)
                    .build();

            var mask = Image.builder()
                    .base64Data("maskData")
                    .mimeType(TEST_MIME_TYPE)
                    .build();

            // When
            subject.edit(image, mask, "Fill the masked area");

            // Then
            verify(mockGeminiService).generateContent(eq(TEST_MODEL_NAME), requestCaptor.capture());

            var request = requestCaptor.getValue();
            var content = request.contents().get(0);
            assertThat(content.parts()).hasSize(3); // text + image + mask
            assertThat(content.parts().get(0).text()).isEqualTo("Fill the masked area");
            assertThat(content.parts().get(1).inlineData().data()).isEqualTo(TEST_IMAGE_BASE64);
            assertThat(content.parts().get(2).inlineData().data()).isEqualTo("maskData");
        }
    }

    @Nested
    class BuilderValidationTest {

        @Test
        void shouldThrowExceptionWhenModelNameIsNull() {
            // When & Then
            assertThatThrownBy(() -> GoogleAiGeminiImageModel.builder()
                            .apiKey("test-api-key")
                            .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("modelName");
        }

        @Test
        void shouldReturnCorrectModelName() {
            // Given
            var subject = GoogleAiGeminiImageModel.builder()
                    .apiKey("test-api-key")
                    .modelName(TEST_MODEL_NAME)
                    .build();

            // When & Then
            assertThat(subject.modelName()).isEqualTo(TEST_MODEL_NAME);
        }
    }

    private static GeminiGenerateContentResponse createImageResponse(String base64Data, String mimeType) {
        var candidate = new GeminiCandidate(
                new GeminiContent(
                        List.of(GeminiPart.builder()
                                .inlineData(new GeminiBlob(mimeType, base64Data))
                                .build()),
                        "model"),
                GeminiFinishReason.STOP,
                null);

        return new GeminiGenerateContentResponse("response-id-123", "gemini-pro", List.of(candidate), null, null);
    }

    /**
     * Helper method to inject mock GeminiService into the model.
     * This uses reflection since the constructor is private and service is final.
     */
    private void setGeminiService(GoogleAiGeminiImageModel model, GeminiService service) {
        try {
            var field = GoogleAiGeminiImageModel.class.getDeclaredField("geminiService");
            field.setAccessible(true);
            field.set(model, service);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject mock GeminiService", e);
        }
    }
}
