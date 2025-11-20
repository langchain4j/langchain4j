package dev.langchain4j.model.googleai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
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
}
