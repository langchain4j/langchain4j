package dev.langchain4j.model.googleai;

import static org.assertj.core.api.Assertions.assertThat;

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
}
