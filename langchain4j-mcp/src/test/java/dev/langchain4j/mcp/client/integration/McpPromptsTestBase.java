package dev.langchain4j.mcp.client.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.mcp.client.McpBlobResourceContents;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.McpEmbeddedResource;
import dev.langchain4j.mcp.client.McpException;
import dev.langchain4j.mcp.client.McpGetPromptResult;
import dev.langchain4j.mcp.client.McpImageContent;
import dev.langchain4j.mcp.client.McpPrompt;
import dev.langchain4j.mcp.client.McpPromptArgument;
import dev.langchain4j.mcp.client.McpPromptContent;
import dev.langchain4j.mcp.client.McpPromptMessage;
import dev.langchain4j.mcp.client.McpResourceContents;
import dev.langchain4j.mcp.client.McpRole;
import dev.langchain4j.mcp.client.McpTextContent;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public abstract class McpPromptsTestBase {

    static McpClient mcpClient;

    @Test
    void listPrompts() {
        List<McpPrompt> prompts = mcpClient.listPrompts();
        assertThat(prompts.size()).isEqualTo(5);

        McpPrompt basic = findPromptByName("basic", prompts);
        assertThat(basic).isNotNull();
        assertThat(basic.description()).isEqualTo("Basic simple prompt");
        assertThat(basic.arguments()).isEmpty();

        McpPrompt image = findPromptByName("image", prompts);
        assertThat(image).isNotNull();
        assertThat(image.description()).isEqualTo("Prompt that returns an image");
        assertThat(image.arguments()).isEmpty();

        McpPrompt multi = findPromptByName("multi", prompts);
        assertThat(multi).isNotNull();
        assertThat(multi.description()).isEqualTo("Prompt that returns two messages");
        assertThat(multi.arguments()).isEmpty();

        McpPrompt parametrized = findPromptByName("parametrized", prompts);
        assertThat(parametrized).isNotNull();
        assertThat(parametrized.description()).isEqualTo("Parametrized prompt");
        assertThat(parametrized.arguments()).hasSize(1);
        McpPromptArgument arg = parametrized.arguments().get(0);
        assertThat(arg.name()).isEqualTo("name");
        assertThat(arg.description()).isEqualTo("The name");

        McpPrompt embeddedBlob = findPromptByName("embeddedBinaryResource", prompts);
        assertThat(embeddedBlob).isNotNull();
        assertThat(embeddedBlob.description()).isEqualTo("Prompt that returns an embedded binary resource");
        assertThat(embeddedBlob.arguments()).isEmpty();
    }

    @Test
    void getBasicPrompt() {
        McpGetPromptResult prompt = mcpClient.getPrompt("basic", Map.of());
        assertThat(prompt.description()).isEqualTo(null);
        assertThat(prompt.messages()).hasSize(1);
        McpPromptMessage message = prompt.messages().get(0);
        assertThat(message.role()).isEqualTo(McpRole.USER);
        assertThat(((McpTextContent) message.content()).text()).isEqualTo("Hello");
    }

    @Test
    void getMultiPrompt() {
        McpGetPromptResult prompt = mcpClient.getPrompt("multi", Map.of());
        assertThat(prompt.description()).isEqualTo(null);
        assertThat(prompt.messages()).hasSize(2);

        McpPromptMessage message1 = prompt.messages().get(0);
        assertThat(message1.role()).isEqualTo(McpRole.USER);
        assertThat(message1.content().type()).isEqualTo(McpPromptContent.Type.TEXT);
        assertThat(((McpTextContent) message1.content()).text()).isEqualTo("first");

        McpPromptMessage message2 = prompt.messages().get(1);
        assertThat(message2.role()).isEqualTo(McpRole.USER);
        assertThat(message2.content().type()).isEqualTo(McpPromptContent.Type.TEXT);
        assertThat(((McpTextContent) message2.content()).text()).isEqualTo("second");
    }

    @Test
    void getImagePrompt() {
        McpGetPromptResult prompt = mcpClient.getPrompt("image", Map.of());
        assertThat(prompt.description()).isEqualTo(null);
        assertThat(prompt.messages()).hasSize(1);
        McpPromptMessage message = prompt.messages().get(0);
        assertThat(message.role()).isEqualTo(McpRole.USER);
        assertThat(message.content().type()).isEqualTo(McpPromptContent.Type.IMAGE);
        assertThat(((McpImageContent) message.content()).data()).isEqualTo("aaa");
        assertThat(((McpImageContent) message.content()).mimeType()).isEqualTo("image/png");
    }

    @Test
    void getParametrizedPrompt() {
        McpGetPromptResult prompt = mcpClient.getPrompt("parametrized", Map.of("name", "Bob"));
        assertThat(prompt.description()).isEqualTo(null);
        assertThat(prompt.messages()).hasSize(1);
        McpPromptMessage message = prompt.messages().get(0);
        assertThat(message.role()).isEqualTo(McpRole.USER);
        assertThat(message.content().type()).isEqualTo(McpPromptContent.Type.TEXT);
        assertThat(((McpTextContent) message.content()).text()).isEqualTo("Hello Bob");
    }

    @Test
    void getNonExistentPrompt() {
        assertThatThrownBy(() -> mcpClient.getPrompt("DOES-NOT-EXIST", Map.of()))
                .isInstanceOf(McpException.class);
    }

    @Test
    void getPromptWithEmbeddedBinaryResource() {
        McpGetPromptResult prompt = mcpClient.getPrompt("embeddedBinaryResource", Map.of());
        McpPromptMessage message = prompt.messages().get(0);
        assertThat(message.role()).isEqualTo(McpRole.USER);
        assertThat(message.content().type()).isEqualTo(McpPromptContent.Type.RESOURCE);
        assertThat(message.content()).isInstanceOf(McpEmbeddedResource.class);
        McpEmbeddedResource resource = (McpEmbeddedResource) message.content();
        assertThat(resource.resource().type()).isEqualTo(McpResourceContents.Type.BLOB);
        McpBlobResourceContents blob = (McpBlobResourceContents) resource.resource();
        assertThat(blob.uri()).isEqualTo("file:///embedded-blob");
        assertThat(blob.blob()).isEqualTo("aaaaa");
        assertThat(blob.mimeType()).isEqualTo("application/octet-stream");
    }

    private McpPrompt findPromptByName(String name, List<McpPrompt> promptRefs) {
        for (McpPrompt promptRef : promptRefs) {
            if (promptRef.name().equals(name)) {
                return promptRef;
            }
        }
        return null;
    }
}
