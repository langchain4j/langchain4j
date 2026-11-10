package dev.langchain4j.test.mock;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatModelStreamingEvent;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialResponseContext;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public class StreamingChatModelMockTest {

    @Test
    void test_toTokens() {

        AiMessage aiMessage = AiMessage.from("Hello");

        List<String> tokens = StreamingChatModelMock.toTokens(aiMessage);

        assertThat(tokens).containsExactly("H", "e", "l", "l", "o");
    }

    @Test
    void test_toTokens_with_empty_string() {
        AiMessage aiMessage = AiMessage.from("");

        List<String> tokens = StreamingChatModelMock.toTokens(aiMessage);

        assertThat(tokens).isEmpty();
    }

    @Test
    void test_toTokens_preserves_consecutive_spaces() {
        AiMessage aiMessage = AiMessage.from("a  b");

        List<String> tokens = StreamingChatModelMock.toTokens(aiMessage);

        assertThat(tokens).containsExactly("a", " ", " ", "b");
    }

    @Test
    void should_complete_once_when_subscriber_requests_one_event_at_a_time() {

        StreamingChatModelMock model = StreamingChatModelMock.thatStreams(AiMessage.from("Hi"));
        List<ChatModelStreamingEvent> events = new ArrayList<>();
        AtomicInteger completions = new AtomicInteger();

        model.chat(ChatRequest.builder().messages(UserMessage.from("Hello")).build())
                .subscribe(new Flow.Subscriber<>() {

                    Flow.Subscription subscription;

                    @Override
                    public void onSubscribe(Flow.Subscription subscription) {
                        this.subscription = subscription;
                        subscription.request(1);
                    }

                    @Override
                    public void onNext(ChatModelStreamingEvent event) {
                        events.add(event);
                        subscription.request(1);
                    }

                    @Override
                    public void onError(Throwable error) {
                        throw new AssertionError(error);
                    }

                    @Override
                    public void onComplete() {
                        completions.incrementAndGet();
                    }
                });

        assertThat(events).hasSize(3).last().isInstanceOf(CompleteResponse.class);
        assertThat(completions).hasValue(1);
    }

    @Test
    void should_stream_a_single_response_for_every_request() throws Exception {

        StreamingChatModelMock model = StreamingChatModelMock.thatStreams(AiMessage.from("Hi"));

        assertThat(chat(model)).isEqualTo("Hi");
        assertThat(chat(model)).isEqualTo("Hi");
        assertThat(chat(model)).isEqualTo("Hi");
    }

    @Test
    void should_fail_instead_of_repeating_a_single_response_with_tool_calls() throws Exception {

        StreamingChatModelMock model = StreamingChatModelMock.thatStreams(AiMessage.from(
                ToolExecutionRequest.builder().id("1").name("getWeather").arguments("{}").build()));

        chat(model);
        assertThat(stream(model).error()).hasMessageContaining("has no more responses");
    }

    @Test
    void should_stream_several_responses_in_order_then_repeat_the_last_one() throws Exception {

        StreamingChatModelMock model =
                StreamingChatModelMock.thatStreams(AiMessage.from("first"), AiMessage.from("second"));

        assertThat(chat(model)).isEqualTo("first");
        assertThat(chat(model)).isEqualTo("second");
        assertThat(chat(model)).isEqualTo("second");
    }

    @Test
    void should_fail_instead_of_repeating_a_response_with_tool_calls() throws Exception {

        AiMessage toolCall = AiMessage.from(ToolExecutionRequest.builder()
                .id("1")
                .name("getWeather")
                .arguments("{}")
                .build());
        StreamingChatModelMock model = StreamingChatModelMock.thatStreams(AiMessage.from("first"), toolCall);

        assertThat(chat(model)).isEqualTo("first");
        chat(model);
        assertThat(stream(model).error())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("StreamingChatModelMock has no more responses: the last scripted response (with tool"
                        + " calls) would be repeated, which would loop forever. Script the response that follows the"
                        + " tool results.");
    }

    @Test
    void should_stream_given_tokens() throws Exception {

        StreamingChatModelMock model = StreamingChatModelMock.thatStreams("Hel", "lo");
        List<String> tokens = new CopyOnWriteArrayList<>();
        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

        model.chat(ChatRequest.builder().messages(UserMessage.from("Hi")).build(), new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String partialResponse) {
                tokens.add(partialResponse);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                futureResponse.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                futureResponse.completeExceptionally(error);
            }
        });

        assertThat(futureResponse.get(10, SECONDS).aiMessage().text()).isEqualTo("Hello");
        assertThat(tokens).containsExactly("Hel", "lo");
    }

    @Test
    void should_publish_given_tokens() {

        StreamingChatModelMock model = StreamingChatModelMock.thatStreams("Hel", "lo");

        List<ChatModelStreamingEvent> events = collect(model);

        assertThat(events).hasSize(3);
        assertThat(events.subList(0, 2))
                .extracting(event -> ((PartialResponse) event).text())
                .containsExactly("Hel", "lo");
        assertThat(((CompleteResponse) events.get(2)).chatResponse().aiMessage().text())
                .isEqualTo("Hello");
    }

    @Test
    void should_fail_fast_when_throwing_from_chat_is_used_with_the_publisher() {

        StreamingChatModelMock model =
                StreamingChatModelMock.thatThrows(new RuntimeException("boom")).throwingFromChat();

        // the publisher reports the error to its subscriber, which collect(...) rethrows
        assertThatThrownBy(() -> collect(model))
                .hasCauseInstanceOf(IllegalStateException.class)
                .hasMessageContaining("throwingFromChat()");
    }

    @Test
    void should_publish_the_given_tokens_again_for_every_request() {

        StreamingChatModelMock model = StreamingChatModelMock.thatStreams("Hel", "lo");

        collect(model);
        List<ChatModelStreamingEvent> events = collect(model);

        assertThat(events.subList(0, 2))
                .extracting(event -> ((PartialResponse) event).text())
                .containsExactly("Hel", "lo");
    }

    @Test
    void should_publish_one_token_per_character_when_built_from_ai_message() {

        StreamingChatModelMock model = StreamingChatModelMock.thatStreams(AiMessage.from("Hello"));

        List<ChatModelStreamingEvent> events = collect(model);

        assertThat(events).hasSize(6);
        assertThat(events.subList(0, 5))
                .extracting(event -> ((PartialResponse) event).text())
                .containsExactly("H", "e", "l", "l", "o");
    }

    private static List<ChatModelStreamingEvent> collect(StreamingChatModelMock model) {
        List<ChatModelStreamingEvent> events = new ArrayList<>();
        model.chat(ChatRequest.builder().messages(UserMessage.from("Hi")).build())
                .subscribe(new Flow.Subscriber<>() {

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
                        throw new AssertionError(error);
                    }

                    @Override
                    public void onComplete() {}
                });
        return events;
    }

    @Test
    void should_report_the_given_token_usage_and_finish_reason_in_every_complete_response() throws Exception {

        StreamingChatModelMock model = StreamingChatModelMock.thatStreams("Hi")
                .withTokenUsage(new TokenUsage(1, 2))
                .withFinishReason(FinishReason.LENGTH);

        for (int i = 0; i < 2; i++) {
            ChatResponse response = chatResponse(model);
            assertThat(response.tokenUsage()).isEqualTo(new TokenUsage(1, 2));
            assertThat(response.finishReason()).isEqualTo(FinishReason.LENGTH);
        }
    }

    @Test
    void should_report_the_given_token_usages_in_order_then_repeat_the_last_one() throws Exception {

        StreamingChatModelMock model = StreamingChatModelMock.thatStreams("Hi")
                .withTokenUsages(new TokenUsage(1, 2), new TokenUsage(3, 4));

        assertThat(chatResponse(model).tokenUsage()).isEqualTo(new TokenUsage(1, 2));
        assertThat(chatResponse(model).tokenUsage()).isEqualTo(new TokenUsage(3, 4));
        assertThat(chatResponse(model).tokenUsage()).isEqualTo(new TokenUsage(3, 4));
    }

    @Test
    void should_publish_the_given_token_usages_in_order_then_repeat_the_last_one() {

        StreamingChatModelMock model = StreamingChatModelMock.thatStreams("Hi")
                .withTokenUsages(new TokenUsage(1, 2), new TokenUsage(3, 4))
                .withFinishReason(FinishReason.STOP);

        assertThat(completeResponse(collect(model)).tokenUsage()).isEqualTo(new TokenUsage(1, 2));
        assertThat(completeResponse(collect(model)).tokenUsage()).isEqualTo(new TokenUsage(3, 4));
        ChatResponse third = completeResponse(collect(model));
        assertThat(third.tokenUsage()).isEqualTo(new TokenUsage(3, 4));
        assertThat(third.finishReason()).isEqualTo(FinishReason.STOP);
    }

    @Test
    void should_call_a_tool_and_then_stream() throws Exception {

        StreamingChatModelMock model = StreamingChatModelMock.thatCallsTool("cancelBooking", Map.of("id", 7))
                .thenStreams("can", "celled");

        ToolExecutionRequest toolCall =
                chatResponse(model).aiMessage().toolExecutionRequests().get(0);
        assertThat(toolCall.name()).isEqualTo("cancelBooking");
        assertThat(toolCall.arguments()).isEqualTo("{\"id\":7}");

        Streamed streamed = stream(model);
        assertThat(streamed.tokens()).containsExactly("can", "celled");
        assertThat(streamed.response().aiMessage().text()).isEqualTo("cancelled");
    }

    @Test
    void should_take_the_steps_in_order_then_repeat_the_last_one() throws Exception {

        StreamingChatModelMock model = StreamingChatModelMock.thatStreams("fi", "rst")
                .thenCallsTool("getWeather", Map.of())
                .thenStreams(AiMessage.from("third"));

        assertThat(chat(model)).isEqualTo("first");
        assertThat(chatResponse(model).aiMessage().toolExecutionRequests()).hasSize(1);
        assertThat(chat(model)).isEqualTo("third");
        assertThat(chat(model)).isEqualTo("third");
    }

    @Test
    void should_fail_after_the_tokens_when_the_stream_breaks() throws Exception {

        RuntimeException failure = new IllegalStateException("connection reset");
        StreamingChatModelMock model = StreamingChatModelMock.thatStreams("Hel", "lo").thenFailsWith(failure);

        Streamed first = stream(model);
        assertThat(first.tokens()).containsExactly("Hel", "lo");
        assertThat(first.error()).isSameAs(failure);
        assertThat(first.response()).isNull();

        Streamed second = stream(model);
        assertThat(second.tokens()).containsExactly("Hel", "lo");
        assertThat(second.error()).isSameAs(failure);
    }

    @Test
    void should_publish_the_tokens_and_then_the_error_when_the_stream_breaks() {

        RuntimeException failure = new IllegalStateException("connection reset");
        StreamingChatModelMock model = StreamingChatModelMock.thatStreams("Hel", "lo").thenFailsWith(failure);

        List<Object> signals = new ArrayList<>();
        subscribe(model, signals);

        assertThat(signals).hasSize(3);
        assertThat(((PartialResponse) signals.get(0)).text()).isEqualTo("Hel");
        assertThat(((PartialResponse) signals.get(1)).text()).isEqualTo("lo");
        assertThat(signals.get(2)).isSameAs(failure);
    }

    @Test
    void should_fail_only_the_response_that_the_failure_follows() throws Exception {

        RuntimeException failure = new IllegalStateException("connection reset");
        StreamingChatModelMock model = StreamingChatModelMock.thatStreams("broken")
                .thenFailsWith(failure)
                .thenStreams("fine");

        assertThat(stream(model).error()).isSameAs(failure);
        assertThat(chat(model)).isEqualTo("fine");
    }

    @Test
    void should_fail_before_streaming_with_a_scripted_exception_and_then_stream() throws Exception {

        RuntimeException failure = new IllegalStateException("temporary failure");
        StreamingChatModelMock model = StreamingChatModelMock.thatThrows(failure).thenStreams("recovered");

        Streamed failed = stream(model);
        assertThat(failed.tokens()).isEmpty();
        assertThat(failed.error()).isSameAs(failure);
        assertThat(chat(model)).isEqualTo("recovered");
    }

    @Test
    void should_publish_only_the_error_of_a_step_that_throws() {

        RuntimeException failure = new IllegalStateException("down");
        StreamingChatModelMock model = StreamingChatModelMock.thatThrows(failure);

        List<Object> signals = new ArrayList<>();
        subscribe(model, signals);

        assertThat(signals).containsExactly(failure);
    }

    @Test
    void should_always_fail_with_the_given_exception() throws Exception {

        RuntimeException failure = new IllegalArgumentException("invalid");
        StreamingChatModelMock model = StreamingChatModelMock.thatThrows(failure);

        assertThat(stream(model).error()).isSameAs(failure);
        assertThat(stream(model).error()).isSameAs(failure);
    }

    @Test
    void should_stream_a_collection_of_messages_in_order() throws Exception {

        StreamingChatModelMock model =
                StreamingChatModelMock.thatStreams(List.of(AiMessage.from("first"), AiMessage.from("second")));

        assertThat(chat(model)).isEqualTo("first");
        assertThat(chat(model)).isEqualTo("second");
    }

    @Test
    void should_not_accept_steps_on_a_mock_that_computes_its_answers() {

        assertThatThrownBy(() -> StreamingChatModelMock.thatStreams(request -> AiMessage.from("Hi"))
                        .thenStreams("again"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Steps can only be added");
    }

    @Test
    void should_not_accept_a_failure_after_the_tokens_on_a_mock_that_always_fails() {

        assertThatThrownBy(() -> StreamingChatModelMock.thatThrows(new RuntimeException("a"))
                        .thenFailsWith(new RuntimeException("b")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("thenFailsWith");
        assertThatThrownBy(() -> StreamingChatModelMock.thatStreams("a")
                        .thenThrows(new RuntimeException("a"))
                        .thenFailsWith(new RuntimeException("b")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void should_not_accept_a_second_failure_on_the_same_response() {

        assertThatThrownBy(() -> StreamingChatModelMock.thatStreams("a")
                        .thenFailsWith(new RuntimeException("first"))
                        .thenFailsWith(new RuntimeException("second")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("thenFailsWith");
    }

    @Test
    void should_not_accept_a_failure_after_the_tokens_of_a_tool_call() {

        assertThatThrownBy(() -> StreamingChatModelMock.thatCallsTool("a", Map.of())
                        .thenFailsWith(new RuntimeException()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("calls a tool");
    }

    @Test
    void should_not_accept_a_failure_after_the_tokens_on_a_mock_that_computes_its_answers() {

        assertThatThrownBy(() -> StreamingChatModelMock.thatStreams(request -> AiMessage.from("Hi"))
                        .thenFailsWith(new RuntimeException()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("computes its answers");
    }

    @Test
    void should_not_accept_a_failure_after_all_steps_were_taken() throws Exception {

        StreamingChatModelMock model = StreamingChatModelMock.thatStreams("a");
        chat(model);

        assertThatThrownBy(() -> model.thenFailsWith(new RuntimeException()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("thenFailsWith");
    }

    @Test
    void should_compute_the_answer_from_the_request() throws Exception {

        StreamingChatModelMock model = StreamingChatModelMock.thatStreams(
                request -> AiMessage.from("You said " + ((UserMessage) request.messages().get(0)).singleText()));

        assertThat(stream(model).response().aiMessage().text()).isEqualTo("You said Hi");
    }

    @Test
    void should_report_a_failing_computation_through_on_error() throws Exception {

        RuntimeException failure = new IllegalStateException("cannot answer");
        StreamingChatModelMock model = StreamingChatModelMock.thatStreams(request -> {
            throw failure;
        });

        assertThat(stream(model).error()).isSameAs(failure);
    }

    @Test
    void should_signal_an_exhausted_script_to_the_subscriber() throws Exception {

        StreamingChatModelMock model = StreamingChatModelMock.thatCallsTool("getWeather", Map.of());
        chat(model);

        List<Object> signals = new ArrayList<>();
        subscribe(model, signals);

        assertThat(signals).singleElement().isInstanceOfSatisfying(IllegalStateException.class, error -> assertThat(
                        error)
                .hasMessageContaining("has no more responses"));
    }

    @Test
    void should_publish_complete_tool_calls_before_the_complete_response() {

        ToolExecutionRequest first = ChatModelMock.toolCall("a", Map.of("x", 1));
        ToolExecutionRequest second = ChatModelMock.toolCall("b", Map.of("y", 2));
        StreamingChatModelMock model =
                StreamingChatModelMock.thatStreams(AiMessage.from(first, second), AiMessage.from("done"));

        List<ChatModelStreamingEvent> events = collect(model);

        assertThat(events.stream().filter(CompleteToolCall.class::isInstance).map(CompleteToolCall.class::cast))
                .extracting(CompleteToolCall::toolExecutionRequest)
                .containsExactly(first, second);
        assertThat(events.get(events.size() - 1)).isInstanceOf(CompleteResponse.class);
    }

    @Test
    void should_publish_thinking_then_text_then_partial_and_complete_tool_calls() {

        AiMessage answer = AiMessage.builder()
                .thinking("hm")
                .text("ok")
                .toolExecutionRequests(List.of(ChatModelMock.toolCall("a", "{}")))
                .build();
        StreamingChatModelMock model = StreamingChatModelMock.thatStreams(answer, AiMessage.from("done"));

        List<ChatModelStreamingEvent> events = collect(model);

        assertThat(events).hasSize(8);
        assertThat(events.subList(0, 2)).containsExactly(new PartialThinking("h"), new PartialThinking("m"));
        assertThat(events.subList(2, 4)).containsExactly(new PartialResponse("o"), new PartialResponse("k"));
        assertThat(events.subList(4, 6))
                .extracting(event -> ((PartialToolCall) event).partialArguments())
                .containsExactly("{", "}");
        assertThat(((PartialToolCall) events.get(4)).name()).isEqualTo("a");
        assertThat(((PartialToolCall) events.get(4)).id()).isEqualTo("call_a");
        assertThat(((PartialToolCall) events.get(4)).index()).isZero();
        assertThat(events.get(6)).isInstanceOf(CompleteToolCall.class);
        assertThat(events.get(7)).isInstanceOf(CompleteResponse.class);
    }

    @Test
    void should_call_the_handler_with_thinking_then_text_then_partial_and_complete_tool_calls() throws Exception {

        AiMessage answer = AiMessage.builder()
                .thinking("hm")
                .text("ok")
                .toolExecutionRequests(List.of(ChatModelMock.toolCall("a", "{}")))
                .build();
        StreamingChatModelMock model = StreamingChatModelMock.thatStreams(answer, AiMessage.from("done"));
        List<String> signals = new CopyOnWriteArrayList<>();
        CompletableFuture<Void> done = new CompletableFuture<>();

        model.chat(ChatRequest.builder().messages(UserMessage.from("Hi")).build(), new StreamingChatResponseHandler() {
            @Override
            public void onPartialThinking(PartialThinking partialThinking) {
                signals.add("thinking:" + partialThinking.text());
            }

            @Override
            public void onPartialResponse(String partialResponse) {
                signals.add("text:" + partialResponse);
            }

            @Override
            public void onPartialToolCall(PartialToolCall partialToolCall) {
                signals.add("partial:" + partialToolCall.partialArguments());
            }

            @Override
            public void onCompleteToolCall(CompleteToolCall completeToolCall) {
                signals.add("complete:" + completeToolCall.toolExecutionRequest().name());
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                signals.add("response");
                done.complete(null);
            }

            @Override
            public void onError(Throwable error) {
                done.completeExceptionally(error);
            }
        });

        done.get(5, SECONDS);
        assertThat(signals)
                .containsExactly(
                        "thinking:h",
                        "thinking:m",
                        "text:o",
                        "text:k",
                        "partial:{",
                        "partial:}",
                        "complete:a",
                        "response");
    }

    @Test
    void should_stream_thinking_before_a_failure_after_the_tokens() throws Exception {

        RuntimeException failure = new IllegalStateException("connection reset");
        AiMessage answer = AiMessage.builder().thinking("hm").text("ok").build();
        List<Object> signals = new ArrayList<>();

        StreamingChatModelMock breaking =
                StreamingChatModelMock.thatStreams(List.of(answer)).thenFailsWith(failure);
        subscribe(breaking, signals);

        assertThat(signals)
                .containsExactly(
                        new PartialThinking("h"),
                        new PartialThinking("m"),
                        new PartialResponse("o"),
                        new PartialResponse("k"),
                        failure);
    }

    @Test
    void should_call_a_tool_with_raw_json_arguments() throws Exception {

        StreamingChatModelMock model =
                StreamingChatModelMock.thatCallsTool("a", "{not json").thenCallsTool("b", "[]");

        assertThat(chatResponse(model).aiMessage().toolExecutionRequests().get(0).arguments())
                .isEqualTo("{not json");
        assertThat(chatResponse(model).aiMessage().toolExecutionRequests().get(0).arguments())
                .isEqualTo("[]");
    }

    @Test
    void should_repeat_a_last_step_that_throws() throws Exception {

        RuntimeException failure = new IllegalStateException("down");
        StreamingChatModelMock model = StreamingChatModelMock.thatStreams("first").thenThrows(failure);

        assertThat(chat(model)).isEqualTo("first");
        assertThat(stream(model).error()).isSameAs(failure);
        assertThat(stream(model).error()).isSameAs(failure);
    }

    @Test
    void should_not_accept_an_empty_list_of_messages() {

        assertThatThrownBy(() -> StreamingChatModelMock.thatStreams(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("aiMessages");
    }

    @Test
    void should_not_accept_null_arguments() {

        assertThatThrownBy(() -> StreamingChatModelMock.thatThrows(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StreamingChatModelMock.thatStreams("Hi").thenFailsWith(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StreamingChatModelMock.thatStreams((String) null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_report_the_given_model_names_in_order_then_repeat_the_last_one() throws Exception {

        StreamingChatModelMock model = StreamingChatModelMock.thatStreams("Hi").withModelNames("a", "b");

        assertThat(chatResponse(model).modelName()).isEqualTo("a");
        assertThat(chatResponse(model).modelName()).isEqualTo("b");
        assertThat(chatResponse(model).modelName()).isEqualTo("b");
    }

    @Test
    void should_report_the_given_finish_reasons_in_order_then_repeat_the_last_one() throws Exception {

        StreamingChatModelMock model =
                StreamingChatModelMock.thatStreams("Hi").withFinishReasons(FinishReason.LENGTH, FinishReason.STOP);

        assertThat(chatResponse(model).finishReason()).isEqualTo(FinishReason.LENGTH);
        assertThat(chatResponse(model).finishReason()).isEqualTo(FinishReason.STOP);
        assertThat(chatResponse(model).finishReason()).isEqualTo(FinishReason.STOP);
    }

    @Test
    void should_report_the_given_provider() {

        assertThat(StreamingChatModelMock.thatStreams("Hi").withProvider(ModelProvider.OPEN_AI).provider())
                .isEqualTo(ModelProvider.OPEN_AI);
    }

    @Test
    void should_not_split_a_character_outside_the_basic_plane_into_two_tokens() {

        assertThat(StreamingChatModelMock.toTokens(AiMessage.from("a\uD83D\uDE00"))).containsExactly("a", "\uD83D\uDE00");
    }

    @Test
    void should_stop_after_the_handle_was_cancelled_during_the_last_token() throws Exception {

        StreamingChatModelMock model = StreamingChatModelMock.thatStreams("Hel", "lo");
        List<String> signals = new CopyOnWriteArrayList<>();
        CompletableFuture<Void> lastToken = new CompletableFuture<>();

        model.chat(ChatRequest.builder().messages(UserMessage.from("Hi")).build(), new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(PartialResponse partialResponse, PartialResponseContext context) {
                signals.add(partialResponse.text());
                if (partialResponse.text().equals("lo")) {
                    context.streamingHandle().cancel();
                    lastToken.complete(null);
                }
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                signals.add("complete");
            }

            @Override
            public void onError(Throwable error) {
                signals.add("error");
            }
        });

        lastToken.get(5, SECONDS);
        Thread.sleep(100); // a callback after cancel() would arrive on the streaming thread
        assertThat(signals).containsExactly("Hel", "lo");
    }

    @Test
    void should_pass_an_assertion_error_of_the_handler_to_on_error() throws Exception {

        StreamingChatModelMock model = StreamingChatModelMock.thatStreams("Hi");
        CompletableFuture<Throwable> error = new CompletableFuture<>();
        AssertionError failure = new AssertionError("wrong token");

        model.chat(ChatRequest.builder().messages(UserMessage.from("Hi")).build(), new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                throw failure;
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {}

            @Override
            public void onError(Throwable throwable) {
                error.complete(throwable);
            }
        });

        assertThat(error.get(5, SECONDS)).isSameAs(failure);
    }

    @Test
    void should_notify_listeners_of_a_stream_that_breaks() throws Exception {

        List<String> events = new CopyOnWriteArrayList<>();
        RuntimeException failure = new IllegalStateException("connection reset");
        StreamingChatModelMock model = StreamingChatModelMock.thatStreams("Hi")
                .thenFailsWith(failure)
                .withListeners(new ChatModelListener() {
                    @Override
                    public void onResponse(ChatModelResponseContext responseContext) {
                        events.add("response");
                    }

                    @Override
                    public void onError(ChatModelErrorContext errorContext) {
                        events.add("error");
                    }
                });

        assertThat(stream(model).error()).isSameAs(failure);
        assertThat(events).containsExactly("error");
    }

    @Test
    void should_fail_when_there_is_not_exactly_one_request() {

        StreamingChatModelMock model = StreamingChatModelMock.thatStreams("Hi");

        assertThatThrownBy(model::request)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Expected exactly 1 chat request, but received 0");
    }

    private record Streamed(List<String> tokens, ChatResponse response, Throwable error) {}

    private static Streamed stream(StreamingChatModelMock model) throws Exception {
        List<String> tokens = new CopyOnWriteArrayList<>();
        CompletableFuture<Streamed> result = new CompletableFuture<>();
        model.chat(ChatRequest.builder().messages(UserMessage.from("Hi")).build(), new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                tokens.add(partialResponse);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                result.complete(new Streamed(tokens, completeResponse, null));
            }

            @Override
            public void onError(Throwable error) {
                result.complete(new Streamed(tokens, null, error));
            }
        });
        return result.get(5, SECONDS);
    }

    private static void subscribe(StreamingChatModelMock model, List<Object> signals) {
        model.chat(ChatRequest.builder().messages(UserMessage.from("Hi")).build())
                .subscribe(new Flow.Subscriber<>() {

                    @Override
                    public void onSubscribe(Flow.Subscription subscription) {
                        subscription.request(Long.MAX_VALUE);
                    }

                    @Override
                    public void onNext(ChatModelStreamingEvent event) {
                        signals.add(event);
                    }

                    @Override
                    public void onError(Throwable error) {
                        signals.add(error);
                    }

                    @Override
                    public void onComplete() {}
                });
    }

    private static ChatResponse completeResponse(List<ChatModelStreamingEvent> events) {
        return ((CompleteResponse) events.get(events.size() - 1)).chatResponse();
    }

    private static String chat(StreamingChatModelMock model) throws Exception {
        return chatResponse(model).aiMessage().text();
    }

    private static ChatResponse chatResponse(StreamingChatModelMock model) throws Exception {
        CompletableFuture<ChatResponse> response = new CompletableFuture<>();
        model.chat(ChatRequest.builder().messages(UserMessage.from("Hello")).build(), new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String partialResponse) {}

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                response.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                response.completeExceptionally(error);
            }
        });
        return response.get(10, SECONDS);
    }
}
