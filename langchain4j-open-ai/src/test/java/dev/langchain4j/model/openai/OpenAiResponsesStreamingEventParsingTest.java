package dev.langchain4j.model.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.sse.DefaultServerSentEventParser;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Covers how server-sent events of the Responses API are read: which field of which event ends up
 * where in the resulting {@link ChatResponse}.
 */
class OpenAiResponsesStreamingEventParsingTest {

    private static ServerSentEvent event(String json) {
        return new ServerSentEvent(null, json);
    }

    private final StringBuilder text = new StringBuilder();
    private final StringBuilder thinking = new StringBuilder();
    private final List<PartialToolCall> partialToolCalls = new ArrayList<>();
    private final List<CompleteToolCall> completeToolCalls = new ArrayList<>();
    private final CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();
    private final AtomicInteger errorCount = new AtomicInteger();

    private final StreamingChatResponseHandler handler = new StreamingChatResponseHandler() {

        @Override
        public void onPartialResponse(String partialResponse) {
            text.append(partialResponse);
        }

        @Override
        public void onPartialThinking(PartialThinking partialThinking) {
            thinking.append(partialThinking.text());
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
            futureResponse.complete(completeResponse);
        }

        @Override
        public void onError(Throwable error) {
            errorCount.incrementAndGet();
            futureResponse.completeExceptionally(error);
        }
    };

    private ChatResponse chatWith(ServerSentEvent... events) throws Exception {
        stream(events);
        return futureResponse.get(5, TimeUnit.SECONDS);
    }

    private void stream(ServerSentEvent... events) {
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(List.of(events));

        OpenAiResponsesStreamingChatModel.builder()
                .apiKey("dummy")
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .modelName("gpt-5.4-mini")
                .build()
                .chat("Hello", handler);
    }

    @Test
    void should_stream_text_and_reasoning_deltas() throws Exception {
        ChatResponse response = chatWith(
                event("{\"type\":\"response.reasoning_text.delta\",\"delta\":\"Let me \"}"),
                event("{\"type\":\"response.reasoning_summary_text.delta\",\"delta\":\"think.\"}"),
                event("{\"type\":\"response.output_text.delta\",\"delta\":\"Hello\"}"),
                event("{\"type\":\"response.output_text.delta\",\"delta\":\", world\"}"),
                event(
                        "{\"type\":\"response.completed\",\"response\":{\"id\":\"resp_1\",\"model\":\"gpt-5.4-mini\",\"status\":\"completed\",\"output\":[{\"id\":\"msg_1\",\"type\":\"message\",\"role\":\"assistant\",\"content\":[{\"type\":\"output_text\",\"text\":\"Hello, world\"}]}]}}"));

        assertThat(text).hasToString("Hello, world");
        assertThat(thinking).hasToString("Let me think.");
        assertThat(response.aiMessage().text()).isEqualTo("Hello, world");
        assertThat(response.metadata().id()).isEqualTo("resp_1");
        assertThat(response.metadata().modelName()).isEqualTo("gpt-5.4-mini");
        assertThat(response.metadata().finishReason()).isEqualTo(FinishReason.STOP);
    }

    @Test
    void should_stream_a_tool_call_assembled_from_its_events() throws Exception {
        ChatResponse response = chatWith(
                event(
                        "{\"type\":\"response.output_item.added\",\"output_index\":0,\"item\":{\"type\":\"function_call\",\"id\":\"fc_1\",\"call_id\":\"call_1\",\"name\":\"getWeather\"}}"),
                event(
                        "{\"type\":\"response.function_call_arguments.delta\",\"item_id\":\"fc_1\",\"delta\":\"{\\\"city\\\":\"}"),
                event(
                        "{\"type\":\"response.function_call_arguments.delta\",\"item_id\":\"fc_1\",\"delta\":\"\\\"Munich\\\"}\"}"),
                event(
                        "{\"type\":\"response.function_call_arguments.done\",\"item_id\":\"fc_1\",\"arguments\":\"{\\\"city\\\":\\\"Munich\\\"}\"}"),
                event(
                        "{\"type\":\"response.completed\",\"response\":{\"id\":\"resp_1\",\"model\":\"gpt-5.4-mini\",\"status\":\"completed\",\"output\":[]}}"));

        assertThat(partialToolCalls)
                .extracting(
                        PartialToolCall::index,
                        PartialToolCall::id,
                        PartialToolCall::name,
                        PartialToolCall::partialArguments)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(0, "call_1", "getWeather", "{\"city\":"),
                        org.assertj.core.groups.Tuple.tuple(0, "call_1", "getWeather", "\"Munich\"}"));

