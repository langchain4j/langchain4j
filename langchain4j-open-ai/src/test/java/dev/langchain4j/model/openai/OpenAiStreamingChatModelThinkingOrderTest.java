package dev.langchain4j.model.openai;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatModelStreamingEvent;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class OpenAiStreamingChatModelThinkingOrderTest {

    private static final String REASONING_CHUNK = chunk("\"reasoning_content\":\"First \"");
    private static final String MIXED_CHUNK = chunk("\"content\":\"Hello\",\"reasoning_content\":\"then \"");
    private static final String CONTENT_CHUNK = chunk("\"content\":\" world\"");

    @Test
    void should_dispatch_reasoning_before_content_to_handler_in_mixed_chunk() throws Exception {
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(List.of(
                new ServerSentEvent(null, REASONING_CHUNK),
                new ServerSentEvent(null, MIXED_CHUNK),
                new ServerSentEvent(null, CONTENT_CHUNK),
                new ServerSentEvent(null, "[DONE]")));
        OpenAiStreamingChatModel model = OpenAiStreamingChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(httpClient))
                .apiKey("test-key")
                .modelName("gpt-4o-mini")
                .returnThinking(true)
                .build();
        List<String> callbacks = new ArrayList<>();
        CompletableFuture<ChatResponse> completion = new CompletableFuture<>();

        model.chat("Hi", new StreamingChatResponseHandler() {
            @Override
            public void onPartialThinking(PartialThinking partialThinking) {
                callbacks.add("thinking:" + partialThinking.text());
            }

            @Override
            public void onPartialResponse(String partialResponse) {
                callbacks.add("response:" + partialResponse);
            }

            @Override
            public void onCompleteResponse(ChatResponse response) {
                completion.complete(response);
            }

            @Override
            public void onError(Throwable error) {
                completion.completeExceptionally(error);
            }
        });

        completion.get(5, TimeUnit.SECONDS);
        assertThat(callbacks).containsExactly("thinking:First ", "thinking:then ", "response:Hello", "response: world");
    }

    @Test
    void should_publish_reasoning_before_content_in_mixed_chunk() throws Exception {
        WireMockServer server =
                new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
        try {
            server.stubFor(post(urlEqualTo("/v1/chat/completions"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "text/event-stream")
                            .withBody("data: " + REASONING_CHUNK + "\n\n"
                                    + "data: " + MIXED_CHUNK + "\n\n"
                                    + "data: " + CONTENT_CHUNK + "\n\n"
                                    + "data: [DONE]\n\n")));

            OpenAiStreamingChatModel model = OpenAiStreamingChatModel.builder()
                    .baseUrl("http://localhost:" + server.port() + "/v1")
                    .apiKey("test-key")
                    .modelName("gpt-4o-mini")
                    .returnThinking(true)
                    .build();
            ChatRequest request =
                    ChatRequest.builder().messages(UserMessage.from("Hi")).build();
            List<ChatModelStreamingEvent> events = new ArrayList<>();
            CompletableFuture<List<ChatModelStreamingEvent>> completion = new CompletableFuture<>();

            model.chat(request).subscribe(new Flow.Subscriber<>() {
                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    subscription.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(ChatModelStreamingEvent event) {
                    events.add(event);
                }

                @Override
                public void onError(Throwable error) {
                    completion.completeExceptionally(error);
                }

                @Override
                public void onComplete() {
                    completion.complete(List.copyOf(events));
                }
            });

            assertThat(completion.get(5, TimeUnit.SECONDS).stream()
                            .filter(event -> event instanceof PartialThinking || event instanceof PartialResponse)
                            .toList())
                    .containsExactly(
                            new PartialThinking("First "),
                            new PartialThinking("then "),
                            new PartialResponse("Hello"),
                            new PartialResponse(" world"));
        } finally {
            server.stop();
        }
    }

    private static String chunk(String deltaFields) {
        return "{\"id\":\"x\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"gpt-4o-mini\","
                + "\"choices\":[{\"index\":0,\"delta\":{" + deltaFields + "},\"finish_reason\":null}]}";
    }
}
