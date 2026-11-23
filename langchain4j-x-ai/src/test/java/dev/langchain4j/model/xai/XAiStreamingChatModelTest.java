package dev.langchain4j.model.xai;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.HttpResponseReceived;
import dev.langchain4j.http.client.sse.HttpStreamingEvent;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import dev.langchain4j.internal.Json;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatModelStreamingEvent;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.Flow.Publisher;
import mutiny.zero.ZeroPublisher;
import org.junit.jupiter.api.Test;

class XAiStreamingChatModelTest {

    private static final List<ServerSentEvent> TEXT_EVENTS =
            List.of(event("""
                    {"type":"response.created","response":{"id":"resp_1","status":"in_progress"}}"""), event("""
                    {"type":"response.reasoning_summary_text.delta","delta":"Thinking"}"""), event("""
                    {"type":"response.output_text.delta","delta":"Hel"}"""), event("""
                    {"type":"response.output_text.delta","delta":"lo!"}"""), event("""
                    {"type":"response.completed","response":{
                      "id":"resp_1","model":"grok-4.7","status":"completed","created_at":1761200000,
                      "output":[
                        {"type":"reasoning","summary":[{"type":"summary_text","text":"Thinking"}],"encrypted_content":"ENC"},
                        {"type":"message","role":"assistant","content":[{"type":"output_text","text":"Hello!"}]}
                      ],
                      "usage":{"input_tokens":10,"output_tokens":5,"total_tokens":15}}}"""), new ServerSentEvent(null, "[DONE]"));

    private static final List<ServerSentEvent> TOOL_CALL_EVENTS =
            List.of(event("""
                    {"type":"response.output_item.added","output_index":0,
                     "item":{"type":"function_call","id":"fc_1","call_id":"call_1","name":"getWeather","arguments":""}}"""), event("""
                    {"type":"response.function_call_arguments.delta","item_id":"fc_1","output_index":0,"delta":"{\\"city\\":"}"""), event("""
                    {"type":"response.function_call_arguments.delta","item_id":"fc_1","output_index":0,"delta":"\\"Paris\\"}"}"""), event("""
                    {"type":"response.output_item.done","output_index":0,
                     "item":{"type":"function_call","id":"fc_1","call_id":"call_1","name":"getWeather","arguments":"{\\"city\\":\\"Paris\\"}"}}"""), event("""
                    {"type":"response.completed","response":{
                      "id":"resp_2","model":"grok-4.7","status":"completed",
                      "output":[{"type":"function_call","id":"fc_1","call_id":"call_1","name":"getWeather","arguments":"{\\"city\\":\\"Paris\\"}"}]}}"""));

    @Test
    void should_stream_text_and_thinking() throws Exception {
        MockHttpClient httpClient = new MockHttpClient(TEXT_EVENTS);
        XAiStreamingChatModel model = model(new MockHttpClientBuilder(httpClient));

        RecordingHandler handler = new RecordingHandler();
        model.chat(ChatRequest.builder().messages(UserMessage.from("Hi")).build(), handler);
        ChatResponse response = handler.response.get(5, SECONDS);

        Map<String, Object> body = Json.fromJson(httpClient.request().body(), Map.class);
        assertThat(body).containsEntry("stream", true).containsEntry("model", "grok-4.7");
        assertThat(httpClient.request().headers().get("Accept")).containsExactly("text/event-stream");

        assertThat(handler.partialResponses).containsExactly("Hel", "lo!");
        assertThat(handler.partialThinking).containsExactly("Thinking");

        assertThat(response.aiMessage().text()).isEqualTo("Hello!");
        assertThat(response.aiMessage().thinking()).isEqualTo("Thinking");
        assertThat(response.aiMessage().attribute(XAiClient.ENCRYPTED_REASONING_KEY, String.class))
                .isEqualTo("ENC");
        assertThat(response.id()).isEqualTo("resp_1");
        assertThat(response.finishReason()).isEqualTo(FinishReason.STOP);
        assertThat(response.tokenUsage().totalTokenCount()).isEqualTo(15);
    }

    @Test
    void should_stream_tool_calls() throws Exception {
        XAiStreamingChatModel model = model(new MockHttpClientBuilder(new MockHttpClient(TOOL_CALL_EVENTS)));

        RecordingHandler handler = new RecordingHandler();
        model.chat(
                ChatRequest.builder()
                        .messages(UserMessage.from("Weather in Paris?"))
                        .toolSpecifications(ToolSpecification.builder()
                                .name("getWeather")
                                .parameters(JsonObjectSchema.builder()
                                        .addStringProperty("city")
                                        .build())
                                .build())
                        .build(),
                handler);
        ChatResponse response = handler.response.get(5, SECONDS);

        assertThat(handler.partialToolCalls)
                .extracting(
                        PartialToolCall::index,
                        PartialToolCall::id,
                        PartialToolCall::name,
                        PartialToolCall::partialArguments)
                .containsExactly(
                        tuple(0, "call_1", "getWeather", "{\"city\":"), tuple(0, "call_1", "getWeather", "\"Paris\"}"));

        ToolExecutionRequest expected = ToolExecutionRequest.builder()
                .id("call_1")
                .name("getWeather")
                .arguments("{\"city\":\"Paris\"}")
                .build();
        assertThat(handler.completeToolCalls).containsExactly(new CompleteToolCall(0, expected));
        assertThat(response.aiMessage().toolExecutionRequests()).containsExactly(expected);
        assertThat(response.finishReason()).isEqualTo(FinishReason.TOOL_EXECUTION);
    }

