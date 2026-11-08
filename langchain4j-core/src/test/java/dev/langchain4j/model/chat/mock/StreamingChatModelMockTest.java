package dev.langchain4j.model.chat.mock;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatModelStreamingEvent;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteResponse;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import java.util.ArrayList;
import java.util.List;
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

        StreamingChatModelMock model = StreamingChatModelMock.thatAlwaysStreams(AiMessage.from("Hi"));
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

        StreamingChatModelMock model = StreamingChatModelMock.thatAlwaysStreams(AiMessage.from("Hi"));

        assertThat(chat(model)).isEqualTo("Hi");
        assertThat(chat(model)).isEqualTo("Hi");
        assertThat(chat(model)).isEqualTo("Hi");
    }

    @Test
    void should_fail_instead_of_repeating_a_single_response_with_tool_calls() throws Exception {

        StreamingChatModelMock model = StreamingChatModelMock.thatAlwaysStreams(AiMessage.from(
                ToolExecutionRequest.builder().id("1").name("getWeather").arguments("{}").build()));

        chat(model);
        assertThatThrownBy(() -> chat(model)).hasMessageContaining("has no more responses");
    }

    @Test
    void should_stream_several_responses_in_order_then_repeat_the_last_one() throws Exception {

        StreamingChatModelMock model =
                StreamingChatModelMock.thatAlwaysStreams(AiMessage.from("first"), AiMessage.from("second"));

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
        StreamingChatModelMock model = StreamingChatModelMock.thatAlwaysStreams(AiMessage.from("first"), toolCall);

        assertThat(chat(model)).isEqualTo("first");
        chat(model);
        assertThatThrownBy(() -> chat(model))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("StreamingChatModelMock has no more responses: the last scripted response (with tool"
                        + " calls) would be repeated, which would loop forever. Script the response that follows the"
                        + " tool results.");
    }

    @Test
    void should_stream_given_tokens() throws Exception {

        StreamingChatModelMock model = StreamingChatModelMock.thatAlwaysStreams("Hel", "lo");
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

        StreamingChatModelMock model = StreamingChatModelMock.thatAlwaysStreams(List.of("Hel", "lo"));

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
                StreamingChatModelMock.thatAlwaysThrowsExceptionWithMessage("boom").throwingFromChat();

        // the publisher reports the error to its subscriber, which collect(...) rethrows
        assertThatThrownBy(() -> collect(model))
                .hasCauseInstanceOf(IllegalStateException.class)
                .hasMessageContaining("throwingFromChat()");
    }

    @Test
    void should_publish_the_given_tokens_again_for_every_request() {

        StreamingChatModelMock model = StreamingChatModelMock.thatAlwaysStreams("Hel", "lo");

        collect(model);
        List<ChatModelStreamingEvent> events = collect(model);

        assertThat(events.subList(0, 2))
                .extracting(event -> ((PartialResponse) event).text())
                .containsExactly("Hel", "lo");
    }

    @Test
    void should_publish_one_token_per_character_when_built_from_ai_message() {

        StreamingChatModelMock model = StreamingChatModelMock.thatAlwaysStreams(AiMessage.from("Hello"));

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

        StreamingChatModelMock model = StreamingChatModelMock.thatAlwaysStreams("Hi")
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

        StreamingChatModelMock model = StreamingChatModelMock.thatAlwaysStreams("Hi")
                .withTokenUsages(new TokenUsage(1, 2), new TokenUsage(3, 4));

        assertThat(chatResponse(model).tokenUsage()).isEqualTo(new TokenUsage(1, 2));
        assertThat(chatResponse(model).tokenUsage()).isEqualTo(new TokenUsage(3, 4));
        assertThat(chatResponse(model).tokenUsage()).isEqualTo(new TokenUsage(3, 4));
    }

    @Test
    void should_publish_the_given_token_usages_in_order_then_repeat_the_last_one() {

        StreamingChatModelMock model = StreamingChatModelMock.thatAlwaysStreams("Hi")
                .withTokenUsages(new TokenUsage(1, 2), new TokenUsage(3, 4))
                .withFinishReason(FinishReason.STOP);

        assertThat(completeResponse(collect(model)).tokenUsage()).isEqualTo(new TokenUsage(1, 2));
        assertThat(completeResponse(collect(model)).tokenUsage()).isEqualTo(new TokenUsage(3, 4));
        ChatResponse third = completeResponse(collect(model));
        assertThat(third.tokenUsage()).isEqualTo(new TokenUsage(3, 4));
        assertThat(third.finishReason()).isEqualTo(FinishReason.STOP);
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
