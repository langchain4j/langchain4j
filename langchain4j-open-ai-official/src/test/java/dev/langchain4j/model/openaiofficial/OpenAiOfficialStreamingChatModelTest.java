package dev.langchain4j.model.openaiofficial;

import static org.assertj.core.api.Assertions.assertThat;

import com.openai.client.OpenAIClientAsync;
import com.openai.client.OpenAIClientAsyncImpl;
import com.openai.core.ClientOptions;
import com.openai.core.RequestOptions;
import com.openai.core.http.Headers;
import com.openai.core.http.HttpClient;
import com.openai.core.http.HttpRequest;
import com.openai.core.http.HttpResponse;
import dev.langchain4j.exception.ContentFilteredException;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class OpenAiOfficialStreamingChatModelTest {

    @Test
    void should_raise_content_filtered_exception_when_stream_contains_refusal() throws Exception {
        String stream = sse(
                chunk("{\"role\":\"assistant\",\"refusal\":\"I cannot\"}", null),
                chunk("{\"refusal\":\" help with that.\"}", null),
                chunk("{}", "\"stop\""));

        RecordingHandler handler = streamWith(stream);

        assertThat(handler.error.get()).isInstanceOf(ContentFilteredException.class);
        assertThat(handler.error.get()).hasMessage("I cannot help with that.");
        assertThat(handler.response.get()).isNull();
    }

    @Test
    void should_complete_normally_when_stream_contains_no_refusal() throws Exception {
        String stream = sse(chunk("{\"role\":\"assistant\",\"content\":\"Paris\"}", null), chunk("{}", "\"stop\""));

        RecordingHandler handler = streamWith(stream);

        assertThat(handler.error.get()).isNull();
        assertThat(handler.response.get().aiMessage().text()).isEqualTo("Paris");
    }

    private static RecordingHandler streamWith(String sseBody) throws Exception {
        OpenAIClientAsync client = new OpenAIClientAsyncImpl(ClientOptions.builder()
                .apiKey("test-key")
                .httpClient(new CannedHttpClient(sseBody))
                .build());

        OpenAiOfficialStreamingChatModel model = OpenAiOfficialStreamingChatModel.builder()
                .openAIClientAsync(client)
                .modelName("gpt-4o-mini")
                .build();

        RecordingHandler handler = new RecordingHandler();
        model.chat("Hello", handler);
        assertThat(handler.done.await(30, TimeUnit.SECONDS)).isTrue();
        return handler;
    }

    private static String chunk(String delta, String finishReason) {
        return "{\"id\":\"chatcmpl-test\",\"object\":\"chat.completion.chunk\",\"created\":1730000000,"
                + "\"model\":\"gpt-4o-mini\",\"choices\":[{\"index\":0,\"delta\":" + delta
                + ",\"finish_reason\":" + (finishReason == null ? "null" : finishReason) + "}]}";
    }

    private static String sse(String... chunks) {
        StringBuilder builder = new StringBuilder();
        for (String chunk : chunks) {
            builder.append("data: ").append(chunk).append("\n\n");
        }
        return builder.append("data: [DONE]\n\n").toString();
    }

    private static class RecordingHandler implements StreamingChatResponseHandler {

        private final CountDownLatch done = new CountDownLatch(1);
        private final AtomicReference<ChatResponse> response = new AtomicReference<>();
        private final AtomicReference<Throwable> error = new AtomicReference<>();

        @Override
        public void onPartialResponse(String partialResponse) {}

        @Override
        public void onCompleteResponse(ChatResponse completeResponse) {
            response.set(completeResponse);
            done.countDown();
        }

        @Override
        public void onError(Throwable throwable) {
            error.set(throwable);
            done.countDown();
        }
    }

    private static class CannedHttpClient implements HttpClient {

        private final String sseBody;

        CannedHttpClient(String sseBody) {
            this.sseBody = sseBody;
        }

        @Override
        public HttpResponse execute(HttpRequest request, RequestOptions requestOptions) {
            return new CannedHttpResponse(sseBody);
        }

        @Override
        public CompletableFuture<HttpResponse> executeAsync(HttpRequest request, RequestOptions requestOptions) {
            return CompletableFuture.completedFuture(execute(request, requestOptions));
        }

        @Override
        public void close() {}
    }

    private static class CannedHttpResponse implements HttpResponse {

        private final String sseBody;

        CannedHttpResponse(String sseBody) {
            this.sseBody = sseBody;
        }

        @Override
        public int statusCode() {
            return 200;
        }

        @Override
        public Headers headers() {
            return Headers.builder().put("Content-Type", "text/event-stream").build();
        }

        @Override
        public InputStream body() {
            return new ByteArrayInputStream(sseBody.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public void close() {}
    }
}