    @Test
    void should_report_failure_event_as_error() throws Exception {
        List<ServerSentEvent> events = List.of(event("""
                {"type":"response.failed","response":{"status":"failed","error":{"message":"boom"}}}"""));
        XAiStreamingChatModel model = model(new MockHttpClientBuilder(new MockHttpClient(events)));

        RecordingHandler handler = new RecordingHandler();
        model.chat(ChatRequest.builder().messages(UserMessage.from("Hi")).build(), handler);

        assertThat(handler.response)
                .failsWithin(5, SECONDS)
                .withThrowableThat()
                .havingRootCause()
                .withMessageContaining("boom");
    }

    @Test
    void should_stream_events_through_publisher() throws Exception {
        List<HttpStreamingEvent> httpEvents = new ArrayList<>();
        httpEvents.add(new HttpResponseReceived(SuccessfulHttpResponse.builder()
                .statusCode(200)
                .headers(Map.of())
                .build()));
        httpEvents.addAll(TEXT_EVENTS);
        XAiStreamingChatModel model = model(new MockHttpClientBuilder(new StreamingHttpClient(httpEvents)));

        Publisher<ChatModelStreamingEvent> publisher = model.chat(
                ChatRequest.builder().messages(UserMessage.from("Hi")).build());
        List<ChatModelStreamingEvent> events = collect(publisher);

        assertThat(events)
                .filteredOn(PartialResponse.class::isInstance)
                .extracting(e -> ((PartialResponse) e).text())
                .containsExactly("Hel", "lo!");
        assertThat(events).filteredOn(PartialThinking.class::isInstance).hasSize(1);
        assertThat(events.get(events.size() - 1)).isInstanceOf(CompleteResponse.class);
        ChatResponse response = ((CompleteResponse) events.get(events.size() - 1)).chatResponse();
        assertThat(response.aiMessage().text()).isEqualTo("Hello!");
    }

    private static XAiStreamingChatModel model(HttpClientBuilder httpClientBuilder) {
        return XAiStreamingChatModel.builder()
                .httpClientBuilder(httpClientBuilder)
                .modelName(XAiChatModelName.GROK_4_7)
                .build();
    }

    private static ServerSentEvent event(String data) {
        return new ServerSentEvent(null, data.replace("\n", ""));
    }

    private static List<ChatModelStreamingEvent> collect(Publisher<ChatModelStreamingEvent> publisher)
            throws InterruptedException {
        List<ChatModelStreamingEvent> events = new CopyOnWriteArrayList<>();
        List<Throwable> errors = new CopyOnWriteArrayList<>();
        CountDownLatch done = new CountDownLatch(1);
        publisher.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ChatModelStreamingEvent item) {
                events.add(item);
            }

            @Override
            public void onError(Throwable throwable) {
                errors.add(throwable);
                done.countDown();
            }

            @Override
            public void onComplete() {
                done.countDown();
            }
        });
        assertThat(done.await(5, SECONDS)).isTrue();
        assertThat(errors).isEmpty();
        return events;
    }

    private static class RecordingHandler implements StreamingChatResponseHandler {

        final List<String> partialResponses = new CopyOnWriteArrayList<>();
        final List<String> partialThinking = new CopyOnWriteArrayList<>();
        final List<PartialToolCall> partialToolCalls = new CopyOnWriteArrayList<>();
        final List<CompleteToolCall> completeToolCalls = new CopyOnWriteArrayList<>();
        final CompletableFuture<ChatResponse> response = new CompletableFuture<>();

        @Override
        public void onPartialResponse(String partialResponse) {
            partialResponses.add(partialResponse);
        }

        @Override
        public void onPartialThinking(PartialThinking partialThinking) {
            this.partialThinking.add(partialThinking.text());
        }

        @Override
        public void onPartialToolCall(PartialToolCall partialToolCall) {
            partialToolCalls.add(partialToolCall);
        }

        @Override
        public void onCompleteToolCall(CompleteToolCall completeToolCall) {
            completeToolCalls.add(completeToolCall);
        }

        @Override
        public void onCompleteResponse(ChatResponse completeResponse) {
            response.complete(completeResponse);
        }

        @Override
        public void onError(Throwable error) {
            response.completeExceptionally(error);
        }
    }

    /**
     * Emits fixed events from {@link #stream(HttpRequest, ServerSentEventParser)}, which {@link MockHttpClient} lacks.
     */
    private static class StreamingHttpClient implements HttpClient {

        private final List<HttpStreamingEvent> events;

        StreamingHttpClient(List<HttpStreamingEvent> events) {
            this.events = events;
        }

        @Override
        public SuccessfulHttpResponse execute(HttpRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Publisher<HttpStreamingEvent> stream(HttpRequest request, ServerSentEventParser parser) {
            return ZeroPublisher.fromIterable(events);
        }
    }
}
