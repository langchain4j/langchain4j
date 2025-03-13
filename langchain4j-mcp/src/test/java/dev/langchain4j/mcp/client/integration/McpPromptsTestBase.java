package dev.langchain4j.mcp.client.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.McpException;
import dev.langchain4j.mcp.client.PromptArg;
import dev.langchain4j.mcp.client.PromptContent;
import dev.langchain4j.mcp.client.PromptMessage;
import dev.langchain4j.mcp.client.PromptRef;
import dev.langchain4j.mcp.client.PromptResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public abstract class McpPromptsTestBase {

    static McpClient mcpClient;

    @Test
    void listPrompts() {
        List<PromptRef> prompts = mcpClient.listPrompts();
        assertThat(prompts.size()).isEqualTo(4);

        PromptRef basic = findPromptByName("basic", prompts);
        assertThat(basic).isNotNull();
        assertThat(basic.description()).isEqualTo("Basic simple prompt");
        assertThat(basic.arguments()).isEmpty();

        PromptRef image = findPromptByName("image", prompts);
        assertThat(image).isNotNull();
        assertThat(image.description()).isEqualTo("Prompt that returns an image");
        assertThat(image.arguments()).isEmpty();

        PromptRef multi = findPromptByName("multi", prompts);
        assertThat(multi).isNotNull();
        assertThat(multi.description()).isEqualTo("Prompt that returns two messages");
        assertThat(multi.arguments()).isEmpty();

        PromptRef parametrized = findPromptByName("parametrized", prompts);
        assertThat(parametrized).isNotNull();
        assertThat(parametrized.description()).isEqualTo("Parametrized prompt");
        assertThat(parametrized.arguments()).hasSize(1);
        PromptArg arg = parametrized.arguments().get(0);
        assertThat(arg.name()).isEqualTo("name");
        assertThat(arg.description()).isEqualTo("The name");
    }

    @Test
    void getBasicPrompt() {
        PromptResponse prompt = mcpClient.getPrompt("basic", Map.of());
        assertThat(prompt.description()).isEqualTo(null);
        assertThat(prompt.messages()).hasSize(1);
        PromptMessage message = prompt.messages().get(0);
        assertThat(message.role()).isEqualTo("user");
        assertThat(message.content().type()).isEqualTo(PromptContent.Type.TEXT);
        assertThat(message.content().asText().text()).isEqualTo("Hello");
    }

    @Test
    void getMultiPrompt() {
        PromptResponse prompt = mcpClient.getPrompt("multi", Map.of());
        assertThat(prompt.description()).isEqualTo(null);
        assertThat(prompt.messages()).hasSize(2);

        PromptMessage message1 = prompt.messages().get(0);
        assertThat(message1.role()).isEqualTo("user");
        assertThat(message1.content().type()).isEqualTo(PromptContent.Type.TEXT);
        assertThat(message1.content().asText().text()).isEqualTo("first");

        PromptMessage message2 = prompt.messages().get(1);
        assertThat(message2.role()).isEqualTo("user");
        assertThat(message2.content().type()).isEqualTo(PromptContent.Type.TEXT);
        assertThat(message2.content().asText().text()).isEqualTo("second");
    }

    @Test
    void getImagePrompt() {
        PromptResponse prompt = mcpClient.getPrompt("image", Map.of());
        assertThat(prompt.description()).isEqualTo(null);
        assertThat(prompt.messages()).hasSize(1);
        PromptMessage message = prompt.messages().get(0);
        assertThat(message.role()).isEqualTo("user");
        assertThat(message.content().type()).isEqualTo(PromptContent.Type.IMAGE);
        assertThat(message.content().asImage().data()).isEqualTo("aaa");
        assertThat(message.content().asImage().mimeType()).isEqualTo("image/png");
    }

    @Test
    void getParametrizedPrompt() {
        PromptResponse prompt = mcpClient.getPrompt("parametrized", Map.of("name", "Bob"));
        assertThat(prompt.description()).isEqualTo(null);
        assertThat(prompt.messages()).hasSize(1);
        PromptMessage message = prompt.messages().get(0);
        assertThat(message.role()).isEqualTo("user");
        assertThat(message.content().type()).isEqualTo(PromptContent.Type.TEXT);
        assertThat(message.content().asText().text()).isEqualTo("Hello Bob");
    }

    @Test
    void getNonExistentPrompt() {
        assertThatThrownBy(() -> mcpClient.getPrompt("DOES-NOT-EXIST", Map.of()))
                .isInstanceOf(McpException.class);
    }

    private PromptRef findPromptByName(String name, List<PromptRef> promptRefs) {
        for (PromptRef promptRef : promptRefs) {
            if (promptRef.name().equals(name)) {
                return promptRef;
            }
        }
        return null;
    }
}
