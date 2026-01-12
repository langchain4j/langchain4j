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
        String base64Image =
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFBQIAX8jx0gAAAABJRU5ErkJggg==";
        String dataUri = "data:image/png;base64," + base64Image;

        // Create ImageContent with data URI (this is how images are typically sent from
        // web clients)
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
        String base64Image =
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFBQIAX8jx0gAAAABJRU5ErkJggg==";
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
    void fromContentToGPart_handlesDataUriImageWithoutBase64Marker() {
        // Given - Data URI without ";base64" marker
        String base64Image =
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFBQIAX8jx0gAAAABJRU5ErkJggg==";
        String dataUri = "data:image/jpeg," + base64Image;

        ImageContent imageContent = ImageContent.from(dataUri);

        // When
        GeminiContent.GeminiPart result = PartsAndContentsMapper.fromContentToGPart(imageContent);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.inlineData()).isNotNull();
        assertThat(result.inlineData().mimeType()).isEqualTo("image/jpeg");
        assertThat(result.inlineData().data()).isEqualTo(base64Image);
    }

    @Test
    void fromContentToGPart_handlesDataUriAudioWithoutBase64Marker() {
        // Given - Data URI without ";base64" marker
        String base64Audio = "QXVkaW9EYXRh";
        String dataUri = "data:audio/wav," + base64Audio;

        Audio audio = Audio.builder().url(dataUri).build();
        AudioContent audioContent = new AudioContent(audio);

        // When
        GeminiContent.GeminiPart result = PartsAndContentsMapper.fromContentToGPart(audioContent);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.inlineData()).isNotNull();
        assertThat(result.inlineData().mimeType()).isEqualTo("audio/wav");
        assertThat(result.inlineData().data()).isEqualTo(base64Audio);
    }

    @Test
    void fromContentToGPart_handlesDataUriVideoWithoutBase64Marker() {
        // Given - Data URI without ";base64" marker
        String base64Video = "VmlkZW9EYXRh";
        String dataUri = "data:video/webm," + base64Video;

        Video video = Video.builder().url(dataUri).build();
        VideoContent videoContent = new VideoContent(video);

        // When
        GeminiContent.GeminiPart result = PartsAndContentsMapper.fromContentToGPart(videoContent);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.inlineData()).isNotNull();
        assertThat(result.inlineData().mimeType()).isEqualTo("video/webm");
        assertThat(result.inlineData().data()).isEqualTo(base64Video);
    }

    @Test
    void fromContentToGPart_handlesDataUriPdfWithoutBase64Marker() {
        // Given - Data URI without ";base64" marker
        String base64Pdf = "UERGRGF0YQ==";
        String dataUri = "data:application/pdf," + base64Pdf;

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
    void fromContentToGPart_throwsExceptionForInvalidDataUriImage() {
        // Given - Invalid data URI without comma
        String invalidDataUri = "data:image/png;base64";

        ImageContent imageContent = ImageContent.from(invalidDataUri);

        // When/Then
        assertThatThrownBy(() -> PartsAndContentsMapper.fromContentToGPart(imageContent))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid data URI format");
    }

    @Test
    void fromContentToGPart_throwsExceptionForInvalidDataUriAudio() {
        // Given - Invalid data URI without comma
        String invalidDataUri = "data:audio/mp3";

        Audio audio = Audio.builder().url(invalidDataUri).build();
        AudioContent audioContent = new AudioContent(audio);

        // When/Then
        assertThatThrownBy(() -> PartsAndContentsMapper.fromContentToGPart(audioContent))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid data URI format");
    }

    @Test
    void fromContentToGPart_throwsExceptionForInvalidDataUriVideo() {
        // Given - Invalid data URI without comma
        String invalidDataUri = "data:video/mp4";

        Video video = Video.builder().url(invalidDataUri).build();
        VideoContent videoContent = new VideoContent(video);

        // When/Then
        assertThatThrownBy(() -> PartsAndContentsMapper.fromContentToGPart(videoContent))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid data URI format");
    }

    @Test
    void fromContentToGPart_throwsExceptionForInvalidDataUriPdf() {
        // Given - Invalid data URI without comma
        String invalidDataUri = "data:application/pdf";

        PdfFile pdfFile = PdfFile.builder().url(invalidDataUri).build();
        PdfFileContent pdfFileContent = new PdfFileContent(pdfFile);

        // When/Then
        assertThatThrownBy(() -> PartsAndContentsMapper.fromContentToGPart(pdfFileContent))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid data URI format");
    }

    @Test
    void fromContentToGPart_handlesDataUriImageWithDifferentMimeTypes() {
        // Test various image MIME types
        String base64Data = "R0lGODlhAQABAAAAACw=";

        // GIF
        ImageContent gifContent = ImageContent.from("data:image/gif;base64," + base64Data);
        GeminiContent.GeminiPart gifResult = PartsAndContentsMapper.fromContentToGPart(gifContent);
        assertThat(gifResult.inlineData().mimeType()).isEqualTo("image/gif");

        // WebP
        ImageContent webpContent = ImageContent.from("data:image/webp;base64," + base64Data);
        GeminiContent.GeminiPart webpResult = PartsAndContentsMapper.fromContentToGPart(webpContent);
        assertThat(webpResult.inlineData().mimeType()).isEqualTo("image/webp");

        // SVG
        ImageContent svgContent = ImageContent.from("data:image/svg+xml;base64," + base64Data);
        GeminiContent.GeminiPart svgResult = PartsAndContentsMapper.fromContentToGPart(svgContent);
        assertThat(svgResult.inlineData().mimeType()).isEqualTo("image/svg+xml");
    }

    @Test
    void fromContentToGPart_handlesDataUriAudioWithDifferentMimeTypes() {
        // Test various audio MIME types
        String base64Data = "QXVkaW9EYXRh";

        // WAV
        Audio wavAudio =
                Audio.builder().url("data:audio/wav;base64," + base64Data).build();
        GeminiContent.GeminiPart wavResult = PartsAndContentsMapper.fromContentToGPart(new AudioContent(wavAudio));
        assertThat(wavResult.inlineData().mimeType()).isEqualTo("audio/wav");

        // OGG
        Audio oggAudio =
                Audio.builder().url("data:audio/ogg;base64," + base64Data).build();
        GeminiContent.GeminiPart oggResult = PartsAndContentsMapper.fromContentToGPart(new AudioContent(oggAudio));
        assertThat(oggResult.inlineData().mimeType()).isEqualTo("audio/ogg");

        // FLAC
        Audio flacAudio =
                Audio.builder().url("data:audio/flac;base64," + base64Data).build();
        GeminiContent.GeminiPart flacResult = PartsAndContentsMapper.fromContentToGPart(new AudioContent(flacAudio));
        assertThat(flacResult.inlineData().mimeType()).isEqualTo("audio/flac");
    }

    @Test
    void fromContentToGPart_handlesDataUriVideoWithDifferentMimeTypes() {
        // Test various video MIME types
        String base64Data = "VmlkZW9EYXRh";

        // MP4
        Video mp4Video =
                Video.builder().url("data:video/mp4;base64," + base64Data).build();
        GeminiContent.GeminiPart mp4Result = PartsAndContentsMapper.fromContentToGPart(new VideoContent(mp4Video));
        assertThat(mp4Result.inlineData().mimeType()).isEqualTo("video/mp4");

        // WebM
        Video webmVideo =
                Video.builder().url("data:video/webm;base64," + base64Data).build();
        GeminiContent.GeminiPart webmResult = PartsAndContentsMapper.fromContentToGPart(new VideoContent(webmVideo));
        assertThat(webmResult.inlineData().mimeType()).isEqualTo("video/webm");

        // MPEG
        Video mpegVideo =
                Video.builder().url("data:video/mpeg;base64," + base64Data).build();
        GeminiContent.GeminiPart mpegResult = PartsAndContentsMapper.fromContentToGPart(new VideoContent(mpegVideo));
        assertThat(mpegResult.inlineData().mimeType()).isEqualTo("video/mpeg");
    }

    @Test
    void fromContentToGPart_handlesImageWithDetailLevelLow() {
        // Given
        String base64Image =
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFBQIAX8jx0gAAAABJRU5ErkJggg==";
        Image image =
                Image.builder().base64Data(base64Image).mimeType("image/png").build();
        ImageContent imageContent = ImageContent.from(image, ImageContent.DetailLevel.LOW);

        // When
        GeminiContent.GeminiPart result = PartsAndContentsMapper.fromContentToGPart(imageContent);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.inlineData()).isNotNull();
        assertThat(result.mediaResolution()).isNotNull();
        assertThat(result.mediaResolution().level()).isEqualTo(GeminiMediaResolutionLevel.MEDIA_RESOLUTION_LOW);
    }

    @Test
    void fromContentToGPart_handlesImageWithDetailLevelHigh() {
        // Given
        String base64Image =
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFBQIAX8jx0gAAAABJRU5ErkJggg==";
        Image image =
                Image.builder().base64Data(base64Image).mimeType("image/png").build();
        ImageContent imageContent = ImageContent.from(image, ImageContent.DetailLevel.HIGH);

        // When
        GeminiContent.GeminiPart result = PartsAndContentsMapper.fromContentToGPart(imageContent);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.mediaResolution()).isNotNull();
        assertThat(result.mediaResolution().level()).isEqualTo(GeminiMediaResolutionLevel.MEDIA_RESOLUTION_HIGH);
    }

    @Test
    void fromContentToGPart_handlesImageWithDetailLevelAuto() {
        // Given
        String base64Image =
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFBQIAX8jx0gAAAABJRU5ErkJggg==";
        Image image =
                Image.builder().base64Data(base64Image).mimeType("image/png").build();
        ImageContent imageContent = ImageContent.from(image, ImageContent.DetailLevel.AUTO);

        // When
        GeminiContent.GeminiPart result = PartsAndContentsMapper.fromContentToGPart(imageContent);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.mediaResolution()).isNotNull();
        assertThat(result.mediaResolution().level()).isEqualTo(GeminiMediaResolutionLevel.MEDIA_RESOLUTION_UNSPECIFIED);
    }

    @Test
    void fromContentToGPart_handlesImageWithDataUriAndDetailLevel() {
        // Given
        String base64Image =
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFBQIAX8jx0gAAAABJRU5ErkJggg==";
        String dataUri = "data:image/png;base64," + base64Image;
        ImageContent imageContent = ImageContent.from(dataUri, ImageContent.DetailLevel.HIGH);

        // When
        GeminiContent.GeminiPart result = PartsAndContentsMapper.fromContentToGPart(imageContent);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.inlineData()).isNotNull();
        assertThat(result.mediaResolution()).isNotNull();
        assertThat(result.mediaResolution().level()).isEqualTo(GeminiMediaResolutionLevel.MEDIA_RESOLUTION_HIGH);
    }
}
