package dev.langchain4j.test.mock;

import static dev.langchain4j.test.mock.MockChatModel.toolCall;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class MockChatModelTest {

    private static final ChatRequest REQUEST =
            ChatRequest.builder().messages(UserMessage.from("Hello")).build();

    private static final AiMessage TOOL_CALL = AiMessage.from(ToolExecutionRequest.builder()
            .id("1")
            .name("getWeather")
            .arguments("{}")
            .build());

    @Test
    void should_answer_one_request_per_step_and_fail_after_the_last_one() {

        MockChatModel model = MockChatModel.thatResponds(AiMessage.from("Hi"));

        assertThat(chat(model).text()).isEqualTo("Hi");
        assertThatThrownBy(() -> chat(model))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("MockChatModel received request 2, but its script has only 1 step. Script one more step,"
                        + " or end the script with thenRepeats() to answer every further request with its last step."
                        + " If the mock is shared between tests, for example as a bean, create a new one for each"
                        + " test. The last message of request 2: USER \"Hello\"");
    }

    @Test
    void should_ask_for_the_answer_after_the_tool_results_when_the_script_ends_with_a_tool_call() {

        MockChatModel model = MockChatModel.thatResponds(TOOL_CALL);

        assertThat(chat(model).hasToolExecutionRequests()).isTrue();
        assertThatThrownBy(() -> chat(model))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("received request 2")
                .hasMessageContaining("The last step called a tool, so script the answer that follows the tool results.");
    }

    @Test
    void should_show_the_shortened_last_message_of_the_request_after_the_script() {

        MockChatModel model = MockChatModel.thatCallsTool("getWeather", Map.of());
        ToolExecutionRequest toolCall = chat(model).toolExecutionRequests().get(0);
        ChatRequest request = ChatRequest.builder()
                .messages(
                        UserMessage.from("Hello"),
                        AiMessage.from(toolCall),
                        ToolExecutionResultMessage.from(toolCall, "sunny".repeat(100)))
                .build();

        assertThatThrownBy(() -> model.chat(request))
                .hasMessageContaining("The last message of request 2: TOOL_EXECUTION_RESULT \"result of getWeather: sunny")
                .hasMessageEndingWith("...\"")
                .message()
                .hasSizeLessThan(600);
    }

    @Test
    void should_repeat_a_single_answer_for_every_request() {

        MockChatModel model = MockChatModel.thatResponds("Hi").thenRepeats();

        assertThat(chat(model).text()).isEqualTo("Hi");
        assertThat(chat(model).text()).isEqualTo("Hi");
        assertThat(chat(model).text()).isEqualTo("Hi");
    }

    @Test
    void should_repeat_only_the_last_step_of_a_script() {

        MockChatModel model =
                MockChatModel.thatResponds("first").thenResponds("second").thenRepeats();

        assertThat(chat(model).text()).isEqualTo("first");
        assertThat(chat(model).text()).isEqualTo("second");
        assertThat(chat(model).text()).isEqualTo("second");
        assertThat(chat(model).text()).isEqualTo("second");
    }

    @Test
    void should_not_repeat_a_last_step_that_calls_a_tool() {

        assertThatThrownBy(() -> MockChatModel.thatCallsTool("getWeather", Map.of()).thenRepeats())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot repeat a step that calls a tool");
        assertThatThrownBy(() -> MockChatModel.thatResponds("first")
                        .thenResponds(TOOL_CALL)
                        .thenRepeats())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot repeat a step that calls a tool");
    }

    @Test
    void should_not_accept_steps_after_repeating() {

        assertThatThrownBy(() -> MockChatModel.thatResponds("first").thenRepeats().thenResponds("second"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("thenRepeats() must be the last call of the script");
        assertThatThrownBy(() -> MockChatModel.thatResponds("first").thenRepeats().thenRepeats())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("thenRepeats() must be the last call of the script");
    }

    @Test
    void should_compute_the_answer_of_a_step_from_the_request() {

        MockChatModel model = MockChatModel.thatResponds(
                request -> AiMessage.from("You said: " + ((UserMessage) request.messages().get(0)).singleText()));

        assertThat(chat(model).text()).isEqualTo("You said: Hello");
        assertThatThrownBy(() -> chat(model))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("received request 2, but its script has only 1 step");
    }

    @Test
    void should_compute_every_answer_when_the_computed_step_repeats() {

        AtomicInteger calls = new AtomicInteger();
        MockChatModel model = MockChatModel.thatResponds(request -> AiMessage.from("answer " + calls.incrementAndGet()))
                .thenRepeats();

        assertThat(chat(model).text()).isEqualTo("answer 1");
        assertThat(chat(model).text()).isEqualTo("answer 2");
        assertThat(chat(model).text()).isEqualTo("answer 3");
    }

    @Test
    void should_take_the_next_steps_after_a_computed_step() {

        MockChatModel model = MockChatModel.thatResponds(request -> AiMessage.from("computed"))
                .thenResponds("second")
                .thenFails(new IllegalStateException("third"));

        assertThat(chat(model).text()).isEqualTo("computed");
        assertThat(chat(model).text()).isEqualTo("second");
        assertThatThrownBy(() -> chat(model)).hasMessage("third");
    }

    @Test
    void should_compute_the_answer_of_chat_async_on_another_thread() {

        Thread caller = Thread.currentThread();
        List<Thread> computedOn = new ArrayList<>();
        MockChatModel model = MockChatModel.thatResponds(request -> {
            computedOn.add(Thread.currentThread());
            return AiMessage.from("Hi");
        });

        assertThat(model.chatAsync(REQUEST)).succeedsWithin(5, SECONDS);
        assertThat(computedOn).singleElement().isNotSameAs(caller);
    }

    @Test
    void should_answer_chat_async_without_the_inheritable_thread_locals_but_with_the_class_loader_of_the_caller() {

        InheritableThreadLocal<String> currentUser = new InheritableThreadLocal<>();
        currentUser.set("klaus");
        List<ClassLoader> classLoaders = new ArrayList<>();
        try {
            MockChatModel model = MockChatModel.thatResponds(request -> {
                classLoaders.add(Thread.currentThread().getContextClassLoader());
                return AiMessage.from(String.valueOf(currentUser.get()));
            });

            // like a provider's I/O thread, which does not know the user of the calling thread
            assertThat(model.chatAsync(REQUEST))
                    .succeedsWithin(5, SECONDS)
                    .extracting(response -> response.aiMessage().text())
                    .isEqualTo("null");
            assertThat(classLoaders).containsExactly(Thread.currentThread().getContextClassLoader());
        } finally {
            currentUser.remove();
        }
    }

    @Test
    void should_answer_concurrent_async_requests_whose_computed_answers_wait_for_each_other() {

        int requestCount = Runtime.getRuntime().availableProcessors() * 2 + 4; // more than the common pool has threads
        CountDownLatch allArrived = new CountDownLatch(requestCount);
        MockChatModel model = MockChatModel.thatResponds(request -> {
                    allArrived.countDown();
                    try {
                        if (!allArrived.await(10, SECONDS)) {
                            throw new IllegalStateException("not all requests arrived");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(e);
                    }
                    return AiMessage.from("answer");
                })
                .thenRepeats();

        List<CompletableFuture<ChatResponse>> futures = new ArrayList<>();
        for (int i = 0; i < requestCount; i++) {
            futures.add(model.chatAsync(REQUEST));
        }

        for (CompletableFuture<ChatResponse> future : futures) {
            assertThat(future).succeedsWithin(20, SECONDS);
        }
    }

    @Test
    void should_answer_with_exactly_the_given_response() {

        ChatResponse given = ChatResponse.builder()
                .aiMessage(AiMessage.from("Hi"))
                .id("response-1")
                .modelName("model")
                .tokenUsage(new TokenUsage(1, 2))
                .finishReason(FinishReason.LENGTH)
                .build();
        MockChatModel model = MockChatModel.thatResponds(given).thenResponds(ChatResponse.builder()
                .aiMessage(AiMessage.from("no metadata"))
                .build());

        ChatResponse first = chatResponse(model);
        assertThat(first.aiMessage().text()).isEqualTo("Hi");
        assertThat(first.metadata()).isEqualTo(given.metadata());
        ChatResponse second = chatResponse(model);
        assertThat(second.finishReason()).isNull();
        assertThat(second.tokenUsage()).isNull();
    }

    @Test
    void should_report_the_finish_reason_of_a_provider_and_no_token_usage_by_default() {

        MockChatModel model = MockChatModel.thatCallsTool("getWeather", Map.of()).thenResponds("Sunny");

        ChatResponse toolCall = chatResponse(model);
        assertThat(toolCall.finishReason()).isEqualTo(FinishReason.TOOL_EXECUTION);
        assertThat(toolCall.tokenUsage()).isNull();
        assertThat(toolCall.modelName()).isNull();
        assertThat(chatResponse(model).finishReason()).isEqualTo(FinishReason.STOP);
    }

    @Test
    void should_call_a_tool_and_then_respond() {

        MockChatModel model = MockChatModel.thatCallsTool("cancelBooking", Map.of("bookingNumber", "MS-777"))
                .thenResponds("cancelled");

        ToolExecutionRequest toolCall = chat(model).toolExecutionRequests().get(0);
        assertThat(toolCall.name()).isEqualTo("cancelBooking");
        assertThat(toolCall.arguments()).isEqualTo("{\"bookingNumber\":\"MS-777\"}");
        assertThat(toolCall.id()).isEqualTo("tool_call_1");
        assertThat(chat(model).text()).isEqualTo("cancelled");
        assertThat(model.requests()).hasSize(2);
    }

    @Test
    void should_take_the_steps_in_order_and_fail_after_the_last_one() {

        MockChatModel model = MockChatModel.thatResponds(AiMessage.from("first"), AiMessage.from("second"))
                .thenCallsTool("getWeather", Map.of())
                .thenResponds(AiMessage.from("fourth"));

        assertThat(chat(model).text()).isEqualTo("first");
        assertThat(chat(model).text()).isEqualTo("second");
        assertThat(chat(model).toolExecutionRequests()).hasSize(1);
        assertThat(chat(model).text()).isEqualTo("fourth");
        assertThatThrownBy(() -> chat(model))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("MockChatModel received request 5, but its script has only 4 steps.");
    }

    @Test
    void should_take_each_step_exactly_once_when_called_from_several_threads() throws Exception {

        int requests = 50;
        MockChatModel model = MockChatModel.thatResponds(response(0));
        for (int i = 1; i < requests; i++) {
            model.thenResponds(response(i));
        }
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Future<ChatResponse>> futures = new ArrayList<>();
            for (int i = 0; i < requests; i++) {
                futures.add(executor.submit(() -> chatResponse(model)));
            }
            List<ChatResponse> responses = new ArrayList<>();
            for (Future<ChatResponse> future : futures) {
                responses.add(future.get(5, SECONDS));
            }

            assertThat(responses)
                    .extracting(response -> response.aiMessage().text())
                    .doesNotHaveDuplicates()
                    .hasSize(requests);
            assertThat(responses)
                    .allSatisfy(response -> assertThat(response.metadata().modelName())
                            .isEqualTo("model-" + response.aiMessage().text()));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void should_throw_the_scripted_exception_and_then_respond() {

        RuntimeException failure = new IllegalStateException("temporary failure");
        MockChatModel model = MockChatModel.thatFails(failure).thenResponds("recovered");

        assertThatThrownBy(() -> chat(model)).isSameAs(failure);
        assertThat(chat(model).text()).isEqualTo("recovered");
    }

    @Test
    void should_repeat_a_last_step_that_fails() {

        RuntimeException failure = new IllegalStateException("down");
        MockChatModel model = MockChatModel.thatResponds("first").thenFails(failure).thenRepeats();

        assertThat(chat(model).text()).isEqualTo("first");
        assertThatThrownBy(() -> chat(model)).isSameAs(failure);
        assertThatThrownBy(() -> chat(model)).isSameAs(failure);
    }

    @Test
    void should_respond_with_a_collection_of_messages_in_order() {

        MockChatModel model = MockChatModel.thatResponds(List.of(AiMessage.from("first"), AiMessage.from("second")));

        assertThat(chat(model).text()).isEqualTo("first");
        assertThat(chat(model).text()).isEqualTo("second");
    }

    @Test
    void should_not_accept_an_empty_collection_of_messages() {

        assertThatThrownBy(() -> MockChatModel.thatResponds(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("aiMessages");
    }

    @Test
    void should_fail_the_first_request_with_the_given_exception() {

        RuntimeException failure = new IllegalArgumentException("invalid");
        MockChatModel model = MockChatModel.thatFails(failure);

        assertThatThrownBy(() -> chat(model)).isSameAs(failure);
        assertThatThrownBy(() -> chat(model)).hasMessageContaining("received request 2");
    }

    @Test
    void should_fail_every_request_with_the_given_exception_when_repeated() {

        RuntimeException failure = new IllegalArgumentException("invalid");
        MockChatModel model = MockChatModel.thatFails(failure).thenRepeats();

        assertThatThrownBy(() -> chat(model)).isSameAs(failure);
        assertThatThrownBy(() -> chat(model)).isSameAs(failure);
    }

    @Test
    void should_number_the_tool_calls_without_an_id() {

        MockChatModel model = MockChatModel.thatCallsTool("a", Map.of())
                .thenResponds(AiMessage.from(toolCall("a", Map.of("x", 1)), toolCall("a", Map.of("x", 1))))
                .thenResponds(AiMessage.from(ToolExecutionRequest.builder()
                        .id("given")
                        .name("b")
                        .arguments("{}")
                        .build()))
                .thenResponds("done");

        assertThat(chat(model).toolExecutionRequests())
                .extracting(ToolExecutionRequest::id)
                .containsExactly("tool_call_1");
        assertThat(chat(model).toolExecutionRequests())
                .extracting(ToolExecutionRequest::id)
                .containsExactly("tool_call_2", "tool_call_3");
        assertThat(chat(model).toolExecutionRequests())
                .extracting(ToolExecutionRequest::id)
                .containsExactly("given");
    }

    @Test
    void should_number_the_tool_calls_of_computed_answers_and_given_responses() {

        MockChatModel model = MockChatModel.thatResponds(request -> AiMessage.from(toolCall("a", Map.of())))
                .thenResponds(ChatResponse.builder()
                        .aiMessage(AiMessage.from(toolCall("b", Map.of())))
                        .tokenUsage(new TokenUsage(1, 2))
                        .build())
                .thenResponds("done");

        assertThat(chat(model).toolExecutionRequests().get(0).id()).isEqualTo("tool_call_1");
        ChatResponse response = chatResponse(model);
        assertThat(response.aiMessage().toolExecutionRequests().get(0).id()).isEqualTo("tool_call_2");
        assertThat(response.tokenUsage()).isEqualTo(new TokenUsage(1, 2));
    }

    @Test
    void should_create_a_tool_call_without_an_id() {

        assertThat(toolCall("a", Map.of()).id()).isNull();
    }

    @Test
    void should_not_accept_two_tool_calls_with_the_same_id_in_one_answer() {

        AiMessage answer = AiMessage.from(
                ToolExecutionRequest.builder().id("1").name("a").arguments("{}").build(),
                ToolExecutionRequest.builder().id("1").name("b").arguments("{\"x\":1}").build());

        assertThatThrownBy(() -> MockChatModel.thatResponds(answer, AiMessage.from("done")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("two tool calls with the id \"1\"");
        assertThatThrownBy(() -> chat(MockChatModel.thatResponds(request -> answer)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("a provider never gives two tool calls of one answer the same id");
    }

    @Test
    void should_accept_the_same_tool_call_twice_without_ids_and_number_them() {

        MockChatModel model =
                MockChatModel.thatResponds(AiMessage.from(toolCall("a", Map.of()), toolCall("a", Map.of())));

        assertThat(chat(model).toolExecutionRequests())
                .extracting(ToolExecutionRequest::id)
                .containsExactly("tool_call_1", "tool_call_2");
    }

    @Test
    void should_not_give_a_tool_call_an_id_that_was_given_explicitly() {

        MockChatModel model = MockChatModel.thatCallsTool("a", Map.of())
                .thenResponds(AiMessage.from(
                        ToolExecutionRequest.builder()
                                .id("tool_call_1")
                                .name("b")
                                .arguments("{}")
                                .build(),
                        toolCall("c", Map.of())));

        assertThat(chat(model).toolExecutionRequests())
                .extracting(ToolExecutionRequest::id)
                .containsExactly("tool_call_2");
        assertThat(chat(model).toolExecutionRequests())
                .extracting(ToolExecutionRequest::id)
                .containsExactly("tool_call_1", "tool_call_3");
    }

    @Test
    void should_number_the_tool_calls_of_each_mock_on_its_own() {

        MockChatModel first = MockChatModel.thatCallsTool("a", Map.of());
        MockChatModel second = MockChatModel.thatCallsTool("a", Map.of());

        assertThat(chat(first).toolExecutionRequests().get(0).id()).isEqualTo("tool_call_1");
        assertThat(chat(second).toolExecutionRequests().get(0).id()).isEqualTo("tool_call_1");
    }

    @Test
    void should_throw_exactly_the_scripted_exception_without_mapping_it() {

        RuntimeException failure = new HttpException(429, "Too many requests");

        assertThatThrownBy(() -> chat(MockChatModel.thatFails(failure))).isSameAs(failure);
        assertThatThrownBy(() -> chat(MockChatModel.thatResponds(request -> {
                    throw failure;
                })))
                .isSameAs(failure);
    }

    @Test
    void should_take_the_steps_in_order_with_chat_async() {

        MockChatModel model = MockChatModel.thatResponds("first").thenResponds("second");

        CompletableFuture<ChatResponse> first = model.chatAsync(REQUEST);
        CompletableFuture<ChatResponse> second = model.chatAsync(REQUEST);

        assertThat(first).succeedsWithin(5, SECONDS).satisfies(r -> assertThat(r.aiMessage().text()).isEqualTo("first"));
        assertThat(second)
                .succeedsWithin(5, SECONDS)
                .satisfies(r -> assertThat(r.aiMessage().text()).isEqualTo("second"));
    }

    @Test
    void should_fail_the_future_of_chat_async_with_a_scripted_exception() {

        RuntimeException failure = new IllegalStateException("temporary failure");
        MockChatModel model = MockChatModel.thatFails(failure).thenResponds("recovered");

        assertThat(model.chatAsync(REQUEST))
                .failsWithin(5, SECONDS)
                .withThrowableThat()
                .havingCause()
                .isSameAs(failure);
        assertThat(model.chatAsync(REQUEST)).succeedsWithin(5, SECONDS);
    }

    @Test
    void should_fail_the_future_of_chat_async_when_the_script_runs_out() {

        MockChatModel model = MockChatModel.thatResponds("Hi");
        chat(model);

        assertThat(model.chatAsync(REQUEST))
                .failsWithin(5, SECONDS)
                .withThrowableThat()
                .havingCause()
                .isInstanceOf(IllegalStateException.class)
                .withMessageContaining("received request 2");
    }

    @Test
    void should_accept_an_empty_answer() {

        assertThat(chat(MockChatModel.thatResponds("")).text()).isEmpty();
    }

    @Test
    void should_return_a_copy_of_the_requests() {

        MockChatModel model = MockChatModel.thatResponds("Hi").thenRepeats();
        chat(model);

        List<ChatRequest> requests = model.requests();
        chat(model);

        assertThat(requests).hasSize(1);
        assertThat(model.requests()).hasSize(2);
    }

    @Test
    void should_fail_when_there_is_not_exactly_one_request() {

        MockChatModel model = MockChatModel.thatResponds("Hi");

        assertThatThrownBy(model::request)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Expected exactly 1 chat request, but received 0");
    }

    @Test
    void should_not_accept_null_arguments() {

        assertThatThrownBy(() -> MockChatModel.thatFails(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MockChatModel.thatResponds("Hi").thenFails(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MockChatModel.thatResponds((Function<ChatRequest, AiMessage>) null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MockChatModel.thatResponds((ChatResponse) null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_build_several_tool_calls_for_one_answer() {

        MockChatModel model = MockChatModel.thatResponds(
                AiMessage.from(toolCall("a", Map.of("x", 1)), toolCall("b", Map.of("y", "z"))), AiMessage.from("done"));

        assertThat(chat(model).toolExecutionRequests())
                .extracting(ToolExecutionRequest::name, ToolExecutionRequest::arguments)
                .containsExactly(tuple("a", "{\"x\":1}"), tuple("b", "{\"y\":\"z\"}"));
    }

    @Test
    void should_call_a_tool_with_raw_json_arguments() {

        MockChatModel model = MockChatModel.thatCallsTool("a", "{not json").thenCallsTool("b", "{\"x\":1}");

        assertThat(chat(model).toolExecutionRequests().get(0).arguments()).isEqualTo("{not json");
        assertThat(chat(model).toolExecutionRequests().get(0).arguments()).isEqualTo("{\"x\":1}");
        assertThat(toolCall("c", "[]").arguments()).isEqualTo("[]");
    }

    @Test
    void should_not_accept_null_raw_json_arguments() {

        assertThatThrownBy(() -> toolCall("a", (String) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("arguments");
    }

    @Test
    void should_validate_a_tool_call() {

        assertThatThrownBy(() -> toolCall(" ", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("toolName");
        assertThatThrownBy(() -> toolCall("a", (Map<String, ?>) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("arguments");
    }

    @Test
    void should_report_the_model_name_of_the_request() {

        MockChatModel model = MockChatModel.thatResponds("Hi").thenRepeats();

        ChatResponse response = model.chat(ChatRequest.builder()
                .messages(UserMessage.from("Hello"))
                .modelName("gpt-test")
                .build());

        assertThat(response.modelName()).isEqualTo("gpt-test");
        assertThat(chatResponse(model).modelName()).isNull();
    }

    @Test
    void should_report_the_default_token_usage_except_in_a_whole_response() {

        TokenUsage tokenUsage = new TokenUsage(10, 5);
        ChatResponse given = ChatResponse.builder()
                .aiMessage(AiMessage.from("given"))
                .modelName("given-model")
                .tokenUsage(new TokenUsage(1, 1))
                .build();
        MockChatModel model = MockChatModel.thatCallsTool("getWeather", Map.of())
                .thenResponds("Sunny")
                .thenResponds(given)
                .withTokenUsage(tokenUsage);

        assertThat(chatResponse(model).tokenUsage()).isEqualTo(tokenUsage);
        assertThat(chatResponse(model).tokenUsage()).isEqualTo(tokenUsage);
        ChatResponse whole = model.chat(ChatRequest.builder()
                .messages(UserMessage.from("Hello"))
                .modelName("gpt-test")
                .build());
        assertThat(whole.metadata()).isEqualTo(given.metadata());
    }

    @Test
    void should_report_the_default_token_usage_of_a_computed_answer() {

        TokenUsage tokenUsage = new TokenUsage(10, 5);
        MockChatModel model = MockChatModel.thatResponds(request -> AiMessage.from("computed"))
                .thenRepeats()
                .withTokenUsage(tokenUsage);

        assertThat(chatResponse(model).tokenUsage()).isEqualTo(tokenUsage);
        assertThat(model.chatAsync(REQUEST))
                .succeedsWithin(5, SECONDS)
                .satisfies(response -> assertThat(response.tokenUsage()).isEqualTo(tokenUsage));
    }

    @Test
    void should_take_the_listeners_as_a_list() {

        ChatModelListener listener = new ChatModelListener() {};
        List<ChatModelListener> listeners = new ArrayList<>(List.of(listener));
        MockChatModel model = MockChatModel.thatResponds("Hi").withListeners(listeners);

        listeners.clear();

        assertThat(model.listeners()).containsExactly(listener);
    }

    private static ChatResponse response(int number) {
        return ChatResponse.builder()
                .aiMessage(AiMessage.from(String.valueOf(number)))
                .modelName("model-" + number)
                .build();
    }

    private static AiMessage chat(MockChatModel model) {
        return chatResponse(model).aiMessage();
    }

    private static ChatResponse chatResponse(MockChatModel model) {
        return model.chat(ChatRequest.builder().messages(UserMessage.from("Hello")).build());
    }
}
