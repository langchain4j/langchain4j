package dev.langchain4j.model.bedrock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import java.net.URI;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultStatus;

class BedrockMessageConverterTest {

    @Test
    void convertContent_text() {
        ContentBlock block = BedrockMessageConverter.convertContent(TextContent.from("hello"));
        assertThat(block.text()).isEqualTo("hello");
    }

    @Test
    void convertContent_image_base64() {
        byte[] pngBytes = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47}; // PNG magic bytes
        String base64 = Base64.getEncoder().encodeToString(pngBytes);
        ImageContent image = ImageContent.from(base64, "image/png");

        ContentBlock block = BedrockMessageConverter.convertContent(image);
        assertThat(block.image()).isNotNull();
        assertThat(block.image().format().toString()).isEqualTo("png");
    }

    @Test
    void convertContent_unsupported_type_throws() {
        Content unsupported = new Content() {
            @Override
            public dev.langchain4j.data.message.ContentType type() {
                return null;
            }
        };
        assertThatThrownBy(() -> BedrockMessageConverter.convertContent(unsupported))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported content type");
    }

    @Test
    void convertContents_empty_list() {
        assertThat(BedrockMessageConverter.convertContents(Collections.emptyList())).isEmpty();
    }

    @Test
    void convertContents_null_list() {
        assertThat(BedrockMessageConverter.convertContents(null)).isEmpty();
    }

    @Test
    void convertContents_multiple() {
        List<Content> contents = List.of(TextContent.from("a"), TextContent.from("b"));
        List<ContentBlock> blocks = BedrockMessageConverter.convertContents(contents);
        assertThat(blocks).hasSize(2);
        assertThat(blocks.get(0).text()).isEqualTo("a");
        assertThat(blocks.get(1).text()).isEqualTo("b");
    }

    @Test
    void createImageBlock() {
        byte[] pngBytes = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47};
        String base64 = Base64.getEncoder().encodeToString(pngBytes);
        ImageContent image = ImageContent.from(base64, "image/png");

        ContentBlock block = BedrockMessageConverter.createImageBlock(image);
        assertThat(block.image()).isNotNull();
        assertThat(block.image().source().bytes().asByteArray()).isEqualTo(pngBytes);
    }

    @Test
    void createToolResultBlock() {
        ToolExecutionResultMessage msg = ToolExecutionResultMessage.from("tool-id-1", "myTool", "result text");
        ContentBlock block = BedrockMessageConverter.createToolResultBlock(msg);
        assertThat(block.toolResult().toolUseId()).isEqualTo("tool-id-1");
        assertThat(block.toolResult().content().get(0).text()).isEqualTo("result text");
        assertThat(block.toolResult().status()).isNull();
    }

    @Test
    void createToolResultBlock_error() {
        ToolExecutionResultMessage msg = ToolExecutionResultMessage.builder()
                .id("tool-id-1")
                .toolName("myTool")
                .text("something went wrong")
                .isError(true)
                .build();
        ContentBlock block = BedrockMessageConverter.createToolResultBlock(msg);
        assertThat(block.toolResult().toolUseId()).isEqualTo("tool-id-1");
        assertThat(block.toolResult().content().get(0).text()).isEqualTo("something went wrong");
        assertThat(block.toolResult().status()).isEqualTo(ToolResultStatus.ERROR);
    }

    @Test
    void convertToolRequests() {
        List<ToolExecutionRequest> requests = List.of(
                ToolExecutionRequest.builder()
                        .id("id-1")
                        .name("tool1")
                        .arguments("{\"key\":\"value\"}")
                        .build(),
                ToolExecutionRequest.builder()
                        .id("id-2")
                        .name("tool2")
                        .arguments("{}")
                        .build());

        List<ContentBlock> blocks = BedrockMessageConverter.convertToolRequests(requests);
        assertThat(blocks).hasSize(2);
        assertThat(blocks.get(0).toolUse().name()).isEqualTo("tool1");
        assertThat(blocks.get(0).toolUse().toolUseId()).isEqualTo("id-1");
        assertThat(blocks.get(1).toolUse().name()).isEqualTo("tool2");
    }

    @Test
    void extractFilenameWithoutExtensionFromUri_valid() {
        URI uri = URI.create("https://example.com/docs/report.pdf");
        String name = BedrockMessageConverter.extractFilenameWithoutExtensionFromUri(uri);
        assertThat(name).isEqualTo("report");
    }

    @Test
    void extractFilenameWithoutExtensionFromUri_null_generates_uuid() {
        String name = BedrockMessageConverter.extractFilenameWithoutExtensionFromUri(null);
        assertThat(name).isNotBlank();
        // Should be a valid UUID
        assertThat(name).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }
}
