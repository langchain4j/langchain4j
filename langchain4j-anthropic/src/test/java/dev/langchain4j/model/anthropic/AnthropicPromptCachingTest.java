package dev.langchain4j.model.anthropic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.model.chat.TestStreamingChatResponseHandler;
import dev.langchain4j.model.chat.request.ChatRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AnthropicPromptCachingTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // language=json
    private static final String RESPONSE_BODY =
            """
            {
              "id": "msg_123",
              "type": "message",
              "role": "assistant",
              "model": "claude-opus-5-5",
              "content": [{"type": "text", "text": "Hello!"}],
              "stop_reason": "end_turn",
              "usage": {"input_tokens": 10, "output_tokens": 5}
            }
            """;

    private final MockHttpClient httpClient = new MockHttpClient(SuccessfulHttpResponse.builder()
            .statusCode(200)
            .headers(Map.of("content-type", List.of("application/json")))
            .body(RESPONSE_BODY)
            .build());

    @Test
    void should_not_send_any_cache_control_by_default() throws Exception {
        AnthropicChatModel model = modelBuilder().build();

        model.chat(conversation());

        assertThat(httpClient.request().body()).doesNotContain("cache_control");
    }

    @Test
    void should_send_top_level_cache_control_when_cache_automatically_is_enabled() throws Exception {
        AnthropicChatModel model = modelBuilder().cacheAutomatically(true).build();

        model.chat(conversation());

        JsonNode body = lastRequestBody();
        assertThat(body.get("cache_control").toString()).isEqualTo("{\"type\":\"ephemeral\"}");
        assertThat(cacheControls(body)).containsExactly("{\"type\":\"ephemeral\"}");
    }

    @Test
    void should_apply_cache_ttl_to_every_breakpoint() throws Exception {
        AnthropicChatModel model = modelBuilder()
                .cacheSystemMessages(true)
                .cacheTools(true)
                .cacheAutomatically(true)
                .cacheTtl(AnthropicChatRequestParameters.CACHE_TTL_1H)
                .build();

        UserMessage markedUserMessage = UserMessage.from("What is the weather in Munich?");
        markedUserMessage.attributes().put("cache_control", "ephemeral");

        model.chat(ChatRequest.builder()
                .messages(SystemMessage.from("You are a helpful assistant."), markedUserMessage)
                .toolSpecifications(weatherTool())
                .build());

        assertThat(cacheControls(lastRequestBody()))
                .hasSize(4) // system, tool, user message and top-level
                .containsOnly("{\"type\":\"ephemeral\",\"ttl\":\"1h\"}");
    }

    @Test
    void should_override_cache_automatically_and_cache_ttl_per_request() throws Exception {
        AnthropicChatModel model = modelBuilder().cacheAutomatically(false).build();

        model.chat(ChatRequest.builder()
                .messages(conversation().messages())
                .parameters(AnthropicChatRequestParameters.builder()
                        .cacheAutomatically(true)
                        .cacheTtl("1h")
                        .build())
                .build());

        assertThat(lastRequestBody().get("cache_control").toString())
                .isEqualTo("{\"type\":\"ephemeral\",\"ttl\":\"1h\"}");
    }

    @Test
    void should_disable_cache_automatically_per_request_when_model_default_is_enabled() throws Exception {
        AnthropicChatModel model = modelBuilder().cacheAutomatically(true).build();

        model.chat(ChatRequest.builder()
                .messages(conversation().messages())
                .parameters(AnthropicChatRequestParameters.builder()
                        .cacheAutomatically(false)
                        .build())
                .build());

        assertThat(httpClient.request().body()).doesNotContain("cache_control");
    }

    @Test
    void should_not_send_cache_control_when_only_cache_ttl_is_set() throws Exception {
        AnthropicChatModel model = modelBuilder().cacheTtl("1h").build();

        model.chat(conversation());

        assertThat(httpClient.request().body()).doesNotContain("cache_control");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void should_fail_on_blank_cache_ttl(String cacheTtl) {
        assertThatThrownBy(() -> modelBuilder().cacheTtl(cacheTtl).build())
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cacheTtl");
        assertThatThrownBy(() -> AnthropicChatRequestParameters.builder()
                        .cacheTtl(cacheTtl)
                        .build())
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cacheTtl");
    }

    @Test
    void should_configure_cache_automatically_and_cache_ttl_via_default_request_parameters() throws Exception {
        AnthropicChatModel model = modelBuilder()
                .defaultRequestParameters(AnthropicChatRequestParameters.builder()
                        .cacheAutomatically(true)
                        .cacheTtl(AnthropicChatRequestParameters.CACHE_TTL_1H)
                        .build())
                .build();

        model.chat(conversation());

        assertThat(lastRequestBody().get("cache_control").toString())
                .isEqualTo("{\"type\":\"ephemeral\",\"ttl\":\"1h\"}");
    }

    @Test
    void should_send_cache_automatically_and_cache_ttl_from_streaming_model() throws Exception {
        MockHttpClient streamingHttpClient = MockHttpClient.thatAlwaysResponds(List.of(
                new ServerSentEvent(
                        "message_start",
                        "{\"type\":\"message_start\",\"message\":{\"id\":\"msg_123\",\"type\":\"message\","
                                + "\"role\":\"assistant\",\"model\":\"claude-opus-5-5\","
                                + "\"usage\":{\"input_tokens\":10,\"output_tokens\":0}}}"),
                new ServerSentEvent("message_stop", "{\"type\":\"message_stop\"}")));
        AnthropicStreamingChatModel model = AnthropicStreamingChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(streamingHttpClient))
                .apiKey("test-key")
                .modelName("claude-opus-5-5")
                .cacheAutomatically(true)
                .defaultRequestParameters(AnthropicChatRequestParameters.builder()
                        .cacheTtl(AnthropicChatRequestParameters.CACHE_TTL_1H)
                        .build())
                .build();

        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(conversation(), handler);
        assertThat(handler.get()).isNotNull();

        JsonNode body = OBJECT_MAPPER.readTree(streamingHttpClient.request().body());
        assertThat(body.get("cache_control").toString()).isEqualTo("{\"type\":\"ephemeral\",\"ttl\":\"1h\"}");
    }

    private AnthropicChatModel.AnthropicChatModelBuilder modelBuilder() {
        return AnthropicChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(httpClient))
                .apiKey("test-key")
                .modelName("claude-opus-5-5");
    }

    private static ChatRequest conversation() {
        return ChatRequest.builder()
                .messages(
                        SystemMessage.from("You are a helpful assistant."),
                        UserMessage.from("Hi"),
                        AiMessage.from("Hello! How can I help?"),
                        UserMessage.from("Tell me a joke"))
                .build();
    }

    private static ToolSpecification weatherTool() {
        return ToolSpecification.builder()
                .name("weather")
                .description("Returns the weather")
                .build();
    }

    private JsonNode lastRequestBody() throws Exception {
        return OBJECT_MAPPER.readTree(httpClient.request().body());
    }

    private static List<String> cacheControls(JsonNode node) {
        List<String> result = new ArrayList<>();
        collectCacheControls(node, result);
        return result;
    }

    private static void collectCacheControls(JsonNode node, List<String> result) {
        if (node.isObject()) {
            node.fields().forEachRemaining(field -> {
                if (field.getKey().equals("cache_control")) {
                    result.add(field.getValue().toString());
                } else {
                    collectCacheControls(field.getValue(), result);
                }
            });
        } else if (node.isArray()) {
            node.forEach(element -> collectCacheControls(element, result));
        }
    }
}
