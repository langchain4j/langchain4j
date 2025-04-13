package dev.langchain4j.mcp.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;

/**
 * Test for converting PromptMessage as returned from MCP servers to instances
 * of ChatMessage from the core langchain4j API.
 */
public class PromptContentConversionTest {

    @Test
    public void testUserMessageWithText() throws JsonProcessingException {
        // language=JSON
        String response =
                """
                {"jsonrpc":"2.0","id":111,"result":{"messages":[{"role":"user","content":{"text":"Hello","type":"text"}}]}}
                """;
        JsonNode responseJsonNode = DefaultMcpClient.OBJECT_MAPPER.readTree(response);
        McpGetPromptResult promptResponse = PromptsHelper.parsePromptContents(responseJsonNode);

        ChatMessage chatMessage = promptResponse.messages().get(0).toChatMessage();
        assertThat(chatMessage).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) chatMessage).singleText()).isEqualTo("Hello");
    }

    @Test
    public void testAiMessageWithText() throws JsonProcessingException {
        // language=JSON
        String response =
                """
                {"jsonrpc":"2.0","id":123,"result":{"messages":[{"role":"assistant","content":{"text":"Hello","type":"text"}}]}}
                """;
        JsonNode responseJsonNode = DefaultMcpClient.OBJECT_MAPPER.readTree(response);
        McpGetPromptResult promptResponse = PromptsHelper.parsePromptContents(responseJsonNode);

        ChatMessage chatMessage = promptResponse.messages().get(0).toChatMessage();
        assertThat(chatMessage).isInstanceOf(AiMessage.class);
        assertThat(((AiMessage) chatMessage).text()).isEqualTo("Hello");
    }

    @Test
    public void testUserMessageWithImage() throws JsonProcessingException {
        // language=JSON
        String response =
                """
                {"jsonrpc":"2.0","id":1,"result":{"messages":[{"role":"user","content":{"data":"aaa","mimeType":"image/png","type":"image"}}]}}
                """;
        JsonNode responseJsonNode = DefaultMcpClient.OBJECT_MAPPER.readTree(response);
        McpGetPromptResult promptResponse = PromptsHelper.parsePromptContents(responseJsonNode);

        ChatMessage chatMessage = promptResponse.messages().get(0).toChatMessage();
        assertThat(chatMessage).isInstanceOf(UserMessage.class);
        Content content = ((UserMessage) chatMessage).contents().get(0);
        assertThat(content).isInstanceOf(ImageContent.class);
        ImageContent imageContent = (ImageContent) content;
        assertThat(imageContent.image().base64Data()).isEqualTo("aaa");
        assertThat(imageContent.image().mimeType()).isEqualTo("image/png");
    }
}
