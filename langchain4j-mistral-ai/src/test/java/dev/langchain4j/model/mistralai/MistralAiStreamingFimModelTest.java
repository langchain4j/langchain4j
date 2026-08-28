package dev.langchain4j.model.mistralai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.model.chat.TestStreamingResponseHandler;
import dev.langchain4j.model.output.Response;
import java.util.List;
import org.junit.jupiter.api.Test;

class MistralAiStreamingFimModelTest {

    private static final String MODEL = "codestral-latest";

    @Test
    void should_stream_text_and_complete() {
        // given
        List<ServerSentEvent> events =
                List.of(textEvent("def add(a, b):"), textEvent(" return a + b"), finishEvent("stop"), doneEvent());

        MistralAiStreamingFimModel model = createModel(events);

        // when
        Response<String> response = generate(model, "def add");

        // then
        assertThat(response.content()).isEqualTo("def add(a, b): return a + b");
    }

    @Test
    void should_not_fail_on_delta_without_content() {
        // given - a finish_reason-only chunk has an empty delta (no "content" field)
        List<ServerSentEvent> events = List.of(textEvent("return 42"), finishEvent("stop"), doneEvent());

        MistralAiStreamingFimModel model = createModel(events);

        // when / then - must not throw NullPointerException on the content-less delta
        assertThatCode(() -> {
                    Response<String> response = generate(model, "def answer");
                    assertThat(response.content()).isEqualTo("return 42");
                })
                .doesNotThrowAnyException();
    }

    // -- Helper methods --

    private static MistralAiStreamingFimModel createModel(List<ServerSentEvent> events) {
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(events);
        return MistralAiStreamingFimModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-api-key")
                .modelName(MODEL)
                .build();
    }

    private static Response<String> generate(MistralAiStreamingFimModel model, String prompt) {
        var handler = new TestStreamingResponseHandler<String>();
        model.generate(prompt, handler);
        return handler.get();
    }

    private static ServerSentEvent textEvent(String text) {
        String escaped = text.replace("\"", "\\\"");
        return event("""
                {"id":"abc123","model":"%s","choices":[{"index":0,"delta":{"content":[{"type":"text","text":"%s"}]}}]}""".formatted(MODEL, escaped));
    }

    private static ServerSentEvent finishEvent(String reason) {
        return event("""
                {"id":"abc123","model":"%s","choices":[{"index":0,"delta":{},"finish_reason":"%s"}],"usage":{"prompt_tokens":10,"completion_tokens":20,"total_tokens":30}}""".formatted(MODEL, reason));
    }

    private static ServerSentEvent doneEvent() {
        return new ServerSentEvent(null, "[DONE]");
    }

    private static ServerSentEvent event(String data) {
        return new ServerSentEvent(null, data);
    }
}
