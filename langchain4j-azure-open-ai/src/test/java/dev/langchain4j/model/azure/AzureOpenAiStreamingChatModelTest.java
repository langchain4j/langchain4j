package dev.langchain4j.model.azure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.azure.ai.openai.OpenAIAsyncClient;
import com.azure.ai.openai.models.ChatCompletions;
import com.azure.json.JsonProviders;
import com.azure.json.JsonReader;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialResponseContext;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.chat.response.StreamingHandle;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

class AzureOpenAiStreamingChatModelTest {

    @Test
    void should_pass_streaming_handle_to_partial_response_emitted_during_subscribe() throws Exception {
        // Flux.just(...) emits all chunks while subscribe(...) is still running
        AzureOpenAiStreamingChatModel model =
                modelStreaming(chunk("{\"role\":\"assistant\",\"content\":\"Paris\"}", null), chunk("{}", "stop"));
        RecordingHandler handler = new RecordingHandler(false);

        model.chat("Hello", handler);

        assertThat(handler.done.await(30, TimeUnit.SECONDS)).isTrue();
        assertThat(handler.error.get()).isNull();
        assertThat(handler.streamingHandles).hasSize(1).doesNotContainNull();
        assertThat(handler.response.get().aiMessage().text()).isEqualTo("Paris");
    }

    @Test
    void should_cancel_from_partial_response_emitted_during_subscribe() throws Exception {
        // Flux.just(...) emits all chunks while subscribe(...) is still running,
        // so the whole stream is handled before chat(...) returns
        AzureOpenAiStreamingChatModel model = modelStreaming(
                chunk("{\"role\":\"assistant\",\"content\":\"Paris\"}", null),
                chunk("{\"content\":\" is the capital\"}", null),
                chunk("{}", "stop"));
        RecordingHandler handler = new RecordingHandler(true);

        model.chat("Hello", handler);

        assertThat(handler.streamingHandles).hasSize(1);
        assertThat(handler.streamingHandles.get(0).isCancelled()).isTrue();
        assertThat(handler.error.get()).isNull();
        assertThat(handler.response.get()).isNull();
    }

    private static AzureOpenAiStreamingChatModel modelStreaming(ChatCompletions... chunks) {
        OpenAIAsyncClient client = mock(OpenAIAsyncClient.class);
        when(client.getChatCompletionsStream(any(), any())).thenReturn(Flux.just(chunks));

        return AzureOpenAiStreamingChatModel.builder()
                .openAIAsyncClient(client)
                .deploymentName("gpt-4o-mini")
                .build();
    }

    private static ChatCompletions chunk(String delta, String finishReason) throws IOException {
        String json = "{\"id\":\"chatcmpl-test\",\"created\":1730000000,\"model\":\"gpt-4o-mini\","
                + "\"choices\":[{\"index\":0,\"delta\":" + delta
                + ",\"finish_reason\":" + (finishReason == null ? "null" : "\"" + finishReason + "\"") + "}]}";
        try (JsonReader jsonReader = JsonProviders.createReader(json)) {
            return ChatCompletions.fromJson(jsonReader);
        }
    }

    private static class RecordingHandler implements StreamingChatResponseHandler {

        private final boolean cancelOnPartialResponse;
        private final CountDownLatch done = new CountDownLatch(1);
        private final List<StreamingHandle> streamingHandles = new CopyOnWriteArrayList<>();
        private final AtomicReference<ChatResponse> response = new AtomicReference<>();
        private final AtomicReference<Throwable> error = new AtomicReference<>();

        RecordingHandler(boolean cancelOnPartialResponse) {
            this.cancelOnPartialResponse = cancelOnPartialResponse;
        }

        @Override
        public void onPartialResponse(PartialResponse partialResponse, PartialResponseContext context) {
            streamingHandles.add(context.streamingHandle());
            if (cancelOnPartialResponse) {
                context.streamingHandle().cancel();
            }
        }

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
}
