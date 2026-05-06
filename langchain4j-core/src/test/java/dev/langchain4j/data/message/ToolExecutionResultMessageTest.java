package dev.langchain4j.data.message;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.image.Image;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

class ToolExecutionResultMessageTest implements WithAssertions {

    @Test
    void methods() {
        ToolExecutionResultMessage tm = new ToolExecutionResultMessage("id", "toolName", "text");
        assertThat(tm.id()).isEqualTo("id");
        assertThat(tm.toolName()).isEqualTo("toolName");
        assertThat(tm.text()).isEqualTo("text");
        assertThat(tm.contents()).hasSize(1);
        assertThat(tm.contents().get(0)).isInstanceOf(TextContent.class);
        assertThat(tm.hasSingleText()).isTrue();
        assertThat(tm.isError()).isNull();
        assertThat(tm.type()).isEqualTo(ChatMessageType.TOOL_EXECUTION_RESULT);
        assertThat(tm.toString())
                .contains("id='id'", "toolName='toolName'", "isError=null")
                .contains("TextContent")
                .contains("text");
    }

    @Test
    void methods_with_isError() {
        ToolExecutionResultMessage tm = ToolExecutionResultMessage.builder()
                .id("id")
                .toolName("toolName")
                .text("error message")
                .isError(true)
                .build();
        assertThat(tm.id()).isEqualTo("id");
        assertThat(tm.toolName()).isEqualTo("toolName");
        assertThat(tm.text()).isEqualTo("error message");
        assertThat(tm.contents()).containsExactly(TextContent.from("error message"));
        assertThat(tm.isError()).isTrue();
        assertThat(tm.type()).isEqualTo(ChatMessageType.TOOL_EXECUTION_RESULT);

        assertThat(tm.toString())
                .contains("id='id'", "toolName='toolName'", "isError=true")
                .contains("TextContent")
                .contains("error message");
    }

    @Test
    void equals_hash_code() {
        ToolExecutionResultMessage t1 = new ToolExecutionResultMessage("id", "toolName", "text");
        ToolExecutionResultMessage t2 = new ToolExecutionResultMessage("id", "toolName", "text");

        ToolExecutionResultMessage t3 = new ToolExecutionResultMessage("foo", "toolName", "text");
        ToolExecutionResultMessage t4 = new ToolExecutionResultMessage("foo", "toolName", "text");

        assertThat(t1)
                .isEqualTo(t1)
                .isNotEqualTo(null)
                .isNotEqualTo(new Object())
                .isEqualTo(t2)
                .hasSameHashCodeAs(t2)
                .isNotEqualTo(ToolExecutionResultMessage.from("changed", "toolName", "text"))
                .isNotEqualTo(ToolExecutionResultMessage.from("id", "changed", "text"))
                .isNotEqualTo(ToolExecutionResultMessage.from("id", "toolName", "changed"))
                .isNotEqualTo(ToolExecutionResultMessage.builder()
                        .id("id")
                        .toolName("toolName")
                        .text("text")
                        .isError(true)
                        .build())
                .isNotEqualTo(t3)
                .doesNotHaveSameHashCodeAs(t3);

        assertThat(t3).isEqualTo(t3).isEqualTo(t4).hasSameHashCodeAs(t4);
    }

