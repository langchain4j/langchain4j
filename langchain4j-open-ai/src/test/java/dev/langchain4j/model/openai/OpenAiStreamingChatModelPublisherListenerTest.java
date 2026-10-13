package dev.langchain4j.model.openai;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelPublisherListenerTest;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/**
 * Drives the common {@link AbstractStreamingChatModelPublisherListenerTest} for the OpenAI provider, replaying
 * deterministic OpenAI-style SSE via WireMock (no network) for {@link OpenAiStreamingChatModel#chat(ChatRequest)}.
 */
class OpenAiStreamingChatModelPublisherListenerTest extends AbstractStreamingChatModelPublisherListenerTest {

    private static final String PATH = "/v1/chat/completions";

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
                    .withBody("{\"error\":{\"message\":\"boom\"}}")));
        } else {
            ResponseDefinitionBuilder response = aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/event-stream")
                    .withBody(openAiStreamBody(scenario.contentChunks()));
            if (scenario.spreadOverMillis() > 0) {
                response = response.withChunkedDribbleDelay(
                        scenario.contentChunks() + 1, (int) scenario.spreadOverMillis());
            }
            wireMockServer.stubFor(post(urlEqualTo(PATH)).willReturn(response));
        }

        return OpenAiStreamingChatModel.builder()
                .baseUrl("http://localhost:" + wireMockServer.port() + "/v1")
                .apiKey("test-key")
                .modelName("gpt-4o-mini")
                .listeners(List.of(listener))
                .build();
    }

    private static String openAiStreamBody(int contentChunks) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < contentChunks; i++) {
            sb.append("data: ").append(contentChunk("chunk-" + i)).append("\n\n");
        }
        sb.append("data: [DONE]\n\n");
        return sb.toString();
    }

    private static String contentChunk(String content) {
        return "{\"id\":\"x\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"gpt-4o-mini\","
                + "\"choices\":[{\"index\":0,\"delta\":{\"content\":\"" + content + "\"},\"finish_reason\":null}]}";
    }
}
