package dev.langchain4j.model.anthropic.common;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import dev.langchain4j.model.anthropic.AnthropicChatModelName;
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelPublisherListenerTest;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/**
 * Drives the common {@link AbstractStreamingChatModelPublisherListenerTest} for the Anthropic provider, replaying
 * deterministic Anthropic-style SSE via WireMock (no network) for
 * {@link AnthropicStreamingChatModel#chat(ChatRequest)}.
 */
class AnthropicStreamingChatModelPublisherListenerTest extends AbstractStreamingChatModelPublisherListenerTest {

    private static final String PATH = "/v1/messages";

    private WireMockServer wireMockServer;

    @BeforeEach
    void startServer() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();
    }

    @AfterEach
    void stopServer() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @Override
    protected StreamingChatModel createModel(ChatModelListener listener, StreamScenario scenario) {
        if (scenario.fail()) {
            wireMockServer.stubFor(post(urlEqualTo(PATH)).willReturn(aResponse()
                    .withStatus(500)
                    .withBody("{\"type\":\"error\",\"error\":{\"type\":\"api_error\",\"message\":\"boom\"}}")));
        } else {
            ResponseDefinitionBuilder response = aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/event-stream")
                    .withBody(anthropicStreamBody(scenario.contentChunks()));
            if (scenario.spreadOverMillis() > 0) {
                // 5 framing events (message_start, content_block_start/stop, message_delta/stop) + N deltas.
                response = response.withChunkedDribbleDelay(
                        scenario.contentChunks() + 5, (int) scenario.spreadOverMillis());
            }
            wireMockServer.stubFor(post(urlEqualTo(PATH)).willReturn(response));
        }

        return AnthropicStreamingChatModel.builder()
                .baseUrl("http://localhost:" + wireMockServer.port() + "/v1")
                .apiKey("test-key")
                .modelName(AnthropicChatModelName.CLAUDE_HAIKU_4_5_20251001)
                .maxTokens(100)
                .listeners(listener)
                .build();
    }

    private static String anthropicStreamBody(int contentChunks) {
        StringBuilder sb = new StringBuilder();
        sb.append("event: message_start\n")
                .append("data: {\"type\":\"message_start\",\"message\":{\"id\":\"msg_1\",\"type\":\"message\","
                        + "\"role\":\"assistant\",\"content\":[],\"model\":\"claude-haiku-4-5-20251001\","
                        + "\"stop_reason\":null,\"usage\":{\"input_tokens\":5,\"output_tokens\":0}}}\n\n");
        sb.append("event: content_block_start\n")
                .append("data: {\"type\":\"content_block_start\",\"index\":0,"
                        + "\"content_block\":{\"type\":\"text\",\"text\":\"\"}}\n\n");
        for (int i = 0; i < contentChunks; i++) {
            sb.append("event: content_block_delta\n")
                    .append("data: {\"type\":\"content_block_delta\",\"index\":0,"
                            + "\"delta\":{\"type\":\"text_delta\",\"text\":\"chunk-" + i + " \"}}\n\n");
        }
        sb.append("event: content_block_stop\n").append("data: {\"type\":\"content_block_stop\",\"index\":0}\n\n");
        sb.append("event: message_delta\n")
                .append("data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"},"
                        + "\"usage\":{\"output_tokens\":" + contentChunks + "}}\n\n");
        sb.append("event: message_stop\n").append("data: {\"type\":\"message_stop\"}\n\n");
        return sb.toString();
    }
}
