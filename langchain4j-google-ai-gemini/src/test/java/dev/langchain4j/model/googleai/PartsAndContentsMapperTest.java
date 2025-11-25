package dev.langchain4j.model.googleai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.audio.Audio;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.AudioContent;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.VideoContent;
import dev.langchain4j.data.pdf.PdfFile;
import dev.langchain4j.data.video.Video;
import dev.langchain4j.model.googleai.GeminiContent.GeminiPart.GeminiBlob;
import java.util.List;
import org.junit.jupiter.api.Test;

class PartsAndContentsMapperTest {

    @Test
    void fromGPartsToAiMessage_handlesNullParts() {

        // Given
        List<GeminiContent.GeminiPart> parts = null;

        // When
        AiMessage result = PartsAndContentsMapper.fromGPartsToAiMessage(parts, false, null);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.text()).isNull();
        assertThat(result.thinking()).isNull();
        assertThat(result.toolExecutionRequests()).isEmpty();
        assertThat(result.attributes()).isEmpty();
    }

    @Test
    void fromGPartsToAiMessage_handlesEmptyPartsList() {
        // Given
        List<GeminiContent.GeminiPart> parts = List.of();

        // When
        AiMessage result = PartsAndContentsMapper.fromGPartsToAiMessage(parts, false, null);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.text()).isNull();
        assertThat(result.thinking()).isNull();
        assertThat(result.toolExecutionRequests()).isEmpty();
        assertThat(result.attributes()).isEmpty();
    }

    @Test
    void fromGPartsToAiMessage_handlesPartWithAllFieldsNull() {
        // Given
        GeminiContent.GeminiPart part = GeminiContent.GeminiPart.builder().build();
        List<GeminiContent.GeminiPart> parts = List.of(part);

        // When
        AiMessage result = PartsAndContentsMapper.fromGPartsToAiMessage(parts, false, null);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.text()).isNull();
        assertThat(result.thinking()).isNull();
        assertThat(result.toolExecutionRequests()).isEmpty();
        assertThat(result.attributes()).isEmpty();
    }

    @Test
    void fromGPartsToAiMessage_handlesPartWithEmptyText() {
        // Given
        GeminiContent.GeminiPart part =
                GeminiContent.GeminiPart.builder().text("").build();
        List<GeminiContent.GeminiPart> parts = List.of(part);

        // When
        AiMessage result = PartsAndContentsMapper.fromGPartsToAiMessage(parts, false, null);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.text()).isNull();
        assertThat(result.thinking()).isNull();
        assertThat(result.toolExecutionRequests()).isEmpty();
        assertThat(result.attributes()).isEmpty();
    }

    @Test
    void fromGPartsToAiMessage_handlesNonNullParts() {

        // Given
        GeminiContent.GeminiPart part =
                GeminiContent.GeminiPart.builder().text("Hello world").build();
        List<GeminiContent.GeminiPart> parts = List.of(part);

        // When
        AiMessage result = PartsAndContentsMapper.fromGPartsToAiMessage(parts, false, null);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.text()).isEqualTo("Hello world");
        assertThat(result.thinking()).isNull();
        assertThat(result.toolExecutionRequests()).isEmpty();
        assertThat(result.attributes()).isEmpty();
    }

    @Test
    void fromMessageToGContent_systemMessageWithText() {
        SystemMessage msg = new SystemMessage("system text");
        List<GeminiContent> result = PartsAndContentsMapper.fromMessageToGContent(List.of(msg), null, false);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).role()).isEqualTo("model");
        assertThat(result.get(0).parts().get(0).text()).isEqualTo("system text");
    }

    @Test
    void fromMessageToGContent_userMessageWithTextContent() {
        UserMessage msg = new UserMessage(List.of(new dev.langchain4j.data.message.TextContent("user text")));
        List<GeminiContent> result = PartsAndContentsMapper.fromMessageToGContent(List.of(msg), null, false);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).role()).isEqualTo("user");
        assertThat(result.get(0).parts().get(0).text()).isEqualTo("user text");
    }

    @Test
    void fromMessageToGContent_emptyMessageListReturnsEmpty() {
        List<GeminiContent> result = PartsAndContentsMapper.fromMessageToGContent(List.of(), null, false);
        assertThat(result).isEmpty();
    }

    @Test
    void fromGPartsToAiMessage_handlesGeneratedImages() {
        // Given
        GeminiBlob imageBlob = new GeminiBlob(
                "image/png",
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8/5+hHgAHggJ/PchI7wAAAABJRU5ErkJggg==");

        GeminiContent.GeminiPart textPart = GeminiContent.GeminiPart.builder()
                .text("Here's your generated image:")
                .build();
        GeminiContent.GeminiPart imagePart =
                GeminiContent.GeminiPart.builder().inlineData(imageBlob).build();
        List<GeminiContent.GeminiPart> parts = List.of(textPart, imagePart);

        // When
        AiMessage result = PartsAndContentsMapper.fromGPartsToAiMessage(parts, false, null);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.text()).isEqualTo("Here's your generated image:");
        assertThat(result.toolExecutionRequests()).isEmpty();

        // Verify generated images are stored in attributes
        List<Image> generatedImages = GeneratedImageHelper.getGeneratedImages(result);
        assertThat(generatedImages).hasSize(1);
        assertThat(generatedImages.get(0).base64Data()).isEqualTo(imageBlob.data());
        assertThat(generatedImages.get(0).mimeType()).isEqualTo("image/png");
    }

    @Test
    void fromGPartsToAiMessage_ignoresNonImageInlineData() {
        // Given
        GeminiBlob audioBlob = new GeminiBlob("audio/mp3", "base64audiodata");
        GeminiContent.GeminiPart audioPart =
                GeminiContent.GeminiPart.builder().inlineData(audioBlob).build();
        List<GeminiContent.GeminiPart> parts = List.of(audioPart);

        // When
        AiMessage result = PartsAndContentsMapper.fromGPartsToAiMessage(parts, false, null);

        // Then
        assertThat(result).isNotNull();
        List<Image> generatedImages = GeneratedImageHelper.getGeneratedImages(result);
        assertThat(generatedImages).isEmpty(); // Should ignore non-image data
    }

    @Test
    void fromContentToGPart_handlesDataUriImage() {
        // Given - Create a simple base64 encoded 1x1 red pixel PNG
        String base64Image = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFBQIAX8jx0gAAAABJRU5ErkJggg==";
        String dataUri = "data:image/png;base64," + base64Image;

        // Create ImageContent with data URI (this is how images are typically sent from web clients)
        ImageContent imageContent = ImageContent.from(dataUri);

        // When - This should not throw NullPointerException
        GeminiContent.GeminiPart result = PartsAndContentsMapper.fromContentToGPart(imageContent);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.inlineData()).isNotNull();
        assertThat(result.inlineData().mimeType()).isEqualTo("image/png");
        assertThat(result.inlineData().data()).isEqualTo(base64Image);
    }

    @Test
    void fromContentToGPart_handlesDataUriAudio() {
        // Given
        String base64Audio = "SGVsbG8gV29ybGQ="; // "Hello World" in base64
        String dataUri = "data:audio/mp3;base64," + base64Audio;

        Audio audio = Audio.builder().url(dataUri).build();
        AudioContent audioContent = new AudioContent(audio);

        // When
        GeminiContent.GeminiPart result = PartsAndContentsMapper.fromContentToGPart(audioContent);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.inlineData()).isNotNull();
        assertThat(result.inlineData().mimeType()).isEqualTo("audio/mp3");
        assertThat(result.inlineData().data()).isEqualTo(base64Audio);
    }

    @Test
    void fromContentToGPart_handlesDataUriVideo() {
        // Given
        String base64Video = "VmlkZW9EYXRh"; // "VideoData" in base64
        String dataUri = "data:video/mp4;base64," + base64Video;

        Video video = Video.builder().url(dataUri).build();
        VideoContent videoContent = new VideoContent(video);

        // When
        GeminiContent.GeminiPart result = PartsAndContentsMapper.fromContentToGPart(videoContent);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.inlineData()).isNotNull();
        assertThat(result.inlineData().mimeType()).isEqualTo("video/mp4");
        assertThat(result.inlineData().data()).isEqualTo(base64Video);
    }

    @Test
    void fromContentToGPart_handlesDataUriPdf() {
        // Given
        String base64Pdf = "UERGRGF0YQ=="; // "PDFData" in base64
        String dataUri = "data:application/pdf;base64," + base64Pdf;

        PdfFile pdfFile = PdfFile.builder().url(dataUri).build();
        PdfFileContent pdfFileContent = new PdfFileContent(pdfFile);

        // When
        GeminiContent.GeminiPart result = PartsAndContentsMapper.fromContentToGPart(pdfFileContent);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.inlineData()).isNotNull();
        assertThat(result.inlineData().mimeType()).isEqualTo("application/pdf");
        assertThat(result.inlineData().data()).isEqualTo(base64Pdf);
    }

    @Test
    void fromContentToGPart_handlesDataUriWithoutBase64Marker() {
        // Given - Data URI without ";base64" marker
        String base64Image = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFBQIAX8jx0gAAAABJRU5ErkJggg==";
        String dataUri = "data:image/png," + base64Image;

        ImageContent imageContent = ImageContent.from(dataUri);

        // When
        GeminiContent.GeminiPart result = PartsAndContentsMapper.fromContentToGPart(imageContent);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.inlineData()).isNotNull();
        assertThat(result.inlineData().mimeType()).isEqualTo("image/png");
        assertThat(result.inlineData().data()).isEqualTo(base64Image);
    }

    @Test
    void fromContentToGPart_throwsExceptionForInvalidDataUri() {
        // Given - Invalid data URI without comma
        String invalidDataUri = "data:image/png;base64";

        ImageContent imageContent = ImageContent.from(invalidDataUri);

        // When/Then
        assertThatThrownBy(() -> PartsAndContentsMapper.fromContentToGPart(imageContent))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid data URI format");
    }
}
