package dev.langchain4j.model.openai;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.StreamingEvent;
import org.reactivestreams.Publisher;
import org.reactivestreams.tck.PublisherVerification;
import org.reactivestreams.tck.TestEnvironment;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static org.reactivestreams.FlowAdapters.toPublisher;

/**
 * Reactive Streams TCK for the full {@link OpenAiStreamingChatModel#chat(ChatRequest)} pipeline:
 * {@code JdkHttpClient.StreamingHttpEventPublisher -> DefaultOpenAiClient.chatCompletionPublisher -> downstream}.
 * Uses WireMock to emit deterministic OpenAI-style SSE responses.
 */
public class OpenAiStreamingChatModelTckTest extends PublisherVerification<StreamingEvent> {

    private static final long DEFAULT_TIMEOUT_MILLIS = 2_000L;
    private static final long DEFAULT_NO_SIGNALS_TIMEOUT_MILLIS = DEFAULT_TIMEOUT_MILLIS;
    private static final long DEFAULT_POLL_TIMEOUT_MILLIS = 50L;
    private static final long PUBLISHER_REFERENCE_CLEANUP_TIMEOUT_MILLIS = 300L;

    private static final long MAX_ELEMENTS = 100L;

    private static final String CHAT_COMPLETIONS_PATH = "/v1/chat/completions";
    private static final String FAIL_PATH = "/fail/v1/chat/completions";

    private static WireMockServer wireMockServer;

    public OpenAiStreamingChatModelTckTest() {
        super(
                new TestEnvironment(DEFAULT_TIMEOUT_MILLIS, DEFAULT_NO_SIGNALS_TIMEOUT_MILLIS, DEFAULT_POLL_TIMEOUT_MILLIS),
                PUBLISHER_REFERENCE_CLEANUP_TIMEOUT_MILLIS);
    }

    @BeforeClass
    public static void startServer() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();

        wireMockServer.stubFor(post(FAIL_PATH).willReturn(aResponse()
                .withStatus(500)
                .withBody("{\"error\":{\"message\":\"boom\"}}")));
    }

    @AfterClass
    public static void stopServer() {
        if (wireMockServer != null) {
            wireMockServer.stop();
            wireMockServer = null;
        }
    }

    @Override
    public long maxElementsFromPublisher() {
        return MAX_ELEMENTS;
    }

    @Override
    public Publisher<StreamingEvent> createPublisher(long elements) {
        // The publisher emits N PartialResponse events followed by 1 aggregated ChatResponse.
        // So to produce exactly `elements` items, stub (elements - 1) content chunks + [DONE].
        long contentChunks = Math.max(0, elements - 1);
        String path = "/sse/" + elements + CHAT_COMPLETIONS_PATH;
        wireMockServer.stubFor(post(path).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "text/event-stream")
                .withBody(openAiStreamBody(contentChunks))));

        String baseUrl = "http://localhost:" + wireMockServer.port() + "/sse/" + elements + "/v1";
        return newPublisher(baseUrl);
    }

    @Override
    public Publisher<StreamingEvent> createFailedPublisher() {
        String baseUrl = "http://localhost:" + wireMockServer.port() + "/fail/v1";
        return newPublisher(baseUrl);
    }

    private Publisher<StreamingEvent> newPublisher(String baseUrl) {
        OpenAiStreamingChatModel model = OpenAiStreamingChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey("test-key")
                .modelName("gpt-4o-mini")
                .build();

        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("hi"))
                .build();

        return toPublisher(model.chat(request));
    }

    private static String openAiStreamBody(long contentChunks) {
        StringBuilder sb = new StringBuilder();
        for (long i = 0; i < contentChunks; i++) {
            sb.append("data: ")
                    .append(contentChunk("chunk-" + i))
                    .append("\n\n");
        }
        sb.append("data: [DONE]\n\n");
        return sb.toString();
    }

    private static String contentChunk(String content) {
        return "{\"id\":\"x\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"gpt-4o-mini\","
                + "\"choices\":[{\"index\":0,\"delta\":{\"content\":\"" + content + "\"},\"finish_reason\":null}]}";
    }
}
