package dev.langchain4j.model.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests the {@code gpt-5.6+} prompt caching surface of the OpenAI Chat Completions API:
 * {@code prompt_cache_key}, {@code prompt_cache_options}, {@code prompt_cache_breakpoint}
 * and {@code usage.prompt_tokens_details.cache_write_tokens}.
 */
class OpenAiPromptCachingTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String RESPONSE = """
            {
              "id": "chatcmpl-test",
              "object": "chat.completion",
              "created": 1721596428,
              "model": "gpt-5.6",
              "choices": [
                {
                  "index": 0,
                  "message": {"role": "assistant", "content": "Hello!"},
                  "finish_reason": "stop"
                }
              ],
              "usage": {
                "prompt_tokens": 2000,
                "completion_tokens": 5,
                "total_tokens": 2005,
                "prompt_tokens_details": {"cached_tokens": 512, "cache_write_tokens": 1024}
              }
            }
            """;

    private MockHttpClient mockHttpClient;

    private OpenAiChatModel model() {
        return model(OpenAiChatModel.builder());
    }

    private OpenAiChatModel model(OpenAiChatModel.OpenAiChatModelBuilder builder) {
        mockHttpClient = MockHttpClient.thatAlwaysResponds(
                SuccessfulHttpResponse.builder().statusCode(200).body(RESPONSE).build());
        return builder.apiKey("test-key")
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .modelName("gpt-5.6")
                .build();
    }

    private JsonNode requestBody() throws Exception {
        return OBJECT_MAPPER.readTree(mockHttpClient.request().body());
    }

    private static SystemMessage markedSystemMessage(String text) {
        return SystemMessage.builder()
                .text(text)
                .attributes(
                        Map.of(OpenAiPromptCacheBreakpoint.ATTRIBUTE_KEY, OpenAiPromptCacheBreakpoint.MODE_EXPLICIT))
                .build();
    }

    // ----- prompt_cache_key / prompt_cache_options -----

    @Test
    void should_send_prompt_cache_key_and_prompt_cache_options() throws Exception {
        OpenAiChatModel model = model(OpenAiChatModel.builder()
                .promptCacheKey("agent_123_v1")
                .promptCacheOptions(OpenAiPromptCacheOptions.builder()
                        .mode(OpenAiPromptCacheOptions.MODE_EXPLICIT)
                        .ttl(OpenAiPromptCacheOptions.TTL_30M)
                        .build()));

        model.chat("Hi");

        JsonNode body = requestBody();
        assertThat(body.get("prompt_cache_key").asText()).isEqualTo("agent_123_v1");
        assertThat(body.get("prompt_cache_options").get("mode").asText()).isEqualTo("explicit");
        assertThat(body.get("prompt_cache_options").get("ttl").asText()).isEqualTo("30m");
    }

    @Test
    void should_send_prompt_cache_options_from_request_parameters() throws Exception {
        OpenAiChatModel model = model();

        model.chat(ChatRequest.builder()
                .messages(UserMessage.from("Hi"))
                .parameters(OpenAiChatRequestParameters.builder()
                        .promptCacheOptions(OpenAiPromptCacheOptions.implicit())
                        .build())
                .build());

        JsonNode promptCacheOptions = requestBody().get("prompt_cache_options");
        assertThat(promptCacheOptions.get("mode").asText()).isEqualTo("implicit");
        assertThat(promptCacheOptions.has("ttl")).isFalse();
    }

    @Test
    void should_not_send_prompt_cache_fields_by_default() throws Exception {
        model().chat("Hi");

        JsonNode body = requestBody();
        assertThat(body.has("prompt_cache_key")).isFalse();
        assertThat(body.has("prompt_cache_options")).isFalse();
    }

    // ----- prompt_cache_breakpoint -----

    @Test
    void should_send_breakpoint_on_system_message() throws Exception {
        model().chat(ChatRequest.builder()
                .messages(markedSystemMessage("Shared instructions"), UserMessage.from("Hi"))
                .build());

        JsonNode systemContent = requestBody().get("messages").get(0).get("content");
        assertThat(systemContent.isArray()).isTrue();
        assertThat(systemContent).hasSize(1);
        assertThat(systemContent.get(0).get("type").asText()).isEqualTo("text");
        assertThat(systemContent.get(0).get("text").asText()).isEqualTo("Shared instructions");
        assertThat(systemContent
                        .get(0)
                        .get("prompt_cache_breakpoint")
                        .get("mode")
                        .asText())
                .isEqualTo("explicit");
    }

    @Test
    void should_send_system_message_as_plain_string_when_not_marked() throws Exception {
        model().chat(ChatRequest.builder()
                .messages(SystemMessage.from("Shared instructions"), UserMessage.from("Hi"))
                .build());

        JsonNode messages = requestBody().get("messages");
        assertThat(messages.get(0).get("content").asText()).isEqualTo("Shared instructions");
        assertThat(messages.toString()).doesNotContain("prompt_cache_breakpoint");
    }

    @Test
    void should_send_breakpoint_on_single_text_user_message() throws Exception {
        UserMessage userMessage = UserMessage.from("Long document");
        userMessage
                .attributes()
                .put(OpenAiPromptCacheBreakpoint.ATTRIBUTE_KEY, OpenAiPromptCacheBreakpoint.MODE_EXPLICIT);

        model().chat(ChatRequest.builder().messages(userMessage).build());

        JsonNode content = requestBody().get("messages").get(0).get("content");
        assertThat(content.isArray()).isTrue();
        assertThat(content.get(0).get("prompt_cache_breakpoint").get("mode").asText())
                .isEqualTo("explicit");
    }

    @Test
    void should_send_breakpoint_only_on_last_content_block_of_user_message() throws Exception {
        UserMessage userMessage = UserMessage.builder()
                .addContent(TextContent.from("Describe this"))
                .addContent(ImageContent.from("http://image.url"))
                .attributes(
                        Map.of(OpenAiPromptCacheBreakpoint.ATTRIBUTE_KEY, OpenAiPromptCacheBreakpoint.MODE_EXPLICIT))
                .build();

        model().chat(ChatRequest.builder().messages(userMessage).build());

        JsonNode content = requestBody().get("messages").get(0).get("content");
        assertThat(content).hasSize(2);
        assertThat(content.get(0).has("prompt_cache_breakpoint")).isFalse();
        assertThat(content.get(1).get("type").asText()).isEqualTo("image_url");
        assertThat(content.get(1).get("prompt_cache_breakpoint").get("mode").asText())
                .isEqualTo("explicit");
    }

    @Test
    void should_send_breakpoint_on_tool_execution_result_message() throws Exception {
        model().chat(ChatRequest.builder()
                .messages(
                        UserMessage.from("What is the weather?"),
                        AiMessage.from(dev.langchain4j.agent.tool.ToolExecutionRequest.builder()
                                .id("call_123")
                                .name("getWeather")
                                .arguments("{}")
                                .build()),
                        ToolExecutionResultMessage.builder()
                                .id("call_123")
                                .toolName("getWeather")
                                .text("Sunny")
                                .attributes(Map.of(
                                        OpenAiPromptCacheBreakpoint.ATTRIBUTE_KEY,
                                        OpenAiPromptCacheBreakpoint.MODE_EXPLICIT))
                                .build())
                .build());

        JsonNode messages = requestBody().get("messages");
        JsonNode toolMessage = messages.get(messages.size() - 1);
        assertThat(toolMessage.get("role").asText()).isEqualTo("tool");
        assertThat(toolMessage.get("content").isArray()).isTrue();
        assertThat(toolMessage
                        .get("content")
                        .get(0)
                        .get("prompt_cache_breakpoint")
                        .get("mode")
                        .asText())
                .isEqualTo("explicit");
    }

    // ----- validation -----

    @Test
    void should_fail_when_breakpoint_mode_is_not_supported() {
        OpenAiChatModel model = model();
        ChatRequest request = ChatRequest.builder()
                .messages(SystemMessage.builder()
                        .text("Shared instructions")
                        .attributes(Map.of(OpenAiPromptCacheBreakpoint.ATTRIBUTE_KEY, "implicit"))
                        .build())
                .build();

        assertThatThrownBy(() -> model.chat(request))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("prompt_cache_breakpoint")
                .hasMessageContaining("implicit")
                .hasMessageContaining("explicit");
    }

    @Test
    void should_fail_when_breakpoint_is_set_on_ai_message() {
        OpenAiChatModel model = model();
        ChatRequest request = ChatRequest.builder()
                .messages(
                        UserMessage.from("Hi"),
                        AiMessage.builder()
                                .text("Hello!")
                                .attributes(Map.of(
                                        OpenAiPromptCacheBreakpoint.ATTRIBUTE_KEY,
                                        OpenAiPromptCacheBreakpoint.MODE_EXPLICIT))
                                .build())
                .build();

        assertThatThrownBy(() -> model.chat(request))
                .isExactlyInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("AiMessage");
    }

    @Test
    void should_fail_when_breakpoint_is_set_on_legacy_function_message() {
        OpenAiChatModel model = model();
        ChatRequest request = ChatRequest.builder()
                .messages(ToolExecutionResultMessage.builder()
                        .toolName("getWeather")
                        .text("Sunny")
                        .attributes(Map.of(
                                OpenAiPromptCacheBreakpoint.ATTRIBUTE_KEY, OpenAiPromptCacheBreakpoint.MODE_EXPLICIT))
                        .build())
                .build();

        assertThatThrownBy(() -> model.chat(request))
                .isExactlyInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("function message");
    }

    // ----- cache_write_tokens -----

    @Test
    void should_return_cache_write_tokens() {
        ChatResponse response = model().chat(
                        ChatRequest.builder().messages(UserMessage.from("Hi")).build());

        OpenAiTokenUsage tokenUsage = (OpenAiTokenUsage) response.tokenUsage();
        assertThat(tokenUsage.inputTokensDetails().cachedTokens()).isEqualTo(512);
        assertThat(tokenUsage.inputTokensDetails().cacheWriteTokens()).isEqualTo(1024);
    }
}