    @Test
    void builders() {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("id")
                .name("toolName")
                .arguments("arguments")
                .build();

        assertThat(new ToolExecutionResultMessage("id", "toolName", "text"))
                .isEqualTo(ToolExecutionResultMessage.from("id", "toolName", "text"))
                .isEqualTo(ToolExecutionResultMessage.from(request, "text"))
                .isEqualTo(ToolExecutionResultMessage.toolExecutionResultMessage("id", "toolName", "text"))
                .isEqualTo(ToolExecutionResultMessage.toolExecutionResultMessage(request, "text"))
                .isEqualTo(ToolExecutionResultMessage.builder()
                        .id("id")
                        .toolName("toolName")
                        .text("text")
                        .build());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void should_allow_empty_or_blank_text(String text) {
        ToolExecutionResultMessage message = new ToolExecutionResultMessage("id", "toolName", text);
        assertThat(message.text()).isEqualTo(text);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void should_allow_empty_or_blank_text__builder_text(String text) {
        ToolExecutionResultMessage message = ToolExecutionResultMessage.builder()
                .id("id")
                .toolName("toolName")
                .text(text)
                .build();
        assertThat(message.text()).isEqualTo(text);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void should_allow_empty_or_blank_text__builder_contents(String text) {
        ToolExecutionResultMessage message = ToolExecutionResultMessage.builder()
                .id("id")
                .toolName("toolName")
                .contents(TextContent.from(text))
                .build();
        assertThat(message.text()).isEqualTo(text);
    }

    @Test
    void builder_text_should_produce_single_TextContent() {
        ToolExecutionResultMessage message = ToolExecutionResultMessage.builder()
                .id("id")
                .toolName("tool")
                .text("hello")
                .build();

        assertThat(message.text()).isEqualTo("hello");
        assertThat(message.contents()).containsExactly(TextContent.from("hello"));
        assertThat(message.hasSingleText()).isTrue();
    }

    @Test
    void builder_contents_with_single_TextContent() {
        ToolExecutionResultMessage message = ToolExecutionResultMessage.builder()
                .id("id")
                .toolName("tool")
                .contents(TextContent.from("hello"))
                .build();

        assertThat(message.text()).isEqualTo("hello");
        assertThat(message.contents()).containsExactly(TextContent.from("hello"));
        assertThat(message.hasSingleText()).isTrue();
    }

    @Test
    void builder_contents_with_single_ImageContent() {
        ImageContent image = ImageContent.from(
                Image.builder().base64Data("abc").mimeType("image/png").build());

        ToolExecutionResultMessage message = ToolExecutionResultMessage.builder()
                .id("id")
                .toolName("tool")
                .contents(image)
                .build();

        assertThat(message.contents()).containsExactly(image);
        assertThat(message.hasSingleText()).isFalse();
        assertThatThrownBy(message::text)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Use contents() instead");
    }

    @Test
    void builder_contents_with_text_and_image() {
        TextContent text = TextContent.from("description");
        ImageContent image = ImageContent.from(
                Image.builder().base64Data("abc").mimeType("image/png").build());

        ToolExecutionResultMessage message = ToolExecutionResultMessage.builder()
                .id("id")
                .toolName("tool")
                .contents(text, image)
                .build();

        assertThat(message.contents()).containsExactly(text, image);
        assertThat(message.hasSingleText()).isFalse();
        assertThatThrownBy(message::text)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Use contents() instead");
    }

    @Test
    void builder_contents_with_multiple_TextContent() {
        ToolExecutionResultMessage message = ToolExecutionResultMessage.builder()
                .id("id")
                .toolName("tool")
                .contents(TextContent.from("first"), TextContent.from("second"))
                .build();

        assertThat(message.contents()).hasSize(2);
        assertThat(message.hasSingleText()).isFalse();
        assertThatThrownBy(message::text)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Use contents() instead");
    }

    @Test
    void builder_contents_with_list() {
        List<Content> contents = List.of(
                TextContent.from("text"),
                ImageContent.from(Image.builder().base64Data("abc").mimeType("image/png").build()));

        ToolExecutionResultMessage message = ToolExecutionResultMessage.builder()
                .id("id")
                .toolName("tool")
                .contents(contents)
                .build();

        assertThat(message.contents()).isEqualTo(contents);
        assertThat(message.hasSingleText()).isFalse();
    }

    @Test
    void builder_should_fail_when_both_text_and_contents_set() {
        assertThatThrownBy(() -> ToolExecutionResultMessage.builder()
                .id("id")
                .toolName("tool")
                .text("hello")
                .contents(TextContent.from("world"))
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not both");
    }

    @Test
    void builder_should_fail_when_neither_text_nor_contents_set() {
        assertThatThrownBy(() -> ToolExecutionResultMessage.builder()
                .id("id")
                .toolName("tool")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Either text or contents must be provided");
    }

    @Test
    void constructor_text_produces_single_TextContent() {
        ToolExecutionResultMessage message = new ToolExecutionResultMessage("id", "tool", "hello");

        assertThat(message.text()).isEqualTo("hello");
        assertThat(message.contents()).containsExactly(TextContent.from("hello"));
        assertThat(message.hasSingleText()).isTrue();
    }

    @Test
    void constructor_empty_text_produces_single_empty_TextContent() {
        ToolExecutionResultMessage message = new ToolExecutionResultMessage("id", "tool", "");

        assertThat(message.text()).isEmpty();
        assertThat(message.contents()).containsExactly(TextContent.from(""));
        assertThat(message.hasSingleText()).isTrue();
    }
}
