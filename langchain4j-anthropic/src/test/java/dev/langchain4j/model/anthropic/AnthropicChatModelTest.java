package dev.langchain4j.model.anthropic;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AnthropicChatModelTest {

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
                          "model": "claude-3-5-sonnet-20241022",
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
    void should_use_model_defaults_when_request_parameters_absent() {
        // given
        ChatModel model = AnthropicChatModel.builder()
                .baseUrl("http://localhost:" + wireMockServer.port() + "/v1")
                .apiKey("test-key")
                .modelName("claude-3-5-sonnet-20241022")
                .cacheSystemMessages(true)
                .cacheTools(true)
                .returnThinking(true)
                .build();

        ChatRequest request =
                ChatRequest.builder().messages(UserMessage.from("What is 2+2?")).build();

        // when
        ChatResponse response = model.chat(request);

        // then
        assertThat(response).isNotNull();
        assertThat(response.aiMessage().text()).isEqualTo("Hello!");
    }

    @Test
    void should_override_model_settings_with_request_parameters() {
        // given
        ChatModel model = AnthropicChatModel.builder()
                .baseUrl("http://localhost:" + wireMockServer.port() + "/v1")
                .apiKey("test-key")
                .modelName("claude-3-5-sonnet-20241022")
                .cacheSystemMessages(false)
                .build();

        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("What is 2+2?"))
                .parameters(AnthropicChatRequestParameters.builder()
                        .cacheSystemMessages(true)
                        .build())
                .build();

        // when
        ChatResponse response = model.chat(request);

        // then
        assertThat(response).isNotNull();
    }

    @Test
    void should_handle_non_anthropic_parameters_gracefully() {
        // given
        ChatModel model = AnthropicChatModel.builder()
                .baseUrl("http://localhost:" + wireMockServer.port() + "/v1")
                .apiKey("test-key")
                .modelName("claude-3-5-sonnet-20241022")
                .build();

        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("What is 2+2?"))
                .parameters(DefaultChatRequestParameters.EMPTY)
                .build();

        // when
        ChatResponse response = model.chat(request);

        // then
        assertThat(response).isNotNull();
    }
}