        ToolExecutionRequest expected = ToolExecutionRequest.builder()
                .id("call_1")
                .name("getWeather")
                .arguments("{\"city\":\"Munich\"}")
                .build();
        assertThat(completeToolCalls)
                .extracting(CompleteToolCall::toolExecutionRequest)
                .containsExactly(expected);
        assertThat(response.aiMessage().toolExecutionRequests()).containsExactly(expected);
        assertThat(response.metadata().finishReason()).isEqualTo(FinishReason.TOOL_EXECUTION);
    }

    @Test
    void should_complete_a_tool_call_from_the_output_item_done_event() throws Exception {
        ChatResponse response = chatWith(
                event(
                        "{\"type\":\"response.output_item.done\",\"output_index\":2,\"item\":{\"type\":\"function_call\",\"id\":\"fc_1\",\"call_id\":\"call_1\",\"name\":\"getWeather\",\"arguments\":\"{\\\"city\\\":\\\"Munich\\\"}\"}}"),
                event(
                        "{\"type\":\"response.completed\",\"response\":{\"id\":\"resp_1\",\"model\":\"gpt-5.4-mini\",\"status\":\"completed\",\"output\":[]}}"));

        assertThat(completeToolCalls).extracting(CompleteToolCall::index).containsExactly(2);
        assertThat(response.aiMessage().toolExecutionRequests())
                .containsExactly(ToolExecutionRequest.builder()
                        .id("call_1")
                        .name("getWeather")
                        .arguments("{\"city\":\"Munich\"}")
                        .build());
    }

    @Test
    void should_read_the_metadata_of_the_completed_event() throws Exception {
        ChatResponse response = chatWith(event("""
                        {"type":"response.completed","response":{
                          "id":"resp_1",
                          "model":"gpt-5.4-mini",
                          "status":"completed",
                          "created_at":1741476542,
                          "completed_at":1741476548,
                          "service_tier":"default",
                          "usage":{"input_tokens":10,"output_tokens":20,"total_tokens":30,
                                   "input_tokens_details":{"cached_tokens":4},
                                   "output_tokens_details":{"reasoning_tokens":6}},
                          "output":[]}}"""));

        OpenAiResponsesChatResponseMetadata metadata = (OpenAiResponsesChatResponseMetadata) response.metadata();
        assertThat(metadata.createdAt()).isEqualTo(1741476542L);
        assertThat(metadata.completedAt()).isEqualTo(1741476548L);
        assertThat(metadata.serviceTier()).isEqualTo("default");

        OpenAiTokenUsage tokenUsage = (OpenAiTokenUsage) metadata.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(10);
        assertThat(tokenUsage.outputTokenCount()).isEqualTo(20);
        assertThat(tokenUsage.totalTokenCount()).isEqualTo(30);
        assertThat(tokenUsage.inputTokensDetails().cachedTokens()).isEqualTo(4);
        assertThat(tokenUsage.outputTokensDetails().reasoningTokens()).isEqualTo(6);
    }

    @Test
    void should_report_the_incomplete_reason_of_the_incomplete_event() throws Exception {
        ChatResponse response = chatWith(
                event(
                        "{\"type\":\"response.incomplete\",\"response\":{\"id\":\"resp_1\",\"model\":\"gpt-5.4-mini\",\"status\":\"incomplete\",\"incomplete_details\":{\"reason\":\"max_output_tokens\"},\"output\":[]}}"));

        assertThat(response.metadata().finishReason()).isEqualTo(FinishReason.LENGTH);
    }

    @Test
    void should_fail_with_the_error_message_of_the_failed_event() {
        stream(
                event(
                        "{\"type\":\"response.failed\",\"response\":{\"id\":\"resp_1\",\"error\":{\"code\":\"server_error\",\"message\":\"Something went wrong\"}}}"));

        assertThatThrownBy(() -> futureResponse.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasMessageContaining("Response failed: Something went wrong")
                .cause()
                .isInstanceOfSatisfying(
                        OpenAiResponsesStreamingException.class,
                        e -> assertThat(e.code()).isEqualTo("server_error"));
    }

    @Test
    void should_fail_with_the_code_and_message_of_a_top_level_error_event() {
        stream(
                event("{\"type\":\"response.output_text.delta\",\"delta\":\"Hel\"}"),
                event(
                        "{\"type\":\"error\",\"sequence_number\":2,\"code\":\"rate_limit_exceeded\",\"message\":\"Rate limit reached\",\"param\":null}"));

        assertThat(text).hasToString("Hel");
        assertThatThrownBy(() -> futureResponse.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .cause()
                .isInstanceOfSatisfying(OpenAiResponsesStreamingException.class, e -> {
                    assertThat(e).hasMessage("Response failed: Rate limit reached");
                    assertThat(e.code()).isEqualTo("rate_limit_exceeded");
                });
        assertThat(errorCount).hasValue(1);
    }

    @Test
    void should_fail_with_the_code_and_message_of_a_nested_error_event() {
        stream(
                event(
                        "{\"type\":\"error\",\"sequence_number\":1,\"error\":{\"type\":\"insufficient_quota\",\"code\":\"insufficient_quota\",\"message\":\"You exceeded your current quota\",\"param\":null}}"));

        assertThatThrownBy(() -> futureResponse.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .cause()
                .isInstanceOfSatisfying(OpenAiResponsesStreamingException.class, e -> {
                    assertThat(e).hasMessage("Response failed: You exceeded your current quota");
                    assertThat(e.code()).isEqualTo("insufficient_quota");
                });
        assertThat(errorCount).hasValue(1);
    }

    @Test
    void should_fail_when_the_stream_closes_before_a_terminal_event() {
        stream(event("{\"type\":\"response.output_text.delta\",\"delta\":\"Hel\"}"));

        assertThat(text).hasToString("Hel");
        assertThatThrownBy(() -> futureResponse.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .cause()
                .isInstanceOfSatisfying(
                        OpenAiResponsesStreamingException.class,
                        e -> assertThat(e.code()).isNull());
        assertThat(errorCount).hasValue(1);
    }

    @Test
    void should_fail_once_when_the_completed_event_cannot_be_parsed() {
        // like the real HTTP clients: the SSE parser swallows listener exceptions, then the stream closes
        MockHttpClient mockHttpClient = new MockHttpClient() {
            @Override
            public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {
                String body = "data: {\"type\":\"response.output_text.delta\",\"delta\":\"Hel\"}\n\n"
                        + "data: {\"type\":\"response.completed\",\"response\":{\"id\":\n\n";
                new DefaultServerSentEventParser()
                        .parse(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)), listener);
                listener.onClose();
            }
        };

        OpenAiResponsesStreamingChatModel.builder()
                .apiKey("dummy")
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .modelName("gpt-5.4-mini")
                .build()
                .chat("Hello", handler);

        assertThat(text).hasToString("Hel");
        assertThatThrownBy(() -> futureResponse.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .cause()
                .isInstanceOf(OpenAiResponsesStreamingException.class);
        assertThat(errorCount).hasValue(1);
    }

    @Test
    void should_report_an_error_event_followed_by_a_failed_event_only_once() {
        stream(
                event("{\"type\":\"error\",\"code\":\"server_error\",\"message\":\"First\"}"),
                event(
                        "{\"type\":\"response.failed\",\"response\":{\"id\":\"resp_1\",\"error\":{\"code\":\"server_error\",\"message\":\"Second\"}}}"));

        assertThatThrownBy(() -> futureResponse.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasMessageContaining("Response failed: First");
        assertThat(errorCount).hasValue(1);
    }

    @Test
    void should_fall_back_to_the_error_payload_when_the_failed_event_carries_no_message() {
        stream(event("{\"type\":\"response.error\",\"error\":{\"code\":\"server_error\"}}"));

        assertThatThrownBy(() -> futureResponse.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasMessageContaining("server_error");
    }

    @Test
    void should_ignore_events_that_do_not_belong_to_a_known_tool_call() throws Exception {
        ChatResponse response = chatWith(Stream.of(
                        "{\"type\":\"response.output_item.added\",\"output_index\":0,\"item\":{\"type\":\"reasoning\",\"id\":\"rs_1\"}}",
                        "{\"type\":\"response.function_call_arguments.delta\",\"item_id\":\"unknown\",\"delta\":\"{}\"}",
                        "{\"type\":\"response.function_call_arguments.done\",\"item_id\":\"unknown\",\"arguments\":\"{}\"}",
                        "{\"type\":\"response.output_item.done\",\"output_index\":0,\"item\":{\"type\":\"message\",\"id\":\"msg_1\"}}",
                        "{\"type\":\"response.completed\",\"response\":{\"id\":\"resp_1\",\"model\":\"gpt-5.4-mini\",\"status\":\"completed\",\"output\":[]}}")
                .map(OpenAiResponsesStreamingEventParsingTest::event)
                .toArray(ServerSentEvent[]::new));

        assertThat(partialToolCalls).isEmpty();
        assertThat(completeToolCalls).isEmpty();
        assertThat(response.aiMessage().toolExecutionRequests()).isEmpty();
    }
}
