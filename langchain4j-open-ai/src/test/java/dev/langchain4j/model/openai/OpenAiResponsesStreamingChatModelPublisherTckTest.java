package dev.langchain4j.model.openai;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static dev.langchain4j.reactive.streaming.ReactiveStreamingTestSupport.TCK_PUBLISHER_REFERENCE_CLEANUP_TIMEOUT_MILLIS;
import static dev.langchain4j.reactive.streaming.ReactiveStreamingTestSupport.tckTestEnvironment;
import static org.reactivestreams.FlowAdapters.toPublisher;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatModelStreamingEvent;
import org.reactivestreams.Publisher;
import org.reactivestreams.tck.PublisherVerification;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;

/**
 * Reactive Streams TCK for the full {@link OpenAiResponsesStreamingChatModel#chat(ChatRequest)} pipeline:
 * {@code JdkHttpClient.HttpStreamingEventPublisher -> OpenAiResponsesClient.streamingChatPublisher -> downstream}.
 * The Responses API has its own event vocabulary and its own client, so it needs its own verification rather than
 * riding on {@link OpenAiStreamingChatModelPublisherTckTest}, which covers the Chat Completions pipeline.
 * <p>
 * WireMock serves deterministic Responses-style SSE, parameterized by the request path: a request to
 * {@code /sse/{n}/v1} streams {@code n - 1} {@code response.output_text.delta} events followed by one
 * {@code response.completed}. Only event types the client maps are stubbed, so no
 * {@link dev.langchain4j.model.chat.response.RawStreamingEvent}s are produced and the element count is exact.
 */
public class OpenAiResponsesStreamingChatModelPublisherTckTest extends PublisherVerification<ChatModelStreamingEvent> {

    private static final long MAX_ELEMENTS = 100L;

    private static final String RESPONSES_PATH = "/v1/responses";
    private static final String FAIL_PATH = "/fail/v1/responses";

    private static WireMockServer wireMockServer;

    public OpenAiResponsesStreamingChatModelPublisherTckTest() {
        super(tckTestEnvironment(), TCK_PUBLISHER_REFERENCE_CLEANUP_TIMEOUT_MILLIS);
    }

    @BeforeClass
    public static void startServer() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();

        wireMockServer.stubFor(post(FAIL_PATH)
                .willReturn(aResponse().withStatus(500).withBody("{\"error\":{\"message\":\"boom\"}}")));
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
    public Publisher<ChatModelStreamingEvent> createPublisher(long elements) {
        // The publisher emits N PartialResponse events followed by 1 aggregated ChatResponse, so to produce
        // exactly `elements` items, stub (elements - 1) text deltas plus the terminal response.completed.
        long textDeltas = Math.max(0, elements - 1);
        wireMockServer.stubFor(post("/sse/" + elements + RESPONSES_PATH)
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/event-stream")
                        .withBody(responsesStreamBody(textDeltas))));

        return newPublisher("http://localhost:" + wireMockServer.port() + "/sse/" + elements + "/v1");
    }

    @Override
    public Publisher<ChatModelStreamingEvent> createFailedPublisher() {
        return newPublisher("http://localhost:" + wireMockServer.port() + "/fail/v1");
    }

    private static Publisher<ChatModelStreamingEvent> newPublisher(String baseUrl) {
        OpenAiResponsesStreamingChatModel model = OpenAiResponsesStreamingChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey("test-key")
                .modelName("gpt-5-mini")
                .build();

        ChatRequest request = ChatRequest.builder().messages(UserMessage.from("hi")).build();

        return toPublisher(model.chat(request));
    }

    private static String responsesStreamBody(long textDeltas) {
        StringBuilder sb = new StringBuilder();
        for (long i = 0; i < textDeltas; i++) {
            sb.append("data: {\"type\":\"response.output_text.delta\",\"delta\":\"chunk-")
                    .append(i)
                    .append("\"}\n\n");
        }
        sb.append("data: {\"type\":\"response.completed\",\"response\":{\"id\":\"resp_1\",")
                .append("\"model\":\"gpt-5-mini\",\"status\":\"completed\",\"output\":[]}}\n\n");
        return sb.toString();
    }
}
