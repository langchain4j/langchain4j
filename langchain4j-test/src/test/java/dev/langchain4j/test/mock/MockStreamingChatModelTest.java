package dev.langchain4j.test.mock;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
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

public class MockStreamingChatModelTest {

    @Test
    void should_stream_consecutive_spaces_as_separate_tokens() throws Exception {

        MockStreamingChatModel model = MockStreamingChatModel.thatStreams("a  b");

        assertThat(stream(model).tokens()).containsExactly("a", " ", " ", "b");
    }

    @Test
    void should_complete_once_when_subscriber_requests_one_event_at_a_time() {

        MockStreamingChatModel model = MockStreamingChatModel.thatStreams(AiMessage.from("Hi"));
        List<ChatModelStreamingEvent> events = new CopyOnWriteArrayList<>();
        AtomicInteger completions = new AtomicInteger();
        CompletableFuture<Void> done = new CompletableFuture<>();

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
                        done.completeExceptionally(error);
                    }

                    @Override
                    public void onComplete() {
                        completions.incrementAndGet();
                        done.complete(null);
                    }
                });

        assertThat(done).succeedsWithin(5, SECONDS);
        assertThat(events).hasSize(3).last().isInstanceOf(CompleteResponse.class);
        assertThat(completions).hasValue(1);
    }

    @Test
    void should_publish_on_a_new_thread_of_the_mock_for_each_subscription() {

        MockStreamingChatModel model = MockStreamingChatModel.thatStreams("Hi").thenRepeats();

        Thread first = publishingThread(model);
        Thread second = publishingThread(model);

        assertThat(first).isNotSameAs(Thread.currentThread()).isNotSameAs(second);
        assertThat(first.getName()).isEqualTo("MockStreamingChatModel");
        assertThat(second.getName()).isEqualTo("MockStreamingChatModel");
    }

    private static Thread publishingThread(MockStreamingChatModel model) {
        List<Thread> threads = new CopyOnWriteArrayList<>();
        CompletableFuture<Void> done = new CompletableFuture<>();
        model.chat(ChatRequest.builder().messages(UserMessage.from("Hi")).build())
                .subscribe(new Flow.Subscriber<>() {

                    @Override
                    public void onSubscribe(Flow.Subscription subscription) {
                        subscription.request(Long.MAX_VALUE);
                    }

                    @Override
                    public void onNext(ChatModelStreamingEvent event) {
                        threads.add(Thread.currentThread());
                    }

                    @Override
                    public void onError(Throwable error) {
                        done.completeExceptionally(error);
                    }

                    @Override
                    public void onComplete() {
                        threads.add(Thread.currentThread());
                        done.complete(null);
                    }
                });
        assertThat(done).succeedsWithin(5, SECONDS);
        assertThat(threads).hasSize(4).containsOnly(threads.get(0));
        return threads.get(0);
    }

    @Test
    void should_stream_one_response_per_step_and_report_a_request_after_the_last_one_to_on_error() throws Exception {

        MockStreamingChatModel model = MockStreamingChatModel.thatStreams(AiMessage.from("Hi"));

        assertThat(chat(model)).isEqualTo("Hi");
        Streamed second = stream(model);
        assertThat(second.tokens()).isEmpty();
        assertThat(second.error())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("MockStreamingChatModel received request 2, but its script has only 1 step. Script one"
                        + " more step, or end the script with thenRepeats() to answer every further request with its"
                        + " last step. If the mock is shared between tests, for example as a bean, create a new one for"
                        + " each test. The last message of request 2: USER \"Hi\"");
    }

    @Test
    void should_stream_several_responses_in_order_and_fail_after_the_last_one() throws Exception {

        MockStreamingChatModel model =
                MockStreamingChatModel.thatStreams(AiMessage.from("first"), AiMessage.from("second"));

        assertThat(chat(model)).isEqualTo("first");
        assertThat(chat(model)).isEqualTo("second");
        assertThat(stream(model).error()).hasMessageContaining("received request 3, but its script has only 2 steps");
    }

    @Test
    void should_ask_for_the_answer_after_the_tool_results_when_the_script_ends_with_a_tool_call() throws Exception {

        AiMessage toolCall = AiMessage.from(ToolExecutionRequest.builder()
                .id("1")
                .name("getWeather")
                .arguments("{}")
                .build());
        MockStreamingChatModel model = MockStreamingChatModel.thatStreams(AiMessage.from("first"), toolCall);

        assertThat(chat(model)).isEqualTo("first");
        chat(model);
        assertThat(stream(model).error())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(
                        "The last step called a tool, so script the answer that follows the tool results.");
    }

    @Test
    void should_repeat_a_single_answer_for_every_request() throws Exception {

        MockStreamingChatModel model = MockStreamingChatModel.thatStreams("Hi").thenRepeats();

        assertThat(chat(model)).isEqualTo("Hi");
        assertThat(chat(model)).isEqualTo("Hi");
        assertThat(chat(model)).isEqualTo("Hi");
    }

    @Test
    void should_repeat_only_the_last_step_of_a_script() throws Exception {

        MockStreamingChatModel model =
                MockStreamingChatModel.thatStreams("first").thenStreams("second").thenRepeats();

        assertThat(chat(model)).isEqualTo("first");
        assertThat(chat(model)).isEqualTo("second");
        assertThat(chat(model)).isEqualTo("second");
        assertThat(chat(model)).isEqualTo("second");
    }

    @Test
    void should_not_repeat_a_last_step_that_calls_a_tool() {

        assertThatThrownBy(() -> MockStreamingChatModel.thatCallsTool("getWeather", Map.of()).thenRepeats())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot repeat a step that calls a tool");
    }

    @Test
    void should_not_accept_steps_or_a_failure_after_repeating() {

        assertThatThrownBy(() -> MockStreamingChatModel.thatStreams("first").thenRepeats().thenStreams("second"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("thenRepeats() must be the last call of the script");
        assertThatThrownBy(() -> MockStreamingChatModel.thatStreams("first")
                        .thenRepeats()
                        .andFails(new RuntimeException()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("thenRepeats() must be the last call of the script");
    }

    @Test
    void should_compute_the_answer_of_a_step_and_then_take_the_next_steps() throws Exception {

        AtomicInteger calls = new AtomicInteger();
        MockStreamingChatModel model = MockStreamingChatModel.thatStreams(
                        request -> AiMessage.from("computed " + calls.incrementAndGet()))
                .thenStreams("second");

        assertThat(chat(model)).isEqualTo("computed 1");
        assertThat(chat(model)).isEqualTo("second");
        assertThat(stream(model).error()).hasMessageContaining("received request 3, but its script has only 2 steps");
    }

    @Test
    void should_compute_every_answer_when_the_computed_step_repeats() throws Exception {

        AtomicInteger calls = new AtomicInteger();
        MockStreamingChatModel model = MockStreamingChatModel.thatStreams(
                        request -> AiMessage.from("answer " + calls.incrementAndGet()))
                .thenRepeats();

        assertThat(chat(model)).isEqualTo("answer 1");
        assertThat(chat(model)).isEqualTo("answer 2");
        assertThat(chat(model)).isEqualTo("answer 3");
    }

    @Test
    void should_stream_given_tokens() throws Exception {

        MockStreamingChatModel model = MockStreamingChatModel.thatStreamsTokens("Hel", "lo");
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

        MockStreamingChatModel model = MockStreamingChatModel.thatStreamsTokens("Hel", "lo");

        List<ChatModelStreamingEvent> events = collect(model);

        assertThat(events).hasSize(3);
        assertThat(events.subList(0, 2))
                .extracting(event -> ((PartialResponse) event).text())
                .containsExactly("Hel", "lo");
        assertThat(((CompleteResponse) events.get(2)).chatResponse().aiMessage().text())
                .isEqualTo("Hello");
    }

    @Test
    void should_publish_the_given_tokens_again_for_every_request() {

        MockStreamingChatModel model = MockStreamingChatModel.thatStreamsTokens("Hel", "lo").thenRepeats();

        collect(model);
        List<ChatModelStreamingEvent> events = collect(model);

        assertThat(events.subList(0, 2))
                .extracting(event -> ((PartialResponse) event).text())
                .containsExactly("Hel", "lo");
    }

    @Test
    void should_publish_one_token_per_character_when_built_from_ai_message() {

        MockStreamingChatModel model = MockStreamingChatModel.thatStreams(AiMessage.from("Hello"));

        List<ChatModelStreamingEvent> events = collect(model);

        assertThat(events).hasSize(6);
        assertThat(events.subList(0, 5))
                .extracting(event -> ((PartialResponse) event).text())
                .containsExactly("H", "e", "l", "l", "o");
    }

    @Test
    void should_stream_a_text_one_character_per_token() throws Exception {

        MockStreamingChatModel model = MockStreamingChatModel.thatStreams("Hello").thenStreams("Hi");

        Streamed first = stream(model);
        assertThat(first.tokens()).containsExactly("H", "e", "l", "l", "o");
        assertThat(first.response().aiMessage().text()).isEqualTo("Hello");
        assertThat(stream(model).tokens()).containsExactly("H", "i");
    }

    @Test
    void should_stream_exactly_the_given_tokens_in_a_later_step() throws Exception {

        MockStreamingChatModel model = MockStreamingChatModel.thatStreams("Hi").thenStreamsTokens("Hel", "lo");

        stream(model);
        Streamed second = stream(model);
        assertThat(second.tokens()).containsExactly("Hel", "lo");
        assertThat(second.response().aiMessage().text()).isEqualTo("Hello");
    }

    private static List<ChatModelStreamingEvent> collect(MockStreamingChatModel model) {
        List<ChatModelStreamingEvent> events = new CopyOnWriteArrayList<>();
        CompletableFuture<Void> done = new CompletableFuture<>();
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
                        done.completeExceptionally(error);
                    }

                    @Override
                    public void onComplete() {
                        done.complete(null);
                    }
                });
        assertThat(done).succeedsWithin(5, SECONDS);
        return events;
    }

    @Test
    void should_complete_with_exactly_the_given_response() throws Exception {

        ChatResponse given = ChatResponse.builder()
                .aiMessage(AiMessage.from("Hi"))
                .modelName("model")
                .tokenUsage(new TokenUsage(1, 2))
                .finishReason(FinishReason.LENGTH)
                .build();
        MockStreamingChatModel model = MockStreamingChatModel.thatStreams(given).thenStreams(given);

        Streamed streamed = stream(model);
        assertThat(streamed.tokens()).containsExactly("H", "i");
        assertThat(streamed.response().metadata()).isEqualTo(given.metadata());
        assertThat(completeResponse(collect(model)).metadata()).isEqualTo(given.metadata());
    }

    @Test
    void should_report_the_finish_reason_of_a_provider_and_no_token_usage_by_default() throws Exception {

        MockStreamingChatModel model = MockStreamingChatModel.thatCallsTool("getWeather", Map.of())
                .thenStreams("Sunny");

        ChatResponse toolCall = chatResponse(model);
        assertThat(toolCall.finishReason()).isEqualTo(FinishReason.TOOL_EXECUTION);
        assertThat(toolCall.tokenUsage()).isNull();
        assertThat(completeResponse(collect(model)).finishReason()).isEqualTo(FinishReason.STOP);
    }

    @Test
    void should_report_the_model_name_of_the_request() throws Exception {

        MockStreamingChatModel model = MockStreamingChatModel.thatStreams("Hi").thenRepeats();

        ChatResponse response = chatResponse(
                model,
                ChatRequest.builder()
                        .messages(UserMessage.from("Hello"))
                        .modelName("gpt-test")
                        .build());

        assertThat(response.modelName()).isEqualTo("gpt-test");
        assertThat(chatResponse(model).modelName()).isNull();
    }

    @Test
    void should_report_the_default_token_usage_except_in_a_whole_response() throws Exception {

        TokenUsage tokenUsage = new TokenUsage(10, 5);
        ChatResponse given = ChatResponse.builder()
                .aiMessage(AiMessage.from("given"))
                .modelName("given-model")
                .tokenUsage(new TokenUsage(1, 1))
                .build();
        MockStreamingChatModel model = MockStreamingChatModel.thatStreams(request -> AiMessage.from("computed"))
                .thenStreamsTokens("Sun", "ny")
                .thenStreams(given)
                .withTokenUsage(tokenUsage);

        assertThat(chatResponse(model).tokenUsage()).isEqualTo(tokenUsage);
        assertThat(completeResponse(collect(model)).tokenUsage()).isEqualTo(tokenUsage);
        assertThat(chatResponse(model).metadata()).isEqualTo(given.metadata());
    }

    @Test
    void should_take_the_listeners_as_a_list() {

        ChatModelListener listener = new ChatModelListener() {};
        List<ChatModelListener> listeners = new ArrayList<>(List.of(listener));
        MockStreamingChatModel model = MockStreamingChatModel.thatStreams("Hi").withListeners(listeners);

        listeners.clear();

        assertThat(model.listeners()).containsExactly(listener);
    }

    @Test
    void should_call_a_tool_and_then_stream() throws Exception {

        MockStreamingChatModel model = MockStreamingChatModel.thatCallsTool("cancelBooking", Map.of("id", 7))
                .thenStreamsTokens("can", "celled");

        ToolExecutionRequest toolCall =
                chatResponse(model).aiMessage().toolExecutionRequests().get(0);
        assertThat(toolCall.name()).isEqualTo("cancelBooking");
        assertThat(toolCall.arguments()).isEqualTo("{\"id\":7}");

        Streamed streamed = stream(model);
        assertThat(streamed.tokens()).containsExactly("can", "celled");
        assertThat(streamed.response().aiMessage().text()).isEqualTo("cancelled");
    }

    @Test
    void should_take_the_steps_in_order_and_fail_after_the_last_one() throws Exception {

        MockStreamingChatModel model = MockStreamingChatModel.thatStreamsTokens("fi", "rst")
                .thenCallsTool("getWeather", Map.of())
                .thenStreams(AiMessage.from("third"));

        assertThat(chat(model)).isEqualTo("first");
        assertThat(chatResponse(model).aiMessage().toolExecutionRequests()).hasSize(1);
        assertThat(chat(model)).isEqualTo("third");
        assertThat(stream(model).error()).hasMessageContaining("received request 4, but its script has only 3 steps");
    }

    @Test
    void should_fail_after_the_tokens_when_the_stream_breaks() throws Exception {

        RuntimeException failure = new IllegalStateException("connection reset");
        MockStreamingChatModel model =
                MockStreamingChatModel.thatStreamsTokens("Hel", "lo").andFails(failure).thenRepeats();

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
        MockStreamingChatModel model = MockStreamingChatModel.thatStreamsTokens("Hel", "lo").andFails(failure);

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
        MockStreamingChatModel model = MockStreamingChatModel.thatStreams("broken")
                .andFails(failure)
                .thenStreams("fine");

        assertThat(stream(model).error()).isSameAs(failure);
        assertThat(chat(model)).isEqualTo("fine");
    }

    @Test
    void should_fail_before_streaming_with_a_scripted_exception_and_then_stream() throws Exception {

        RuntimeException failure = new IllegalStateException("temporary failure");
        MockStreamingChatModel model = MockStreamingChatModel.thatFails(failure).thenStreams("recovered");

        Streamed failed = stream(model);
        assertThat(failed.tokens()).isEmpty();
        assertThat(failed.error()).isSameAs(failure);
        assertThat(chat(model)).isEqualTo("recovered");
    }

    @Test
    void should_publish_only_the_error_of_a_step_that_throws() {

        RuntimeException failure = new IllegalStateException("down");
        MockStreamingChatModel model = MockStreamingChatModel.thatFails(failure);

        List<Object> signals = new ArrayList<>();
        subscribe(model, signals);

        assertThat(signals).containsExactly(failure);
    }

    @Test
    void should_signal_a_failure_before_anything_is_streamed_without_waiting_for_demand() {

        RuntimeException failure = new IllegalStateException("down");
        List<Object> signals = new CopyOnWriteArrayList<>();
        CompletableFuture<Void> done = new CompletableFuture<>();

        MockStreamingChatModel.thatFails(failure)
                .chat(ChatRequest.builder().messages(UserMessage.from("Hi")).build())
                .subscribe(new Flow.Subscriber<>() {

                    @Override
                    public void onSubscribe(Flow.Subscription subscription) {
                        signals.add("subscribed"); // requests nothing
                    }

                    @Override
                    public void onNext(ChatModelStreamingEvent event) {
                        signals.add(event);
                    }

                    @Override
                    public void onError(Throwable error) {
                        signals.add(error);
                        done.complete(null);
                    }

                    @Override
                    public void onComplete() {
                        signals.add("complete");
                        done.complete(null);
                    }
                });

        assertThat(done).succeedsWithin(5, SECONDS);

        assertThat(signals).containsExactly("subscribed", failure);
    }

    @Test
    void should_fail_the_first_request_with_the_given_exception() throws Exception {

        RuntimeException failure = new IllegalArgumentException("invalid");
        MockStreamingChatModel model = MockStreamingChatModel.thatFails(failure);

        assertThat(stream(model).error()).isSameAs(failure);
        assertThat(stream(model).error()).hasMessageContaining("received request 2");
    }

    @Test
    void should_fail_every_request_with_the_given_exception_when_repeated() throws Exception {

        RuntimeException failure = new IllegalArgumentException("invalid");
        MockStreamingChatModel model = MockStreamingChatModel.thatFails(failure).thenRepeats();

        assertThat(stream(model).error()).isSameAs(failure);
        assertThat(stream(model).error()).isSameAs(failure);
    }

    @Test
    void should_stream_a_collection_of_messages_in_order() throws Exception {

        MockStreamingChatModel model =
                MockStreamingChatModel.thatStreams(List.of(AiMessage.from("first"), AiMessage.from("second")));

        assertThat(chat(model)).isEqualTo("first");
        assertThat(chat(model)).isEqualTo("second");
    }

    @Test
    void should_not_accept_a_failure_after_the_tokens_on_a_mock_that_always_fails() {

        assertThatThrownBy(() -> MockStreamingChatModel.thatFails(new RuntimeException("a"))
                        .andFails(new RuntimeException("b")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("andFails");
        assertThatThrownBy(() -> MockStreamingChatModel.thatStreams("a")
                        .thenFails(new RuntimeException("a"))
                        .andFails(new RuntimeException("b")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void should_not_accept_a_second_failure_on_the_same_response() {

        assertThatThrownBy(() -> MockStreamingChatModel.thatStreams("a")
                        .andFails(new RuntimeException("first"))
                        .andFails(new RuntimeException("second")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("andFails");
    }

    @Test
    void should_not_accept_a_failure_after_the_tokens_of_a_tool_call() {

        assertThatThrownBy(() -> MockStreamingChatModel.thatCallsTool("a", Map.of())
                        .andFails(new RuntimeException()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no text or thinking to stream");
    }

    @Test
    void should_not_accept_a_failure_after_the_tokens_on_a_mock_that_computes_its_answers() {

        assertThatThrownBy(() -> MockStreamingChatModel.thatStreams(request -> AiMessage.from("Hi"))
                        .andFails(new RuntimeException()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("computes its answer");
    }

    @Test
    void should_not_accept_a_failure_after_all_steps_were_taken() throws Exception {

        MockStreamingChatModel model = MockStreamingChatModel.thatStreams("a");
        chat(model);

        assertThatThrownBy(() -> model.andFails(new RuntimeException()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("andFails");
    }

    @Test
    void should_compute_the_answer_from_the_request() throws Exception {

        MockStreamingChatModel model = MockStreamingChatModel.thatStreams(
                request -> AiMessage.from("You said " + ((UserMessage) request.messages().get(0)).singleText()));

        assertThat(stream(model).response().aiMessage().text()).isEqualTo("You said Hi");
    }

    @Test
    void should_report_a_failing_computation_through_on_error() throws Exception {

        RuntimeException failure = new IllegalStateException("cannot answer");
        MockStreamingChatModel model = MockStreamingChatModel.thatStreams(request -> {
            throw failure;
        });

        assertThat(stream(model).error()).isSameAs(failure);
    }

    @Test
    void should_signal_an_exhausted_script_to_the_subscriber() throws Exception {

        MockStreamingChatModel model = MockStreamingChatModel.thatCallsTool("getWeather", Map.of());
        chat(model);

        List<Object> signals = new ArrayList<>();
        subscribe(model, signals);

        assertThat(signals).singleElement().isInstanceOfSatisfying(IllegalStateException.class, error -> assertThat(
                        error)
                .hasMessageContaining("received request 2")
                .hasMessageContaining("script the answer that follows the tool results"));
    }

    @Test
    void should_publish_complete_tool_calls_before_the_complete_response() {

        ToolExecutionRequest first = MockChatModel.toolCall("a", Map.of("x", 1));
        ToolExecutionRequest second = MockChatModel.toolCall("b", Map.of("y", 2));
        MockStreamingChatModel model =
                MockStreamingChatModel.thatStreams(AiMessage.from(first, second), AiMessage.from("done"));

        List<ChatModelStreamingEvent> events = collect(model);

        assertThat(events.stream().filter(CompleteToolCall.class::isInstance).map(CompleteToolCall.class::cast))
                .extracting(CompleteToolCall::toolExecutionRequest)
                .extracting(ToolExecutionRequest::id, ToolExecutionRequest::name, ToolExecutionRequest::arguments)
                .containsExactly(
                        tuple("tool_call_1", first.name(), first.arguments()),
                        tuple("tool_call_2", second.name(), second.arguments()));
        assertThat(events.get(events.size() - 1)).isInstanceOf(CompleteResponse.class);
    }

    @Test
    void should_publish_thinking_then_text_then_partial_and_complete_tool_calls() {

        AiMessage answer = AiMessage.builder()
                .thinking("hm")
                .text("ok")
                .toolExecutionRequests(List.of(MockChatModel.toolCall("a", "{}")))
                .build();
        MockStreamingChatModel model = MockStreamingChatModel.thatStreams(answer, AiMessage.from("done"));

        List<ChatModelStreamingEvent> events = collect(model);

        assertThat(events).hasSize(8);
        assertThat(events.subList(0, 2)).containsExactly(new PartialThinking("h"), new PartialThinking("m"));
        assertThat(events.subList(2, 4)).containsExactly(new PartialResponse("o"), new PartialResponse("k"));
        assertThat(events.subList(4, 6))
                .extracting(event -> ((PartialToolCall) event).partialArguments())
                .containsExactly("{", "}");
        assertThat(((PartialToolCall) events.get(4)).name()).isEqualTo("a");
        assertThat(((PartialToolCall) events.get(4)).id()).isEqualTo("tool_call_1");
        assertThat(((PartialToolCall) events.get(4)).index()).isZero();
        assertThat(events.get(6)).isInstanceOf(CompleteToolCall.class);
        assertThat(events.get(7)).isInstanceOf(CompleteResponse.class);
    }

    @Test
    void should_call_the_handler_with_thinking_then_text_then_partial_and_complete_tool_calls() throws Exception {

        AiMessage answer = AiMessage.builder()
                .thinking("hm")
                .text("ok")
                .toolExecutionRequests(List.of(MockChatModel.toolCall("a", "{}")))
                .build();
        MockStreamingChatModel model = MockStreamingChatModel.thatStreams(answer, AiMessage.from("done"));
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

        MockStreamingChatModel breaking =
                MockStreamingChatModel.thatStreams(List.of(answer)).andFails(failure);
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

        MockStreamingChatModel model =
                MockStreamingChatModel.thatCallsTool("a", "{not json").thenCallsTool("b", "[]");

        assertThat(chatResponse(model).aiMessage().toolExecutionRequests().get(0).arguments())
                .isEqualTo("{not json");
        assertThat(chatResponse(model).aiMessage().toolExecutionRequests().get(0).arguments())
                .isEqualTo("[]");
    }

    @Test
    void should_repeat_a_last_step_that_fails() throws Exception {

        RuntimeException failure = new IllegalStateException("down");
        MockStreamingChatModel model =
                MockStreamingChatModel.thatStreams("first").thenFails(failure).thenRepeats();

        assertThat(chat(model)).isEqualTo("first");
        assertThat(stream(model).error()).isSameAs(failure);
        assertThat(stream(model).error()).isSameAs(failure);
    }

    @Test
    void should_not_accept_an_empty_list_of_messages() {

        assertThatThrownBy(() -> MockStreamingChatModel.thatStreams(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("aiMessages");
    }

    @Test
    void should_not_accept_null_arguments() {

        assertThatThrownBy(() -> MockStreamingChatModel.thatFails(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MockStreamingChatModel.thatStreams("Hi").andFails(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MockStreamingChatModel.thatStreams((String) null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MockStreamingChatModel.thatStreamsTokens("Hel", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MockStreamingChatModel.thatStreamsTokens((String[]) null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_not_split_a_character_outside_the_basic_plane_into_two_tokens() throws Exception {

        assertThat(ScriptedResponses.splitIntoTokens("a\uD83D\uDE00")).containsExactly("a", "\uD83D\uDE00");
        assertThat(stream(MockStreamingChatModel.thatStreams("a\uD83D\uDE00")).tokens())
                .containsExactly("a", "\uD83D\uDE00");
    }

    @Test
    void should_stop_after_the_handle_was_cancelled_during_the_last_token() throws Exception {

        MockStreamingChatModel model = MockStreamingChatModel.thatStreamsTokens("Hel", "lo");
        List<String> signals = new CopyOnWriteArrayList<>();
        CompletableFuture<Thread> streamingThread = new CompletableFuture<>();

        model.chat(ChatRequest.builder().messages(UserMessage.from("Hi")).build(), new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(PartialResponse partialResponse, PartialResponseContext context) {
                signals.add(partialResponse.text());
                if (partialResponse.text().equals("lo")) {
                    context.streamingHandle().cancel();
                    streamingThread.complete(Thread.currentThread());
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

        // once the streaming thread has ended, every callback it would make has run
        streamingThread.get(5, SECONDS).join(5_000);
        assertThat(signals).containsExactly("Hel", "lo");
    }

    @Test
    void should_not_accept_an_empty_token() {

        assertThatThrownBy(() -> MockStreamingChatModel.thatStreamsTokens("Hel", "", "lo"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("A token cannot be empty");
        assertThatThrownBy(() -> MockStreamingChatModel.thatStreams("Hi").thenStreamsTokens(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("A token cannot be empty");
    }

    @Test
    void should_stream_an_answer_without_text_when_given_no_tokens() throws Exception {

        Streamed streamed = stream(MockStreamingChatModel.thatStreamsTokens());

        assertThat(streamed.tokens()).isEmpty();
        assertThat(streamed.error()).isNull();
        assertThat(streamed.response().aiMessage().text()).isNull();
    }

    @Test
    void should_stream_a_tool_call_without_a_name_on_both_paths() throws Exception {

        AiMessage toolCallWithoutName = AiMessage.from(ToolExecutionRequest.builder()
                .id("1")
                .name(" ")
                .arguments("{}")
                .build());

        Streamed streamed = stream(MockStreamingChatModel.thatStreams(toolCallWithoutName));
        assertThat(streamed.error()).isNull();
        assertThat(streamed.response().aiMessage().toolExecutionRequests()).hasSize(1);

        List<Object> signals = new CopyOnWriteArrayList<>();
        subscribe(MockStreamingChatModel.thatStreams(toolCallWithoutName), signals);
        assertThat(signals).noneMatch(PartialToolCall.class::isInstance);
        assertThat(signals).anyMatch(CompleteToolCall.class::isInstance);
        assertThat(signals).last().isInstanceOf(CompleteResponse.class);
    }

    @Test
    void should_report_a_failure_before_streaming_once_on_another_thread_and_log_a_failing_on_error() throws Exception {

        RuntimeException failure = new IllegalStateException("down");
        MockStreamingChatModel model = MockStreamingChatModel.thatFails(failure);
        CompletableFuture<Thread> errorThread = new CompletableFuture<>();
        List<Throwable> errors = new CopyOnWriteArrayList<>();

        model.chat(ChatRequest.builder().messages(UserMessage.from("Hi")).build(), new StreamingChatResponseHandler() {

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {}

            @Override
            public void onError(Throwable error) {
                errors.add(error);
                errorThread.complete(Thread.currentThread());
                throw new IllegalStateException("a failing onError is logged, not thrown");
            }
        });

        Thread thread = errorThread.get(5, SECONDS);
        assertThat(thread).isNotSameAs(Thread.currentThread());
        // once the streaming thread has ended, every callback it would make has run
        thread.join(5_000);
        assertThat(errors).singleElement().isSameAs(failure);
    }

    @Test
    void should_fail_after_the_text_of_a_step_that_also_calls_a_tool() {

        RuntimeException failure = new IllegalStateException("connection reset");
        AiMessage textAndToolCall = AiMessage.from(
                "Let me check.",
                List.of(ToolExecutionRequest.builder()
                        .id("1")
                        .name("getWeather")
                        .arguments("{}")
                        .build()));
        MockStreamingChatModel model = MockStreamingChatModel.thatStreams(textAndToolCall).andFails(failure);
        List<Object> signals = new CopyOnWriteArrayList<>();
        CompletableFuture<Void> failed = new CompletableFuture<>();

        model.chat(ChatRequest.builder().messages(UserMessage.from("Hi")).build(), new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String partialResponse) {
                signals.add(partialResponse);
            }

            @Override
            public void onPartialToolCall(PartialToolCall partialToolCall) {
                signals.add(partialToolCall);
            }

            @Override
            public void onCompleteToolCall(CompleteToolCall completeToolCall) {
                signals.add(completeToolCall);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                signals.add(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                signals.add(error);
                failed.complete(null);
            }
        });

        assertThat(failed).succeedsWithin(5, SECONDS);
        assertThat(String.join("", signals.stream().filter(String.class::isInstance).map(String.class::cast).toList()))
                .isEqualTo("Let me check.");
        assertThat(signals).last().isSameAs(failure);
        assertThat(signals).noneMatch(signal -> signal instanceof PartialToolCall
                || signal instanceof CompleteToolCall
                || signal instanceof ChatResponse);
    }

    @Test
    void should_not_accept_a_failure_after_a_step_without_text() {

        assertThatThrownBy(() -> MockStreamingChatModel.thatStreams("").andFails(new RuntimeException()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no text or thinking to stream");
        assertThatThrownBy(() -> MockStreamingChatModel.thatStreamsTokens().andFails(new RuntimeException()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no text or thinking to stream");
    }

    @Test
    void should_signal_a_demand_that_is_not_positive_to_the_subscriber() {

        List<Object> signals = new CopyOnWriteArrayList<>();
        CompletableFuture<Void> done = new CompletableFuture<>();

        MockStreamingChatModel.thatStreams("Hi")
                .chat(ChatRequest.builder().messages(UserMessage.from("Hi")).build())
                .subscribe(new Flow.Subscriber<>() {

                    @Override
                    public void onSubscribe(Flow.Subscription subscription) {
                        subscription.request(0);
                        subscription.request(1);
                    }

                    @Override
                    public void onNext(ChatModelStreamingEvent event) {
                        signals.add(event);
                    }

                    @Override
                    public void onError(Throwable error) {
                        signals.add(error);
                        done.complete(null);
                    }

                    @Override
                    public void onComplete() {
                        signals.add("complete");
                        done.complete(null);
                    }
                });

        assertThat(done).succeedsWithin(5, SECONDS);

        assertThat(signals).singleElement().isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_stop_emitting_after_the_subscription_was_cancelled() throws Exception {

        List<Object> signals = new CopyOnWriteArrayList<>();
        CompletableFuture<Thread> streamingThread = new CompletableFuture<>();

        MockStreamingChatModel.thatStreams("Hello")
                .chat(ChatRequest.builder().messages(UserMessage.from("Hi")).build())
                .subscribe(new Flow.Subscriber<>() {

                    Flow.Subscription subscription;

                    @Override
                    public void onSubscribe(Flow.Subscription subscription) {
                        this.subscription = subscription;
                        subscription.request(Long.MAX_VALUE);
                    }

                    @Override
                    public void onNext(ChatModelStreamingEvent event) {
                        signals.add(event);
                        subscription.cancel();
                        streamingThread.complete(Thread.currentThread());
                    }

                    @Override
                    public void onError(Throwable error) {
                        signals.add(error);
                    }

                    @Override
                    public void onComplete() {
                        signals.add("complete");
                    }
                });

        // once the streaming thread has ended, every signal it would send has been sent
        streamingThread.get(5, SECONDS).join(5_000);
        assertThat(signals).singleElement().isInstanceOf(PartialResponse.class);
    }

    @Test
    void should_signal_an_assertion_error_of_a_computed_answer_to_the_subscriber_instead_of_throwing() {

        AssertionError failure = new AssertionError("unexpected request");
        List<Object> signals = new CopyOnWriteArrayList<>();
        CompletableFuture<Void> done = new CompletableFuture<>();

        MockStreamingChatModel.thatStreams(request -> {
                    throw failure;
                })
                .chat(ChatRequest.builder().messages(UserMessage.from("Hi")).build())
                .subscribe(new Flow.Subscriber<>() {

                    @Override
                    public void onSubscribe(Flow.Subscription subscription) {
                        signals.add("subscribed");
                        subscription.request(Long.MAX_VALUE);
                    }

                    @Override
                    public void onNext(ChatModelStreamingEvent event) {
                        signals.add(event);
                    }

                    @Override
                    public void onError(Throwable error) {
                        signals.add(error);
                        done.complete(null);
                    }

                    @Override
                    public void onComplete() {
                        signals.add("complete");
                        done.complete(null);
                    }
                });

        assertThat(done).succeedsWithin(5, SECONDS);

        assertThat(signals).containsExactly("subscribed", failure);
    }

    @Test
    void should_cancel_the_stream_when_the_subscriber_throws_from_on_next() throws Exception {

        List<Object> signals = new CopyOnWriteArrayList<>();
        RuntimeException failure = new IllegalStateException("broken subscriber");
        List<Flow.Subscription> subscriptions = new ArrayList<>();
        CompletableFuture<Thread> streamingThread = new CompletableFuture<>();

        MockStreamingChatModel.thatStreams("Hello")
                .chat(ChatRequest.builder().messages(UserMessage.from("Hi")).build())
                .subscribe(new Flow.Subscriber<>() {

                    @Override
                    public void onSubscribe(Flow.Subscription subscription) {
                        subscriptions.add(subscription);
                    }

                    @Override
                    public void onNext(ChatModelStreamingEvent event) {
                        signals.add(event);
                        streamingThread.complete(Thread.currentThread());
                        throw failure;
                    }

                    @Override
                    public void onError(Throwable error) {
                        signals.add(error);
                    }

                    @Override
                    public void onComplete() {
                        signals.add("complete");
                    }
                });

        Flow.Subscription subscription = subscriptions.get(0);
        subscription.request(1);
        // the failure ends the streaming thread, like an uncaught exception on a provider's I/O thread
        streamingThread.get(5, SECONDS).join(5_000);
        subscription.request(10);

        assertThat(signals).singleElement().isInstanceOf(PartialResponse.class);
    }

    @Test
    void should_pass_an_assertion_error_of_the_handler_to_on_error() throws Exception {

        MockStreamingChatModel model = MockStreamingChatModel.thatStreams("Hi");
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
        MockStreamingChatModel model = MockStreamingChatModel.thatStreams("Hi")
                .andFails(failure)
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

        MockStreamingChatModel model = MockStreamingChatModel.thatStreams("Hi");

        assertThatThrownBy(model::request)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Expected exactly 1 chat request, but received 0");
    }

    @Test
    void should_return_the_last_request() throws Exception {

        MockStreamingChatModel model = MockStreamingChatModel.thatStreams("Hi").thenRepeats();

        assertThatThrownBy(model::lastRequest)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Expected at least 1 chat request, but received none");
        chat(model);
        collect(model);

        assertThat(model.lastRequest()).isSameAs(model.requests().get(1));
    }

    @Test
    void should_stream_exactly_the_given_tokens_given_as_a_list() throws Exception {

        MockStreamingChatModel model = MockStreamingChatModel.thatStreamsTokens(List.of("Hel", "lo"))
                .thenStreamsTokens(List.of("Wor", "ld"));

        assertThat(stream(model).tokens()).containsExactly("Hel", "lo");
        assertThat(stream(model).tokens()).containsExactly("Wor", "ld");
    }

    private record Streamed(List<String> tokens, ChatResponse response, Throwable error) {}

    private static Streamed stream(MockStreamingChatModel model) throws Exception {
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

    /**
     * Subscribes to the events of the next answer, requesting all of them, and waits for the end of the stream.
     */
    private static void subscribe(MockStreamingChatModel model, List<Object> signals) {
        CompletableFuture<Void> done = new CompletableFuture<>();
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
                        done.complete(null);
                    }

                    @Override
                    public void onComplete() {
                        done.complete(null);
                    }
                });
        assertThat(done).succeedsWithin(5, SECONDS);
    }

    private static ChatResponse completeResponse(List<ChatModelStreamingEvent> events) {
        return ((CompleteResponse) events.get(events.size() - 1)).chatResponse();
    }

    private static String chat(MockStreamingChatModel model) throws Exception {
        return chatResponse(model).aiMessage().text();
    }

    private static ChatResponse chatResponse(MockStreamingChatModel model) throws Exception {
        return chatResponse(
                model, ChatRequest.builder().messages(UserMessage.from("Hello")).build());
    }

    private static ChatResponse chatResponse(MockStreamingChatModel model, ChatRequest request) throws Exception {
        CompletableFuture<ChatResponse> response = new CompletableFuture<>();
        model.chat(request, new StreamingChatResponseHandler() {

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
