package dev.langchain4j.model.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
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
 * Tests the {@code gpt-5.6+} prompt caching surface of the OpenAI Responses API:
 * {@code prompt_cache_options}, {@code prompt_cache_breakpoint}
 * and {@code usage.input_tokens_details.cache_write_tokens}.
 */
class OpenAiResponsesPromptCachingTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String RESPONSE = """
            {
              "id": "resp_test",
              "model": "gpt-5.6",
              "object": "response",
              "status": "completed",
              "output": [
                {
                  "id": "msg_1",
                  "type": "message",
                  "role": "assistant",
                  "content": [{"type": "output_text", "text": "Hello!"}]
                }
              ],
              "usage": {
                "input_tokens": 2000,
                "output_tokens": 5,
                "total_tokens": 2005,
                "input_tokens_details": {"cached_tokens": 512, "cache_write_tokens": 1024}
              }
            }
            """;

    private MockHttpClient mockHttpClient;

    private OpenAiResponsesChatModel model() {
        return model(OpenAiResponsesChatModel.builder());
    }

    private OpenAiResponsesChatModel model(OpenAiResponsesChatModel.Builder builder) {
        mockHttpClient = MockHttpClient.thatAlwaysResponds(
                SuccessfulHttpResponse.builder().statusCode(200).body(RESPONSE).build());
        return builder.apiKey("test-key")
                .baseUrl("http://localhost")
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .modelName("gpt-5.6")
                .build();
    }

    private JsonNode requestBody() throws Exception {
        return OBJECT_MAPPER.readTree(mockHttpClient.request().body());
    }

    private static Map<String, Object> breakpointAttributes() {
        return Map.of(OpenAiPromptCacheBreakpoint.ATTRIBUTE_KEY, OpenAiPromptCacheBreakpoint.MODE_EXPLICIT);
    }

    // ----- prompt_cache_options -----

    @Test
    void should_send_prompt_cache_options() throws Exception {
        OpenAiResponsesChatModel model = model(OpenAiResponsesChatModel.builder()
                .promptCacheOptions(OpenAiPromptCacheOptions.builder()
                        .mode(OpenAiPromptCacheOptions.MODE_EXPLICIT)
                        .ttl(OpenAiPromptCacheOptions.TTL_30M)
                        .build()));

        model.chat(ChatRequest.builder().messages(UserMessage.from("Hi")).build());

        JsonNode promptCacheOptions = requestBody().get("prompt_cache_options");
        assertThat(promptCacheOptions.get("mode").asText()).isEqualTo("explicit");
        assertThat(promptCacheOptions.get("ttl").asText()).isEqualTo("30m");
    }

    @Test
    void should_send_prompt_cache_options_from_request_parameters() throws Exception {
        OpenAiResponsesChatModel model = model();

        model.chat(ChatRequest.builder()
                .messages(UserMessage.from("Hi"))
                .parameters(OpenAiResponsesChatRequestParameters.builder()
                        .promptCacheOptions(OpenAiPromptCacheOptions.implicit())
                        .build())
                .build());

        JsonNode promptCacheOptions = requestBody().get("prompt_cache_options");
        assertThat(promptCacheOptions.get("mode").asText()).isEqualTo("implicit");
        assertThat(promptCacheOptions.has("ttl")).isFalse();
    }

    @Test
    void should_not_send_prompt_cache_options_by_default() throws Exception {
        model().chat(ChatRequest.builder().messages(UserMessage.from("Hi")).build());

        assertThat(requestBody().has("prompt_cache_options")).isFalse();
    }

    // ----- prompt_cache_breakpoint -----

    @Test
    void should_send_breakpoint_on_system_message() throws Exception {
        model().chat(ChatRequest.builder()
                .messages(
                        SystemMessage.builder()
                                .text("Shared instructions")
                                .attributes(breakpointAttributes())
                                .build(),
                        UserMessage.from("Hi"))
                .build());

        JsonNode systemContent = requestBody().get("input").get(0).get("content");
        assertThat(systemContent).hasSize(1);
        assertThat(systemContent.get(0).get("type").asText()).isEqualTo("input_text");
        assertThat(systemContent
                        .get(0)
                        .get("prompt_cache_breakpoint")
                        .get("mode")
                        .asText())
                .isEqualTo("explicit");
    }

    @Test
    void should_not_send_breakpoint_when_message_is_not_marked() throws Exception {
        model().chat(ChatRequest.builder()
                .messages(SystemMessage.from("Shared instructions"), UserMessage.from("Hi"))
                .build());

        assertThat(requestBody().get("input").toString()).doesNotContain("prompt_cache_breakpoint");
    }

    @Test
    void should_send_breakpoint_on_user_message() throws Exception {
        UserMessage userMessage = UserMessage.from("Long document");
        userMessage.attributes().putAll(breakpointAttributes());

        model().chat(ChatRequest.builder().messages(userMessage).build());

        JsonNode content = requestBody().get("input").get(0).get("content");
        assertThat(content.get(0).get("prompt_cache_breakpoint").get("mode").asText())
                .isEqualTo("explicit");
    }

    @Test
    void should_send_breakpoint_only_on_last_content_block_of_user_message() throws Exception {
        UserMessage userMessage = UserMessage.builder()
                .addContent(TextContent.from("Describe this"))
                .addContent(ImageContent.from("http://image.url"))
                .attributes(breakpointAttributes())
                .build();

        model().chat(ChatRequest.builder().messages(userMessage).build());

        JsonNode content = requestBody().get("input").get(0).get("content");
        assertThat(content).hasSize(2);
        assertThat(content.get(0).has("prompt_cache_breakpoint")).isFalse();
        assertThat(content.get(1).get("type").asText()).isEqualTo("input_image");
        assertThat(content.get(1).get("prompt_cache_breakpoint").get("mode").asText())
                .isEqualTo("explicit");
    }

    @Test
    void should_send_breakpoint_on_tool_execution_result_message() throws Exception {
        model().chat(ChatRequest.builder()
                .messages(
                        UserMessage.from("What is the weather?"),
                        AiMessage.from(ToolExecutionRequest.builder()
                                .id("call_123")
                                .name("getWeather")
                                .arguments("{}")
                                .build()),
                        ToolExecutionResultMessage.builder()
                                .id("call_123")
                                .toolName("getWeather")
                                .text("Sunny")
                                .attributes(breakpointAttributes())
                                .build())
                .build());

        JsonNode input = requestBody().get("input");
        JsonNode functionCallOutput = input.get(input.size() - 1);
        assertThat(functionCallOutput.get("type").asText()).isEqualTo("function_call_output");
        assertThat(functionCallOutput.get("output").isArray()).isTrue();
        assertThat(functionCallOutput
                        .get("output")
                        .get(0)
                        .get("prompt_cache_breakpoint")
                        .get("mode")
                        .asText())
                .isEqualTo("explicit");
    }

    // ----- validation -----

    @Test
    void should_fail_when_breakpoint_mode_is_not_supported() {
        OpenAiResponsesChatModel model = model();
        ChatRequest request = ChatRequest.builder()
                .messages(SystemMessage.builder()
                        .text("Shared instructions")
                        .attributes(Map.of(OpenAiPromptCacheBreakpoint.ATTRIBUTE_KEY, "explicit_v2"))
                        .build())
                .build();

        assertThatThrownBy(() -> model.chat(request))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("prompt_cache_breakpoint")
                .hasMessageContaining("explicit_v2");
    }

    @Test
    void should_fail_when_breakpoint_is_set_on_ai_message() {
        OpenAiResponsesChatModel model = model();
        ChatRequest request = ChatRequest.builder()
                .messages(
                        UserMessage.from("Hi"),
                        AiMessage.builder()
                                .text("Hello!")
                                .attributes(breakpointAttributes())
                                .build())
                .build();

        assertThatThrownBy(() -> model.chat(request))
                .isExactlyInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("AiMessage");
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

    @Test
    void should_leave_cache_write_tokens_null_when_not_reported() {
        mockHttpClient = MockHttpClient.thatAlwaysResponds(SuccessfulHttpResponse.builder()
                .statusCode(200)
                .body(RESPONSE.replace(", \"cache_write_tokens\": 1024", ""))
                .build());
        OpenAiResponsesChatModel model = OpenAiResponsesChatModel.builder()
                .apiKey("test-key")
                .baseUrl("http://localhost")
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .modelName("gpt-5.6")
                .build();

        ChatResponse response = model.chat(
                ChatRequest.builder().messages(UserMessage.from("Hi")).build());

        OpenAiTokenUsage tokenUsage = (OpenAiTokenUsage) response.tokenUsage();
        assertThat(tokenUsage.inputTokensDetails().cachedTokens()).isEqualTo(512);
        assertThat(tokenUsage.inputTokensDetails().cacheWriteTokens()).isNull();
    }
}
