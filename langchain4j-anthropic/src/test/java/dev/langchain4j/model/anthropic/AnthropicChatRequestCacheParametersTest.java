package dev.langchain4j.model.anthropic;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ToolChoice;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AnthropicChatRequestCacheParametersTest {

    private WireMockServer wireMockServer;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(0);
        wireMockServer.start();
        wireMockServer.stubFor(post(anyUrl())
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                        {
                          "id": "msg_123",
                          "type": "message",
                          "role": "assistant",
                          "model": "claude-3-5-haiku-20241022",
                          "content": [{"type": "text", "text": "Hello!"}],
                          "stop_reason": "end_turn",
                          "usage": {"input_tokens": 10, "output_tokens": 5}
                        }
                        """)));
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void should_enable_system_message_caching_per_request_when_model_default_is_disabled() {
        AnthropicChatModel model = modelBuilder().build();

        ChatRequest request = ChatRequest.builder()
                .messages(SystemMessage.from("You are a helpful assistant."), UserMessage.from("Hi"))
                .parameters(AnthropicChatRequestParameters.builder()
                        .cacheSystemMessages(true)
                        .build())
                .build();

        model.chat(request);

        assertThat(lastRequestBody()).contains("cache_control").contains("ephemeral");
    }

    @Test
    void should_disable_system_message_caching_per_request_when_model_default_is_enabled() {
        AnthropicChatModel model = modelBuilder().cacheSystemMessages(true).build();

        ChatRequest request = ChatRequest.builder()
                .messages(SystemMessage.from("You are a helpful assistant."), UserMessage.from("Hi"))
                .parameters(AnthropicChatRequestParameters.builder()
                        .cacheSystemMessages(false)
                        .build())
                .build();

        model.chat(request);

        assertThat(lastRequestBody()).doesNotContain("cache_control");
    }

    @Test
    void should_enable_tool_caching_per_request_when_model_default_is_disabled() {
        AnthropicChatModel model = modelBuilder().build();

        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("What is the weather?"))
                .parameters(AnthropicChatRequestParameters.builder()
                        .toolSpecifications(weatherTool())
                        .cacheTools(true)
                        .build())
                .build();

        model.chat(request);

        assertThat(lastRequestBody()).contains("cache_control").contains("ephemeral");
    }

    @Test
    void should_disable_tool_caching_per_request_when_model_default_is_enabled() {
        AnthropicChatModel model = modelBuilder().cacheTools(true).build();

        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("What is the weather?"))
                .parameters(AnthropicChatRequestParameters.builder()
                        .toolSpecifications(weatherTool())
                        .cacheTools(false)
                        .build())
                .build();

        model.chat(request);

        assertThat(lastRequestBody()).doesNotContain("cache_control");
    }

    @Test
    void should_fall_back_to_model_default_when_request_does_not_specify_caching() {
        AnthropicChatModel model = modelBuilder().cacheSystemMessages(true).build();

        model.chat(ChatRequest.builder()
                .messages(SystemMessage.from("You are a helpful assistant."), UserMessage.from("Hi"))
                .build());

        assertThat(lastRequestBody()).contains("cache_control").contains("ephemeral");
    }

    @Test
    void should_override_thinking_parameters_per_request() {
        AnthropicChatModel model = modelBuilder()
                .thinkingType("enabled")
                .thinkingBudgetTokens(2000)
                .build();

        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("Think about life"))
                .parameters(AnthropicChatRequestParameters.builder()
                        .thinkingType("disabled")
                        .thinkingBudgetTokens(0)
                        .build())
                .build();

        model.chat(request);

        String body = lastRequestBody();
        assertThat(body).contains("\"thinking\"");
        assertThat(body).contains("\"type\"");
        assertThat(body).contains("disabled");
        assertThat(body).contains("\"budget_tokens\"");
        assertThat(body).contains("0");
    }

    @Test
    void should_override_tool_choice_name_and_parallel_tool_use_per_request() {
        AnthropicChatModel model = modelBuilder()
                .toolChoiceName("forced_tool")
                .disableParallelToolUse(true)
                .build();

        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("Use tools"))
                .parameters(AnthropicChatRequestParameters.builder()
                        .toolSpecifications(weatherTool())
                        .toolChoice(ToolChoice.REQUIRED)
                        .toolChoiceName("weather_tool")
                        .disableParallelToolUse(false)
                        .build())
                .build();

        model.chat(request);

        String body = lastRequestBody();
        assertThat(body).contains("\"name\"");
        assertThat(body).contains("weather_tool");
        assertThat(body).contains("\"disable_parallel_tool_use\"");
        assertThat(body).contains("false");
    }

    @Test
    void should_override_user_id_per_request() {
        AnthropicChatModel model = modelBuilder().userId("model-user").build();

        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("Hello"))
                .parameters(AnthropicChatRequestParameters.builder()
                        .userId("request-user")
                        .build())
                .build();

        model.chat(request);

        String body = lastRequestBody();
        assertThat(body).contains("\"user_id\"");
        assertThat(body).contains("request-user");
    }

    @Test
    void should_not_send_diagnostics_when_not_requested() {
        AnthropicChatModel model = modelBuilder().build();

        model.chat(ChatRequest.builder().messages(UserMessage.from("Hi")).build());

        assertThat(lastRequestBody()).doesNotContain("\"diagnostics\"");
    }

    @Test
    void should_opt_in_to_cache_diagnostics_with_null_previous_message_id_on_first_turn() {
        AnthropicChatModel model = modelBuilder().build();

        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("Hi"))
                .parameters(AnthropicChatRequestParameters.builder()
                        .returnCacheDiagnostics(true)
                        .build())
                .build();

        model.chat(request);

        String body = lastRequestBody();
        assertThat(body).contains("\"diagnostics\"");
        assertThat(body).contains("\"previous_message_id\" : null");
    }

    @Test
    void should_send_previous_message_id_on_subsequent_turn() {
        AnthropicChatModel model = modelBuilder().build();

        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("Hi"))
                .parameters(AnthropicChatRequestParameters.builder()
                        .returnCacheDiagnostics(true)
                        .previousMessageId("msg_previous")
                        .build())
                .build();

        model.chat(request);

        assertThat(lastRequestBody()).contains("\"previous_message_id\" : \"msg_previous\"");
    }

    @Test
    void should_clear_previous_message_id_per_request_even_when_model_default_is_set() {
        // Regression test: previousMessageId is a per-request value and is never carried as a
        // model-level default, so a stale id placed on defaultRequestParameters must not leak into a
        // request that opts in fresh with previousMessageId == null (a meaningful first-turn value).
        AnthropicChatModel model = modelBuilder()
                .defaultRequestParameters(AnthropicChatRequestParameters.builder()
                        .previousMessageId("msg_stale_default")
                        .build())
                .build();

        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("Hi"))
                .parameters(AnthropicChatRequestParameters.builder()
                        .returnCacheDiagnostics(true)
                        .previousMessageId(null)
                        .build())
                .build();

        model.chat(request);

        String body = lastRequestBody();
        assertThat(body).contains("\"previous_message_id\" : null");
        assertThat(body).doesNotContain("msg_stale_default");
    }

    @Test
    void should_not_pair_unrelated_default_previous_message_id_with_model_level_diagnostics_toggle() {
        // Regression test: a previousMessageId carried on an unrelated defaultRequestParameters() object
        // must not leak onto the wire just because returnCacheDiagnostics(true) is toggled on at the model
        // level -- previousMessageId is per-request only and is never sourced from a model-level default.
        AnthropicChatModel model = modelBuilder()
                .returnCacheDiagnostics(true)
                .defaultRequestParameters(AnthropicChatRequestParameters.builder()
                        .previousMessageId("msg_unrelated")
                        .build())
                .build();

        model.chat(ChatRequest.builder().messages(UserMessage.from("Hi")).build());

        String body = lastRequestBody();
        assertThat(body).contains("\"diagnostics\"");
        assertThat(body).contains("\"previous_message_id\" : null");
        assertThat(body).doesNotContain("msg_unrelated");
    }

    @Test
    void should_send_per_request_previous_message_id_when_diagnostics_enabled_at_model_level() {
        // The ergonomic path: enable diagnostics once on the model, then only vary previousMessageId
        // per request (without re-stating returnCacheDiagnostics every turn). The per-request id must
        // reach the wire instead of being silently dropped in favour of a null previous_message_id.
        AnthropicChatModel model = modelBuilder().returnCacheDiagnostics(true).build();

        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("Hi"))
                .parameters(AnthropicChatRequestParameters.builder()
                        .previousMessageId("msg_previous")
                        .build())
                .build();

        model.chat(request);

        String body = lastRequestBody();
        assertThat(body).contains("\"diagnostics\"");
        assertThat(body).contains("\"previous_message_id\" : \"msg_previous\"");
    }

    private static ToolSpecification weatherTool() {
        return ToolSpecification.builder()
                .name("get_weather")
                .description("Returns the current weather for a given city")
                .build();
    }

    private AnthropicChatModel.AnthropicChatModelBuilder modelBuilder() {
        return AnthropicChatModel.builder()
                .baseUrl("http://localhost:" + wireMockServer.port() + "/v1")
                .apiKey("test-key")
                .modelName("claude-3-5-haiku-20241022");
    }

    private String lastRequestBody() {
        List<LoggedRequest> requests = wireMockServer.findAll(postRequestedFor(urlPathEqualTo("/v1/messages")));
        assertThat(requests).hasSize(1);
        return requests.get(0).getBodyAsString();
    }
}
