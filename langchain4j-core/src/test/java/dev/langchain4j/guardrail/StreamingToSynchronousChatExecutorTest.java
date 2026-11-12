package dev.langchain4j.guardrail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.chat.mock.StreamingChatModelMock;
import dev.langchain4j.model.chat.request.ChatRequest;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

class StreamingToSynchronousChatExecutorTest {

    private static final ChatRequest CHAT_REQUEST =
            ChatRequest.builder().messages(UserMessage.from("Hello")).build();

    @Test
    void execute_should_fail_instead_of_waiting_forever_when_the_stream_fails() {
        IllegalStateException failure = new IllegalStateException("stream failed");
        List<Throwable> handledErrors = new CopyOnWriteArrayList<>();
        ChatExecutor executor = ChatExecutor.builder(new StreamingChatModelMock(failure))
                .errorHandler(handledErrors::add)
                .chatRequest(CHAT_REQUEST)
                .invocationContext(InvocationContext.builder().build())
                .build();

        assertTimeoutPreemptively(
                Duration.ofSeconds(5), () -> assertThatThrownBy(executor::execute).isSameAs(failure));
        assertThat(handledErrors).containsExactly(failure);
    }

    @Test
    void execute_should_fail_when_the_model_throws_from_chat() {
        IllegalStateException failure = new IllegalStateException("cannot send the request");
        List<Throwable> handledErrors = new CopyOnWriteArrayList<>();
        ChatExecutor executor = ChatExecutor.builder(new StreamingChatModelMock(failure).throwingFromChat())
                .errorHandler(handledErrors::add)
                .chatRequest(CHAT_REQUEST)
                .invocationContext(InvocationContext.builder().build())
                .build();

        assertTimeoutPreemptively(
                Duration.ofSeconds(5), () -> assertThatThrownBy(executor::execute).isSameAs(failure));
        assertThat(handledErrors).containsExactly(failure);
    }
}
