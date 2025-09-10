package dev.langchain4j.model.googleai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import java.util.List;
import org.junit.jupiter.api.Test;

class PartsAndContentsMapperTest {

    @Test
    void fromGPartsToAiMessage_handlesNullParts() {

        // Given
        List<GeminiPart> parts = null;

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
        List<GeminiPart> parts = List.of();

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
        GeminiPart part = GeminiPart.builder().build();
        List<GeminiPart> parts = List.of(part);

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
        GeminiPart part = GeminiPart.builder().text("").build();
        List<GeminiPart> parts = List.of(part);

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
        GeminiPart part = GeminiPart.builder().text("Hello world").build();
        List<GeminiPart> parts = List.of(part);

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
        assertThat(result.get(0).getRole()).isEqualTo("model");
        assertThat(result.get(0).getParts().get(0).getText()).isEqualTo("system text");
    }

    @Test
    void fromMessageToGContent_userMessageWithTextContent() {
        UserMessage msg = new UserMessage(List.of(new dev.langchain4j.data.message.TextContent("user text")));
        List<GeminiContent> result = PartsAndContentsMapper.fromMessageToGContent(List.of(msg), null, false);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRole()).isEqualTo("user");
        assertThat(result.get(0).getParts().get(0).getText()).isEqualTo("user text");
    }

    @Test
    void fromMessageToGContent_toolExecutionResultMessage() {
        ToolExecutionResultMessage msg = new ToolExecutionResultMessage("toolId", "tool name", "tool response");
        List<GeminiContent> result = PartsAndContentsMapper.fromMessageToGContent(List.of(msg), null, false);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRole()).isEqualTo("user");
        assertThat(result.get(0).getParts().get(0).getFunctionResponse().getName())
                .isEqualTo("tool name");
        assertThat(result.get(0)
                        .getParts()
                        .get(0)
                        .getFunctionResponse()
                        .getResponse()
                        .get("response"))
                .isEqualTo("tool response");
    }

    @Test
    void fromMessageToGContent_emptyMessageListReturnsEmpty() {
        List<GeminiContent> result = PartsAndContentsMapper.fromMessageToGContent(List.of(), null, false);
        assertThat(result).isEmpty();
    }

    @Test
    void fromGPartsToAiMessage_handlesGeneratedImages() {
        // Given
        GeminiBlob imageBlob = GeminiBlob.builder()
                .mimeType("image/png")
                .data(
                        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8/5+hHgAHggJ/PchI7wAAAABJRU5ErkJggg==")
                .build();

        GeminiPart textPart =
                GeminiPart.builder().text("Here's your generated image:").build();
        GeminiPart imagePart = GeminiPart.builder().inlineData(imageBlob).build();
        List<GeminiPart> parts = List.of(textPart, imagePart);

        // When
        AiMessage result = PartsAndContentsMapper.fromGPartsToAiMessage(parts, false, null);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.text()).isEqualTo("Here's your generated image:");
        assertThat(result.toolExecutionRequests()).isEmpty();

        // Verify generated images are stored in attributes
        List<Image> generatedImages = GeneratedImageHelper.getGeneratedImages(result);
        assertThat(generatedImages).hasSize(1);
        assertThat(generatedImages.get(0).base64Data()).isEqualTo(imageBlob.getData());
        assertThat(generatedImages.get(0).mimeType()).isEqualTo("image/png");
    }

    @Test
    void fromGPartsToAiMessage_ignoresNonImageInlineData() {
        // Given
        GeminiBlob audioBlob = GeminiBlob.builder()
                .mimeType("audio/mp3")
                .data("base64audiodata")
                .build();

        GeminiPart audioPart = GeminiPart.builder().inlineData(audioBlob).build();
        List<GeminiPart> parts = List.of(audioPart);

        // When
        AiMessage result = PartsAndContentsMapper.fromGPartsToAiMessage(parts, false, null);

        // Then
        assertThat(result).isNotNull();
        List<Image> generatedImages = GeneratedImageHelper.getGeneratedImages(result);
        assertThat(generatedImages).isEmpty(); // Should ignore non-image data
    }
}
