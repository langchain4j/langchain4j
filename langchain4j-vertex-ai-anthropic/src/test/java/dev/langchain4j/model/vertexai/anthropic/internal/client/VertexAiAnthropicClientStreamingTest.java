package dev.langchain4j.model.vertexai.anthropic.internal.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import dev.langchain4j.model.vertexai.anthropic.internal.api.AnthropicRequest;
import dev.langchain4j.model.vertexai.anthropic.internal.api.AnthropicResponse;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class VertexAiAnthropicClientStreamingTest {

    @Test
    void should_not_stream_thinking_blocks() throws Exception {
        AnthropicResponse response = VertexAiAnthropicJsonUtils.fromJson("""
                {"id":"msg_1","type":"message","role":"assistant","model":"claude-opus-5-5",
                 "content":[
                   {"type":"thinking","thinking":"secret reasoning","signature":"sig_1"},
                   {"type":"redacted_thinking","data":"encrypted"},
                   {"type":"text","text":"Hello"}],
                 "stop_reason":"end_turn"}""", AnthropicResponse.class);

        List<String> chunks = new ArrayList<>();
        try (CannedResponseClient client = new CannedResponseClient(response)) {
            client.generateContentStreaming(new AnthropicRequest(), "claude-opus-5-5", new StreamingResponseHandler() {
                @Override
                public void onChunk(String jsonChunk) {
                    chunks.add(jsonChunk);
                }

                @Override
                public void onComplete() {}

                @Override
                public void onError(Throwable error) {
                    throw new AssertionError(error);
                }
            });
        }

        assertThat(chunks)
                .isNotEmpty()
                .allSatisfy(chunk -> assertThat(chunk)
                        .contains("\"text_delta\"")
                        .doesNotContain("secret reasoning")
                        .doesNotContain("sig_1")
                        .doesNotContain("encrypted"));
        assertThat(String.join("", chunks)).contains("Hello");
    }

    private static class CannedResponseClient extends VertexAiAnthropicClient implements AutoCloseable {

        private final AnthropicResponse response;

        CannedResponseClient(AnthropicResponse response) {
            super("project", "us-east5", "claude-opus-5-5", GoogleCredentials.create(new AccessToken("token", null)));
            this.response = response;
        }

        @Override
        public AnthropicResponse generateContent(AnthropicRequest request, String modelName) {
            return response;
        }
    }
}
