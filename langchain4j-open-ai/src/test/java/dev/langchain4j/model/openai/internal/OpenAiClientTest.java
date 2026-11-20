package dev.langchain4j.model.openai.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

import dev.langchain4j.exception.AsyncNotSupportedException;
import dev.langchain4j.model.chat.response.ChatModelStreamingEvent;
import dev.langchain4j.model.openai.internal.chat.ChatCompletionRequest;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.Test;

class OpenAiClientTest {

    @Test
    void chatCompletionPublisher_should_return_a_failing_publisher_by_default() {

        OpenAiClient client = mock(OpenAiClient.class, CALLS_REAL_METHODS); // only the default methods are called

        Flow.Publisher<ChatModelStreamingEvent> publisher = client.chatCompletionPublisher(
                ChatCompletionRequest.builder().build(), new ChatCompletionOptions(false, false));

        List<Object> signals = new CopyOnWriteArrayList<>();
        publisher.subscribe(new Flow.Subscriber<>() {

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                signals.add("onSubscribe");
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
            public void onComplete() {
                signals.add("onComplete");
            }
        });

        assertThat(signals).hasSize(2);
        assertThat(signals.get(0)).isEqualTo("onSubscribe");
        assertThat((Throwable) signals.get(1))
                .isInstanceOf(AsyncNotSupportedException.class)
                .hasMessageContaining("chatCompletionPublisher() is not implemented by");
    }
}
