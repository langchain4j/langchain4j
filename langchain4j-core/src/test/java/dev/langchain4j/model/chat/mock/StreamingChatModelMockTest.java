package dev.langchain4j.model.chat.mock;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatModelStreamingEvent;
import dev.langchain4j.model.chat.response.CompleteResponse;
import java.util.ArrayList;
import java.util.List;
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
}
