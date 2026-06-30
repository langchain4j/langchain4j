package dev.langchain4j.model.anthropic;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AnthropicMidConversationSystemMessagesTest {

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
                                  "model": "claude-opus-4-8",
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
    void should_send_mid_conversation_system_message_inline_when_enabled() {
        AnthropicChatModel model = modelBuilder().midConversationSystemMessages(true).build();

        model.chat(ChatRequest.builder()
                .messages(
                        SystemMessage.from("Leading instruction."),
                        UserMessage.from("Hi"),
                        SystemMessage.from("From now on, be concise."))
                .build());

        String body = lastRequestBody();
        // the mid-conversation system message is emitted as a role:"system" entry inside the messages array
        // (whitespace is stripped because the client pretty-prints the request body)
        assertThat(body.replaceAll("\\s+", "")).contains("\"role\":\"system\"");
        assertThat(body).contains("From now on, be concise.");
        // the leading system message still populates the top-level system field
        assertThat(body).contains("Leading instruction.");
    }

    @Test
    void should_not_mid_conversation_system_messages_by_default() {
        AnthropicChatModel model = modelBuilder().build();

        model.chat(ChatRequest.builder()
                .messages(
                        SystemMessage.from("Leading instruction."),
                        UserMessage.from("Hi"),
                        SystemMessage.from("From now on, be concise."))
                .build());

        String body = lastRequestBody();
        // both system messages go to the top-level system field; none are inlined as role:"system"
        assertThat(body.replaceAll("\\s+", "")).doesNotContain("\"role\":\"system\"");
        assertThat(body).contains("Leading instruction.").contains("From now on, be concise.");
    }

    @Test
    void should_mid_conversation_system_messages_per_request_when_model_default_is_disabled() {
        AnthropicChatModel model = modelBuilder().build();

        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("Hi"), SystemMessage.from("From now on, be concise."))
                .parameters(AnthropicChatRequestParameters.builder()
                        .midConversationSystemMessages(true)
                        .build())
                .build();

        model.chat(request);

        String body = lastRequestBody();
        assertThat(body.replaceAll("\\s+", "")).contains("\"role\":\"system\"");
        assertThat(body).contains("From now on, be concise.");
    }

    private AnthropicChatModel.AnthropicChatModelBuilder modelBuilder() {
        return AnthropicChatModel.builder()
                .baseUrl("http://localhost:" + wireMockServer.port() + "/v1")
                .apiKey("test-key")
                .modelName("claude-opus-4-8");
    }

    private String lastRequestBody() {
        List<LoggedRequest> requests = wireMockServer.findAll(postRequestedFor(urlPathEqualTo("/v1/messages")));
        assertThat(requests).hasSize(1);
        return requests.get(0).getBodyAsString();
    }
}
