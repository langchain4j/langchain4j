package dev.langchain4j.test.mock;

import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.Capability;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public class MockStreamingChatModelTest {

    @Test
    void should_keep_every_space_of_the_text_in_the_tokens() throws Exception {

        MockStreamingChatModel model = MockStreamingChatModel.thatResponds("a  b");

        assertThat(stream(model).tokens()).containsExactly("a", " ", " b");
    }

    @Test
    void should_complete_once_when_subscriber_requests_one_event_at_a_time() {

        MockStreamingChatModel model = MockStreamingChatModel.thatResponds(AiMessage.from("Hi there"));
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

        MockStreamingChatModel model = MockStreamingChatModel.thatResponds("Hi there").thenRepeats();

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

        MockStreamingChatModel model = MockStreamingChatModel.thatResponds(AiMessage.from("Hi"));

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
                MockStreamingChatModel.thatResponds(AiMessage.from("first"), AiMessage.from("second"));

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
        MockStreamingChatModel model = MockStreamingChatModel.thatResponds(AiMessage.from("first"), toolCall);

        assertThat(chat(model)).isEqualTo("first");
        chat(model);
        assertThat(stream(model).error())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(
                        "The last step called a tool, so script the answer that follows the tool results.");
    }

    @Test
    void should_repeat_a_single_answer_for_every_request() throws Exception {

        MockStreamingChatModel model = MockStreamingChatModel.thatResponds("Hi").thenRepeats();

        assertThat(chat(model)).isEqualTo("Hi");
        assertThat(chat(model)).isEqualTo("Hi");
        assertThat(chat(model)).isEqualTo("Hi");
    }

    @Test
    void should_repeat_only_the_last_step_of_a_script() throws Exception {

        MockStreamingChatModel model =
                MockStreamingChatModel.thatResponds("first").thenResponds("second").thenRepeats();

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

        assertThatThrownBy(() -> MockStreamingChatModel.thatResponds("first").thenRepeats().thenResponds("second"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("thenRepeats() must be the last call of the script");
        assertThatThrownBy(() -> MockStreamingChatModel.thatResponds("first")
                        .thenRepeats()
                        .andFails(new RuntimeException()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("thenRepeats() must be the last call of the script");
    }

    @Test
    void should_compute_the_answer_of_a_step_and_then_take_the_next_steps() throws Exception {

        AtomicInteger calls = new AtomicInteger();
        MockStreamingChatModel model = MockStreamingChatModel.thatResponds(
                        request -> AiMessage.from("computed " + calls.incrementAndGet()))
                .thenResponds("second");

        assertThat(chat(model)).isEqualTo("computed 1");
        assertThat(chat(model)).isEqualTo("second");
        assertThat(stream(model).error()).hasMessageContaining("received request 3, but its script has only 2 steps");
    }

    @Test
    void should_compute_every_answer_when_the_computed_step_repeats() throws Exception {

        AtomicInteger calls = new AtomicInteger();
        MockStreamingChatModel model = MockStreamingChatModel.thatResponds(
                        request -> AiMessage.from("answer " + calls.incrementAndGet()))
                .thenRepeats();

        assertThat(chat(model)).isEqualTo("answer 1");
        assertThat(chat(model)).isEqualTo("answer 2");
        assertThat(chat(model)).isEqualTo("answer 3");
    }

    @Test
    void should_stream_given_tokens() throws Exception {

        MockStreamingChatModel model = MockStreamingChatModel.thatStreams("Hel", "lo");
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

        MockStreamingChatModel model = MockStreamingChatModel.thatStreams("Hel", "lo");

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

        MockStreamingChatModel model = MockStreamingChatModel.thatStreams("Hel", "lo").thenRepeats();

        collect(model);
        List<ChatModelStreamingEvent> events = collect(model);

        assertThat(events.subList(0, 2))
                .extracting(event -> ((PartialResponse) event).text())
                .containsExactly("Hel", "lo");
    }

    @Test
    void should_publish_word_like_tokens_when_built_from_ai_message() {

        MockStreamingChatModel model = MockStreamingChatModel.thatResponds(AiMessage.from("Hello, world!"));

        List<ChatModelStreamingEvent> events = collect(model);

        assertThat(events).hasSize(5);
        assertThat(events.subList(0, 4))
                .extracting(event -> ((PartialResponse) event).text())
                .containsExactly("Hello", ",", " world", "!");
    }

    @Test
    void should_stream_a_text_in_word_like_tokens() throws Exception {

        MockStreamingChatModel model = MockStreamingChatModel.thatResponds("It's 12345 o'clock.\nNext")
                .thenResponds("Hi");

        Streamed first = stream(model);
        assertThat(first.tokens()).containsExactly("It", "'s", " ", "123", "45", " o", "'clock", ".\n", "Next");
        assertThat(first.response().aiMessage().text()).isEqualTo("It's 12345 o'clock.\nNext");
        assertThat(stream(model).tokens()).containsExactly("Hi");
    }

    @Test
    void should_stream_exactly_the_given_tokens_in_a_later_step() throws Exception {

        MockStreamingChatModel model = MockStreamingChatModel.thatResponds("Hi").thenStreams("Hel", "lo");

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
        MockStreamingChatModel model = MockStreamingChatModel.thatResponds(given).thenResponds(given);

        Streamed streamed = stream(model);
        assertThat(streamed.tokens()).containsExactly("Hi");
        assertThat(streamed.response().metadata()).isEqualTo(given.metadata());
        assertThat(completeResponse(collect(model)).metadata()).isEqualTo(given.metadata());
    }

    @Test
    void should_report_the_finish_reason_of_a_provider_and_no_token_usage_by_default() throws Exception {

        MockStreamingChatModel model = MockStreamingChatModel.thatCallsTool("getWeather", Map.of())
                .thenResponds("Sunny");

        ChatResponse toolCall = chatResponse(model);
        assertThat(toolCall.finishReason()).isEqualTo(FinishReason.TOOL_EXECUTION);
        assertThat(toolCall.tokenUsage()).isNull();
        assertThat(completeResponse(collect(model)).finishReason()).isEqualTo(FinishReason.STOP);
    }

    @Test
    void should_report_the_model_name_of_the_request() throws Exception {

        MockStreamingChatModel model = MockStreamingChatModel.thatResponds("Hi").thenRepeats();

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
        MockStreamingChatModel model = MockStreamingChatModel.thatResponds(request -> AiMessage.from("computed"))
                .thenStreams("Sun", "ny")
                .thenResponds(given)
                .withTokenUsage(tokenUsage);

        assertThat(chatResponse(model).tokenUsage()).isEqualTo(tokenUsage);
        assertThat(completeResponse(collect(model)).tokenUsage()).isEqualTo(tokenUsage);
        assertThat(chatResponse(model).metadata()).isEqualTo(given.metadata());
    }

    @Test
    void should_take_the_listeners_as_a_list() {

        ChatModelListener listener = new ChatModelListener() {};
        List<ChatModelListener> listeners = new ArrayList<>(List.of(listener));
        MockStreamingChatModel model = MockStreamingChatModel.thatResponds("Hi").withListeners(listeners);

        listeners.clear();

        assertThat(model.listeners()).containsExactly(listener);
    }

    @Test
    void should_report_no_capabilities_by_default() {

        assertThat(MockStreamingChatModel.thatResponds("Hi").supportedCapabilities())
                .isEmpty();
    }

    @Test
    void should_report_the_given_capabilities_and_still_answer_with_the_script() throws Exception {

        MockStreamingChatModel model =
                MockStreamingChatModel.thatResponds("Hi").withSupportedCapabilities(RESPONSE_FORMAT_JSON_SCHEMA);

        assertThat(model.supportedCapabilities()).containsExactly(RESPONSE_FORMAT_JSON_SCHEMA);
        assertThat(chat(model)).isEqualTo("Hi");
    }

    @Test
    void should_take_the_capabilities_as_a_set() {

        Set<Capability> capabilities = new HashSet<>(Set.of(RESPONSE_FORMAT_JSON_SCHEMA));
        MockStreamingChatModel model =
                MockStreamingChatModel.thatResponds("Hi").withSupportedCapabilities(capabilities);

        capabilities.clear();

        assertThat(model.supportedCapabilities()).containsExactly(RESPONSE_FORMAT_JSON_SCHEMA);
    }

    @Test
    void should_accept_a_repeated_capability_and_reject_null_capabilities() {

        MockStreamingChatModel model = MockStreamingChatModel.thatResponds("Hi")
                .withSupportedCapabilities(RESPONSE_FORMAT_JSON_SCHEMA, RESPONSE_FORMAT_JSON_SCHEMA);

        assertThat(model.supportedCapabilities()).containsExactly(RESPONSE_FORMAT_JSON_SCHEMA);
        assertThatThrownBy(() -> model.withSupportedCapabilities((Capability[]) null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> model.withSupportedCapabilities((Set<Capability>) null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_call_a_tool_and_then_stream() throws Exception {

        MockStreamingChatModel model = MockStreamingChatModel.thatCallsTool("cancelBooking", Map.of("id", 7))
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
    void should_take_the_steps_in_order_and_fail_after_the_last_one() throws Exception {

        MockStreamingChatModel model = MockStreamingChatModel.thatStreams("fi", "rst")
                .thenCallsTool("getWeather", Map.of())
                .thenResponds(AiMessage.from("third"));

        assertThat(chat(model)).isEqualTo("first");
        assertThat(chatResponse(model).aiMessage().toolExecutionRequests()).hasSize(1);
        assertThat(chat(model)).isEqualTo("third");
        assertThat(stream(model).error()).hasMessageContaining("received request 4, but its script has only 3 steps");
    }

    @Test
    void should_fail_after_the_tokens_when_the_stream_breaks() throws Exception {

        RuntimeException failure = new IllegalStateException("connection reset");
        MockStreamingChatModel model =
                MockStreamingChatModel.thatStreams("Hel", "lo").andFails(failure).thenRepeats();

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
        MockStreamingChatModel model = MockStreamingChatModel.thatStreams("Hel", "lo").andFails(failure);

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
        MockStreamingChatModel model = MockStreamingChatModel.thatResponds("broken")
                .andFails(failure)
                .thenResponds("fine");

        assertThat(stream(model).error()).isSameAs(failure);
        assertThat(chat(model)).isEqualTo("fine");
    }

    @Test
    void should_fail_before_streaming_with_a_scripted_exception_and_then_stream() throws Exception {

        RuntimeException failure = new IllegalStateException("temporary failure");
        MockStreamingChatModel model = MockStreamingChatModel.thatFails(failure).thenResponds("recovered");

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
                MockStreamingChatModel.thatResponds(List.of(AiMessage.from("first"), AiMessage.from("second")));

        assertThat(chat(model)).isEqualTo("first");
        assertThat(chat(model)).isEqualTo("second");
    }

    @Test
    void should_not_accept_a_failure_after_the_tokens_on_a_mock_that_always_fails() {

        assertThatThrownBy(() -> MockStreamingChatModel.thatFails(new RuntimeException("a"))
                        .andFails(new RuntimeException("b")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("andFails");
        assertThatThrownBy(() -> MockStreamingChatModel.thatResponds("a")
                        .thenFails(new RuntimeException("a"))
                        .andFails(new RuntimeException("b")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void should_not_accept_a_second_failure_on_the_same_response() {

        assertThatThrownBy(() -> MockStreamingChatModel.thatResponds("a")
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

        assertThatThrownBy(() -> MockStreamingChatModel.thatResponds(request -> AiMessage.from("Hi"))
                        .andFails(new RuntimeException()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("computes its answer");
    }

    @Test
    void should_not_accept_a_failure_after_all_steps_were_taken() throws Exception {

        MockStreamingChatModel model = MockStreamingChatModel.thatResponds("a");
        chat(model);

        assertThatThrownBy(() -> model.andFails(new RuntimeException()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("andFails");
    }

    @Test
    void should_compute_the_answer_from_the_request() throws Exception {

        MockStreamingChatModel model = MockStreamingChatModel.thatResponds(
                request -> AiMessage.from("You said " + ((UserMessage) request.messages().get(0)).singleText()));

        assertThat(stream(model).response().aiMessage().text()).isEqualTo("You said Hi");
    }

    @Test
    void should_report_a_failing_computation_through_on_error() throws Exception {

        RuntimeException failure = new IllegalStateException("cannot answer");
        MockStreamingChatModel model = MockStreamingChatModel.thatResponds(request -> {
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
                MockStreamingChatModel.thatResponds(AiMessage.from(first, second), AiMessage.from("done"));

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
                .thinking("I see")
                .text("On it")
                .toolExecutionRequests(List.of(MockChatModel.toolCall("a", "{\"x\":1}")))
                .build();
        MockStreamingChatModel model = MockStreamingChatModel.thatResponds(answer, AiMessage.from("done"));

        List<ChatModelStreamingEvent> events = collect(model);

        assertThat(events).hasSize(11);
        assertThat(events.subList(0, 2)).containsExactly(new PartialThinking("I"), new PartialThinking(" see"));
        assertThat(events.subList(2, 4)).containsExactly(new PartialResponse("On"), new PartialResponse(" it"));
        assertThat(events.subList(4, 9))
                .extracting(event -> ((PartialToolCall) event).partialArguments())
                .containsExactly("{\"", "x", "\":", "1", "}");
        assertThat(((PartialToolCall) events.get(4)).name()).isEqualTo("a");
        assertThat(((PartialToolCall) events.get(4)).id()).isEqualTo("tool_call_1");
        assertThat(((PartialToolCall) events.get(4)).index()).isZero();
        assertThat(events.get(9)).isInstanceOf(CompleteToolCall.class);
        assertThat(events.get(10)).isInstanceOf(CompleteResponse.class);
    }

    @Test
    void should_call_the_handler_with_thinking_then_text_then_partial_and_complete_tool_calls() throws Exception {

        AiMessage answer = AiMessage.builder()
                .thinking("I see")
                .text("On it")
                .toolExecutionRequests(List.of(MockChatModel.toolCall("a", "{\"x\":1}")))
                .build();
        MockStreamingChatModel model = MockStreamingChatModel.thatResponds(answer, AiMessage.from("done"));
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
                        "thinking:I",
                        "thinking: see",
                        "text:On",
                        "text: it",
                        "partial:{\"",
                        "partial:x",
                        "partial:\":",
                        "partial:1",
                        "partial:}",
                        "complete:a",
                        "response");
    }

    @Test
    void should_stream_tokens_that_make_up_exactly_the_text() throws Exception {

        String text = "  H\u00e9llo,\u00a0w\u00f6rld! \ud83d\ude00 12345\r\n\tend  ";
        MockStreamingChatModel model = MockStreamingChatModel.thatResponds(text);

        List<String> tokens = stream(model).tokens();

        assertThat(String.join("", tokens)).isEqualTo(text);
        assertThat(tokens).doesNotContain("");
    }

    @Test
    void should_report_a_failure_after_the_tokens_only_once_they_were_requested() throws Exception {

        RuntimeException failure = new IllegalStateException("connection reset");
        MockStreamingChatModel model = MockStreamingChatModel.thatStreams("Hel", "lo").andFails(failure);
        List<Object> signals = new CopyOnWriteArrayList<>();
        CompletableFuture<Flow.Subscription> subscribed = new CompletableFuture<>();
        CompletableFuture<Thread> streamingThread = new CompletableFuture<>();
        CompletableFuture<Void> done = new CompletableFuture<>();

        model.chat(ChatRequest.builder().messages(UserMessage.from("Hi")).build())
                .subscribe(new Flow.Subscriber<>() {

                    @Override
                    public void onSubscribe(Flow.Subscription subscription) {
                        subscription.request(1);
                        subscribed.complete(subscription);
                    }

                    @Override
                    public void onNext(ChatModelStreamingEvent event) {
                        signals.add(event);
                        streamingThread.complete(Thread.currentThread());
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

        // the subscriber requested one event before the mock's thread started, so that thread delivers it; once the
        // thread has ended, the second token and the failure wait until the subscriber requests more
        streamingThread.get(5, SECONDS).join(5_000);
        assertThat(signals).containsExactly(new PartialResponse("Hel"));

        subscribed.get(5, SECONDS).request(1);

        assertThat(done).succeedsWithin(5, SECONDS);
        assertThat(signals).containsExactly(new PartialResponse("Hel"), new PartialResponse("lo"), failure);
    }

    @Test
    void should_complete_after_the_last_event_when_the_subscriber_requests_the_events_later() throws Exception {

        CompletableFuture<Thread> streamingThread = new CompletableFuture<>();
        MockStreamingChatModel model = MockStreamingChatModel.thatResponds(request -> {
            streamingThread.complete(Thread.currentThread());
            return AiMessage.from("Hello world");
        });
        List<Object> signals = new CopyOnWriteArrayList<>();
        CompletableFuture<Flow.Subscription> subscribed = new CompletableFuture<>();
        CompletableFuture<Void> done = new CompletableFuture<>();

        model.chat(ChatRequest.builder().messages(UserMessage.from("Hi")).build())
                .subscribe(new Flow.Subscriber<>() {

                    @Override
                    public void onSubscribe(Flow.Subscription subscription) {
                        subscribed.complete(subscription);
                    }

                    @Override
                    public void onNext(ChatModelStreamingEvent event) {
                        signals.add(event);
                    }

                    @Override
                    public void onError(Throwable error) {
                        done.completeExceptionally(error);
                    }

                    @Override
                    public void onComplete() {
                        signals.add("complete");
                        done.complete(null);
                    }
                });

        // nothing was requested yet, so the mock's thread produces all events, delivers none of them and ends;
        // every event and the completion are then delivered on this thread, one request at a time
        streamingThread.get(5, SECONDS).join(5_000);
        Flow.Subscription subscription = subscribed.get(5, SECONDS);
        subscription.request(1);
        assertThat(signals).containsExactly(new PartialResponse("Hello"));
        subscription.request(1);
        assertThat(signals).containsExactly(new PartialResponse("Hello"), new PartialResponse(" world"));
        subscription.request(1);

        assertThat(done).succeedsWithin(5, SECONDS);
        assertThat(signals).hasSize(4);
        assertThat(signals.get(2)).isInstanceOf(CompleteResponse.class);
        assertThat(signals.get(3)).isEqualTo("complete");
    }

    @Test
    void should_take_the_next_step_for_each_subscription_to_the_same_publisher() {

        RuntimeException failure = new IllegalStateException("connection reset");
        MockStreamingChatModel model = MockStreamingChatModel.thatFails(failure).thenResponds("recovered");
        Flow.Publisher<ChatModelStreamingEvent> publisher =
                model.chat(ChatRequest.builder().messages(UserMessage.from("Hi")).build());

        assertThat(model.requests()).isEmpty();

        assertThat(signals(publisher)).containsExactly(failure);
        assertThat(model.requests()).hasSize(1);

        List<Object> retry = signals(publisher);
        assertThat(retry.get(0)).isEqualTo(new PartialResponse("recovered"));
        assertThat(retry.get(1)).isInstanceOf(CompleteResponse.class);
        assertThat(retry).hasSize(2);
        assertThat(model.requests()).hasSize(2);

        assertThat(signals(publisher))
                .singleElement()
                .isInstanceOf(IllegalStateException.class)
                .extracting(error -> ((Throwable) error).getMessage())
                .asString()
                .contains("received request 3");
        assertThat(model.requests()).hasSize(3);
    }

    @Test
    void should_report_a_null_request_with_on_error_without_recording_it() throws Exception {

        MockStreamingChatModel model = MockStreamingChatModel.thatResponds("Hi");
        CompletableFuture<Throwable> handlerError = new CompletableFuture<>();

        model.doChat(null, new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String partialResponse) {}

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                handlerError.completeExceptionally(new AssertionError("completed"));
            }

            @Override
            public void onError(Throwable error) {
                handlerError.complete(error);
            }
        });

        assertThat(handlerError.get(5, SECONDS)).isInstanceOf(IllegalArgumentException.class);
        assertThat(signals(model.doChat((ChatRequest) null)))
                .singleElement()
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(model.requests()).isEmpty();
        assertThat(chat(model)).isEqualTo("Hi");
    }

    @Test
    void should_stream_thinking_before_a_failure_after_the_tokens() throws Exception {

        RuntimeException failure = new IllegalStateException("connection reset");
        AiMessage answer = AiMessage.builder().thinking("I see").text("On it").build();
        List<Object> signals = new ArrayList<>();

        MockStreamingChatModel breaking =
                MockStreamingChatModel.thatResponds(List.of(answer)).andFails(failure);
        subscribe(breaking, signals);

        assertThat(signals)
                .containsExactly(
                        new PartialThinking("I"),
                        new PartialThinking(" see"),
                        new PartialResponse("On"),
                        new PartialResponse(" it"),
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
                MockStreamingChatModel.thatResponds("first").thenFails(failure).thenRepeats();

        assertThat(chat(model)).isEqualTo("first");
        assertThat(stream(model).error()).isSameAs(failure);
        assertThat(stream(model).error()).isSameAs(failure);
    }

    @Test
    void should_not_accept_an_empty_list_of_messages() {

        assertThatThrownBy(() -> MockStreamingChatModel.thatResponds(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("aiMessages");
    }

    @Test
    void should_not_accept_a_call_without_messages() {

        assertThatThrownBy(() -> MockStreamingChatModel.thatResponds())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("aiMessages");
        assertThatThrownBy(() -> MockStreamingChatModel.thatResponds((AiMessage[]) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("aiMessages");
    }

    @Test
    void should_not_accept_null_arguments() {

        assertThatThrownBy(() -> MockStreamingChatModel.thatFails(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MockStreamingChatModel.thatResponds("Hi").andFails(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MockStreamingChatModel.thatResponds((String) null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MockStreamingChatModel.thatStreams("Hel", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MockStreamingChatModel.thatStreams((String[]) null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_not_split_a_character_outside_the_basic_plane_into_two_tokens() throws Exception {

        assertThat(ScriptedResponses.splitIntoTokens("a\uD83D\uDE00")).containsExactly("a", "\uD83D\uDE00");
        assertThat(stream(MockStreamingChatModel.thatResponds("a\uD83D\uDE00")).tokens())
                .containsExactly("a", "\uD83D\uDE00");
    }

    @Test
    void should_stop_after_the_handle_was_cancelled_during_the_last_token() throws Exception {

        MockStreamingChatModel model = MockStreamingChatModel.thatStreams("Hel", "lo");
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

        assertThatThrownBy(() -> MockStreamingChatModel.thatStreams("Hel", "", "lo"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("A token cannot be empty");
        assertThatThrownBy(() -> MockStreamingChatModel.thatResponds("Hi").thenStreams(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("A token cannot be empty");
    }

    @Test
    void should_stream_an_answer_without_text_when_given_no_tokens() throws Exception {

        Streamed streamed = stream(MockStreamingChatModel.thatStreams());

        assertThat(streamed.tokens()).isEmpty();
        assertThat(streamed.error()).isNull();
        assertThat(streamed.response().aiMessage().text()).isNull();
    }

    @Test
    void should_not_accept_an_empty_text_because_a_provider_completes_without_text() {

        AiMessage emptyText = AiMessage.from("");

        assertThatThrownBy(() -> MockStreamingChatModel.thatResponds(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot stream the text \"\"")
                .hasMessageContaining("use thatStreams()")
                .hasMessageContaining("thatResponds(ChatResponse)");
        assertThatThrownBy(() -> MockStreamingChatModel.thatResponds("Hi").thenResponds(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MockStreamingChatModel.thatResponds(emptyText))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MockStreamingChatModel.thatResponds(List.of(AiMessage.from("Hi"), emptyText)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MockStreamingChatModel.thatResponds("Hi").thenResponds(emptyText))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_fail_the_request_when_the_computed_answer_has_an_empty_text() throws Exception {

        Streamed streamed = stream(MockStreamingChatModel.thatResponds(request -> AiMessage.from("")));

        assertThat(streamed.error())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot stream the text \"\"");
    }

    @Test
    void should_complete_with_an_empty_text_given_in_a_whole_response() throws Exception {

        ChatResponse response =
                ChatResponse.builder().aiMessage(AiMessage.from("")).build();

        Streamed streamed = stream(MockStreamingChatModel.thatResponds(response));

        assertThat(streamed.tokens()).isEmpty();
        assertThat(streamed.response()).isSameAs(response);
    }

    @Test
    void should_stream_a_tool_call_without_a_name_on_both_paths() throws Exception {

        AiMessage toolCallWithoutName = AiMessage.from(ToolExecutionRequest.builder()
                .id("1")
                .name(" ")
                .arguments("{}")
                .build());

        Streamed streamed = stream(MockStreamingChatModel.thatResponds(toolCallWithoutName));
        assertThat(streamed.error()).isNull();
        assertThat(streamed.response().aiMessage().toolExecutionRequests()).hasSize(1);

        List<Object> signals = new CopyOnWriteArrayList<>();
        subscribe(MockStreamingChatModel.thatResponds(toolCallWithoutName), signals);
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
        MockStreamingChatModel model = MockStreamingChatModel.thatResponds(textAndToolCall).andFails(failure);
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

        assertThatThrownBy(() -> MockStreamingChatModel.thatStreams().andFails(new RuntimeException()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no text or thinking to stream");
    }

    @Test
    void should_signal_a_demand_that_is_not_positive_to_the_subscriber() {

        List<Object> signals = new CopyOnWriteArrayList<>();
        CompletableFuture<Void> done = new CompletableFuture<>();

        MockStreamingChatModel.thatResponds("Hi")
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

        MockStreamingChatModel.thatResponds("Hello")
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

        MockStreamingChatModel.thatResponds(request -> {
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

        MockStreamingChatModel.thatResponds("Hello")
                .chat(ChatRequest.builder().messages(UserMessage.from("Hi")).build())
                .subscribe(new Flow.Subscriber<>() {

                    @Override
                    public void onSubscribe(Flow.Subscription subscription) {
                        subscriptions.add(subscription);
                        // requested before the mock's thread starts, so the event reaches onNext on that thread
                        subscription.request(1);
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

        // the failure ends the streaming thread, like an uncaught exception on a provider's I/O thread
        streamingThread.get(5, SECONDS).join(5_000);
        subscriptions.get(0).request(10);

        assertThat(signals).singleElement().isInstanceOf(PartialResponse.class);
    }

    @Test
    void should_pass_an_assertion_error_of_the_handler_to_on_error() throws Exception {

        MockStreamingChatModel model = MockStreamingChatModel.thatResponds("Hi");
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
        MockStreamingChatModel model = MockStreamingChatModel.thatResponds("Hi")
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

        MockStreamingChatModel model = MockStreamingChatModel.thatResponds("Hi");

        assertThatThrownBy(model::request)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Expected exactly 1 chat request, but received 0");
    }

    @Test
    void should_return_the_last_request() throws Exception {

        MockStreamingChatModel model = MockStreamingChatModel.thatResponds("Hi").thenRepeats();

        assertThatThrownBy(model::lastRequest)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Expected at least 1 chat request, but received none");
        chat(model);
        collect(model);

        assertThat(model.lastRequest()).isSameAs(model.requests().get(1));
    }

    @Test
    void should_stream_exactly_the_given_tokens_given_as_a_list() throws Exception {

        MockStreamingChatModel model = MockStreamingChatModel.thatStreams(List.of("Hel", "lo"))
                .thenStreams(List.of("Wor", "ld"));

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
    private static List<Object> signals(Flow.Publisher<ChatModelStreamingEvent> publisher) {
        List<Object> signals = new CopyOnWriteArrayList<>();
        CompletableFuture<Void> done = new CompletableFuture<>();
        publisher.subscribe(new Flow.Subscriber<>() {

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
        return signals;
    }

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
