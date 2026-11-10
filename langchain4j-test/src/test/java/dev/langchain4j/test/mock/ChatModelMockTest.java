package dev.langchain4j.test.mock;

import static dev.langchain4j.test.mock.ChatModelMock.toolCall;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class ChatModelMockTest {

    private static final ChatRequest REQUEST =
            ChatRequest.builder().messages(UserMessage.from("Hello")).build();

    private static final AiMessage TOOL_CALL = AiMessage.from(ToolExecutionRequest.builder()
            .id("1")
            .name("getWeather")
            .arguments("{}")
            .build());

    @Test
    void should_respond_with_a_single_response_to_every_request() {

        ChatModelMock model = ChatModelMock.thatAlwaysResponds(AiMessage.from("Hi"));

        assertThat(chat(model).text()).isEqualTo("Hi");
        assertThat(chat(model).text()).isEqualTo("Hi");
    }

    @Test
    void should_respond_in_order_then_repeat_the_last_response() {

        ChatModelMock model = ChatModelMock.thatResponds(AiMessage.from("first"), AiMessage.from("second"));

        assertThat(chat(model).text()).isEqualTo("first");
        assertThat(chat(model).text()).isEqualTo("second");
        assertThat(chat(model).text()).isEqualTo("second");
    }

    @Test
    void should_fail_instead_of_repeating_a_response_with_tool_calls() {

        ChatModelMock model = ChatModelMock.thatAlwaysResponds(TOOL_CALL);

        assertThat(chat(model).hasToolExecutionRequests()).isTrue();
        assertThatThrownBy(() -> chat(model))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("ChatModelMock has no more responses: the last scripted response (with tool calls) would"
                        + " be repeated, which would loop forever. Script the response that follows the tool"
                        + " results.");
    }

    @Test
    void should_report_the_given_token_usage_and_finish_reason_in_every_response() {

        ChatModelMock model = ChatModelMock.thatAlwaysResponds("Hi")
                .withTokenUsage(new TokenUsage(1, 2))
                .withFinishReason(FinishReason.LENGTH);

        for (int i = 0; i < 2; i++) {
            ChatResponse response = chatResponse(model);
            assertThat(response.tokenUsage()).isEqualTo(new TokenUsage(1, 2));
            assertThat(response.finishReason()).isEqualTo(FinishReason.LENGTH);
        }
    }

    @Test
    void should_report_the_given_token_usages_in_order_then_repeat_the_last_one() {

        ChatModelMock model =
                ChatModelMock.thatAlwaysResponds("Hi").withTokenUsages(new TokenUsage(1, 2), new TokenUsage(3, 4));

        assertThat(chatResponse(model).tokenUsage()).isEqualTo(new TokenUsage(1, 2));
        assertThat(chatResponse(model).tokenUsage()).isEqualTo(new TokenUsage(3, 4));
        assertThat(chatResponse(model).tokenUsage()).isEqualTo(new TokenUsage(3, 4));
    }

    @Test
    void should_report_no_token_usage_and_finish_reason_by_default() {

        ChatResponse response = chatResponse(ChatModelMock.thatAlwaysResponds("Hi"));

        assertThat(response.tokenUsage()).isNull();
        assertThat(response.finishReason()).isNull();
    }

    @Test
    void should_call_a_tool_and_then_respond() {

        ChatModelMock model = ChatModelMock.thatCallsTool("cancelBooking", Map.of("bookingNumber", "MS-777"))
                .thenResponds("cancelled");

        ToolExecutionRequest toolCall = chat(model).toolExecutionRequests().get(0);
        assertThat(toolCall.name()).isEqualTo("cancelBooking");
        assertThat(toolCall.arguments()).isEqualTo("{\"bookingNumber\":\"MS-777\"}");
        assertThat(toolCall.id()).isEqualTo("call_cancelBooking_1");
        assertThat(chat(model).text()).isEqualTo("cancelled");
        assertThat(model.requests()).hasSize(2);
    }

    @Test
    void should_take_the_steps_in_order_then_repeat_the_last_one() {

        ChatModelMock model = ChatModelMock.thatResponds("first")
                .thenCallsTool("getWeather", Map.of())
                .thenResponds(AiMessage.from("third"));

        assertThat(chat(model).text()).isEqualTo("first");
        assertThat(chat(model).toolExecutionRequests()).hasSize(1);
        assertThat(chat(model).text()).isEqualTo("third");
        assertThat(chat(model).text()).isEqualTo("third");
    }

    @Test
    void should_fail_instead_of_repeating_a_last_step_with_a_tool_call() {

        ChatModelMock model = ChatModelMock.thatCallsTool("getWeather", Map.of());

        chat(model);

        assertThatThrownBy(() -> chat(model)).hasMessageContaining("has no more responses");
    }

    @Test
    void should_throw_the_scripted_exception_and_then_respond() {

        RuntimeException failure = new IllegalStateException("temporary failure");
        ChatModelMock model = ChatModelMock.thatThrows(failure).thenResponds("recovered");

        assertThatThrownBy(() -> chat(model)).isSameAs(failure);
        assertThat(chat(model).text()).isEqualTo("recovered");
    }

    @Test
    void should_repeat_a_last_step_that_throws() {

        RuntimeException failure = new IllegalStateException("down");
        ChatModelMock model = ChatModelMock.thatResponds("first").thenThrows(failure);

        assertThat(chat(model).text()).isEqualTo("first");
        assertThatThrownBy(() -> chat(model)).isSameAs(failure);
        assertThatThrownBy(() -> chat(model)).isSameAs(failure);
    }

    @Test
    void should_respond_with_a_collection_of_messages_in_order() {

        ChatModelMock model = ChatModelMock.thatResponds(List.of(AiMessage.from("first"), AiMessage.from("second")));

        assertThat(chat(model).text()).isEqualTo("first");
        assertThat(chat(model).text()).isEqualTo("second");
    }

    @Test
    void should_not_accept_an_empty_collection_of_messages() {

        assertThatThrownBy(() -> ChatModelMock.thatResponds(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("aiMessages");
    }

    @Test
    void should_not_accept_steps_on_a_mock_without_a_script() {

        assertThatThrownBy(() -> ChatModelMock.thatAlwaysResponds("Hi").thenResponds("again"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Steps can only be added");
        assertThatThrownBy(() -> ChatModelMock.thatAlwaysResponds(AiMessage.from("Hi")).thenCallsTool("a", Map.of()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> ChatModelMock.thatResponds(request -> AiMessage.from("Hi"))
                        .thenThrows(new RuntimeException()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> ChatModelMock.thatAlwaysThrows(new RuntimeException()).thenResponds("again"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void should_always_throw_the_given_exception() {

        RuntimeException failure = new IllegalArgumentException("invalid");
        ChatModelMock model = ChatModelMock.thatAlwaysThrows(failure);

        assertThatThrownBy(() -> chat(model)).isSameAs(failure);
        assertThatThrownBy(() -> chat(model)).isSameAs(failure);
    }

    @Test
    void should_give_calls_of_the_same_tool_different_ids() {

        ChatModelMock model =
                ChatModelMock.thatCallsTool("a", Map.of()).thenCallsTool("a", Map.of()).thenResponds("done");

        assertThat(chat(model).toolExecutionRequests().get(0).id()).isEqualTo("call_a_1");
        assertThat(chat(model).toolExecutionRequests().get(0).id()).isEqualTo("call_a_2");
    }

    @Test
    void should_not_accept_the_same_tool_call_twice_in_one_answer() {

        AiMessage answer = AiMessage.from(toolCall("a", Map.of("x", 1)), toolCall("a", Map.of("x", 1)));

        assertThatThrownBy(() -> ChatModelMock.thatResponds(answer, AiMessage.from("done")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("the same tool call twice");
    }

    @Test
    void should_throw_exactly_the_scripted_exception_without_mapping_it() {

        RuntimeException failure = new HttpException(429, "Too many requests");

        assertThatThrownBy(() -> chat(ChatModelMock.thatThrows(failure))).isSameAs(failure);
        assertThatThrownBy(() -> chat(ChatModelMock.thatAlwaysThrows(failure))).isSameAs(failure);
        assertThatThrownBy(() -> chat(ChatModelMock.thatResponds(request -> {
                    throw failure;
                })))
                .isSameAs(failure);
    }

    @Test
    void should_take_the_steps_in_order_with_chat_async() {

        ChatModelMock model = ChatModelMock.thatResponds("first").thenResponds("second");

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
        ChatModelMock model = ChatModelMock.thatThrows(failure).thenResponds("recovered");

        assertThat(model.chatAsync(REQUEST))
                .failsWithin(5, SECONDS)
                .withThrowableThat()
                .havingCause()
                .isSameAs(failure);
        assertThat(model.chatAsync(REQUEST)).succeedsWithin(5, SECONDS);
    }

    @Test
    void should_fail_the_future_of_chat_async_instead_of_repeating_a_tool_call() {

        ChatModelMock model = ChatModelMock.thatCallsTool("getWeather", Map.of());
        chat(model);

        assertThat(model.chatAsync(REQUEST))
                .failsWithin(5, SECONDS)
                .withThrowableThat()
                .havingCause()
                .withMessageContaining("has no more responses");
    }

    @Test
    void should_report_the_given_model_name_in_every_response() {

        ChatModelMock model = ChatModelMock.thatAlwaysResponds("Hi").withModelName("model");

        assertThat(chatResponse(model).modelName()).isEqualTo("model");
        assertThat(chatResponse(model).modelName()).isEqualTo("model");
    }

    @Test
    void should_report_the_given_model_names_in_order_then_repeat_the_last_one() {

        ChatModelMock model = ChatModelMock.thatAlwaysResponds("Hi").withModelNames("a", "b");

        assertThat(chatResponse(model).modelName()).isEqualTo("a");
        assertThat(chatResponse(model).modelName()).isEqualTo("b");
        assertThat(chatResponse(model).modelName()).isEqualTo("b");
    }

    @Test
    void should_report_the_given_finish_reasons_in_order_then_repeat_the_last_one() {

        ChatModelMock model =
                ChatModelMock.thatAlwaysResponds("Hi").withFinishReasons(FinishReason.LENGTH, FinishReason.STOP);

        assertThat(chatResponse(model).finishReason()).isEqualTo(FinishReason.LENGTH);
        assertThat(chatResponse(model).finishReason()).isEqualTo(FinishReason.STOP);
        assertThat(chatResponse(model).finishReason()).isEqualTo(FinishReason.STOP);
    }

    @Test
    void should_report_the_given_provider() {

        assertThat(ChatModelMock.thatAlwaysResponds("Hi").withProvider(ModelProvider.OPEN_AI).provider())
                .isEqualTo(ModelProvider.OPEN_AI);
    }

    @Test
    void should_accept_an_empty_answer() {

        assertThat(chat(ChatModelMock.thatAlwaysResponds("")).text()).isEmpty();
        assertThat(chat(ChatModelMock.thatResponds("")).text()).isEmpty();
    }

    @Test
    void should_return_a_copy_of_the_requests() {

        ChatModelMock model = ChatModelMock.thatAlwaysResponds("Hi");
        chat(model);

        List<ChatRequest> requests = model.requests();
        chat(model);

        assertThat(requests).hasSize(1);
        assertThat(model.requests()).hasSize(2);
    }

    @Test
    void should_fail_when_there_is_not_exactly_one_request() {

        ChatModelMock model = ChatModelMock.thatAlwaysResponds("Hi");

        assertThatThrownBy(model::request)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Expected exactly 1 chat request, but received 0");
    }

    @Test
    void should_not_accept_null_arguments() {

        assertThatThrownBy(() -> ChatModelMock.thatThrows(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ChatModelMock.thatAlwaysThrows(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ChatModelMock.thatResponds("Hi").thenThrows(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ChatModelMock.thatResponds((Function<ChatRequest, AiMessage>) null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ChatModelMock.thatAlwaysResponds("Hi").withProvider(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_build_several_tool_calls_for_one_answer() {

        ChatModelMock model = ChatModelMock.thatResponds(
                AiMessage.from(toolCall("a", Map.of("x", 1)), toolCall("b", Map.of("y", "z"))), AiMessage.from("done"));

        assertThat(chat(model).toolExecutionRequests())
                .extracting(ToolExecutionRequest::name, ToolExecutionRequest::arguments)
                .containsExactly(tuple("a", "{\"x\":1}"), tuple("b", "{\"y\":\"z\"}"));
    }

    @Test
    void should_call_a_tool_with_raw_json_arguments() {

        ChatModelMock model = ChatModelMock.thatCallsTool("a", "{not json").thenCallsTool("b", "{\"x\":1}");

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

    private static AiMessage chat(ChatModelMock model) {
        return chatResponse(model).aiMessage();
    }

    private static ChatResponse chatResponse(ChatModelMock model) {
        return model.chat(ChatRequest.builder().messages(UserMessage.from("Hello")).build());
    }
}
