package dev.langchain4j.service;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.exception.IllegalConfigurationException;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.rag.content.Content;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class AiServiceTokenStreamTest {

    List<ChatMessage> messages = new ArrayList<>();
    @Mock
    List<Content> content;
    @Mock
    AiServiceContext context;
    @Mock
    Object memoryId;

    AiServiceTokenStream tokenStream;

    @BeforeEach
    void prepareMessages() {
        this.messages.add(dev.langchain4j.data.message.SystemMessage.from("A system message"));
        this.tokenStream = setupAiServiceTokenStream();
    }

    @Test
    void start_correctConfigured_shouldNotThrowException() {
        tokenStream
                .onNext(System.out::println)
                .ignoreErrors();

        assertThatNoException().isThrownBy(() -> tokenStream.start());
    }

    @Test
    void start_onNextNotInvoked_shouldThrowException() {
        tokenStream
                .ignoreErrors();

        assertThatExceptionOfType(IllegalConfigurationException.class).isThrownBy(() -> tokenStream.start());
    }

    @Test
    void start_onNextInvokedMultipleTimes_shouldThrowException() {
        tokenStream
                .onNext(System.out::println)
                .onNext(System.out::println)
                .ignoreErrors();

        assertThatExceptionOfType(IllegalConfigurationException.class).isThrownBy(() -> tokenStream.start());
    }

    @Test
    void start_onErrorNorIgnoreErrorsInvoked_shouldThrowException() {
        tokenStream
                .onNext(System.out::println);

        assertThatExceptionOfType(IllegalConfigurationException.class).isThrownBy(() -> tokenStream.start());
    }

    @Test
    void start_onErrorAndIgnoreErrorsInvoked_shouldThrowException() {
        tokenStream
                .onNext(System.out::println)
                .onError(Throwable::printStackTrace)
                .ignoreErrors();

        assertThatExceptionOfType(IllegalConfigurationException.class).isThrownBy(() -> tokenStream.start());
    }

    @Test
    void start_onCompleteInvokedOneTime_shouldNotThrowException() {
        tokenStream
                .onNext(System.out::println)
                .ignoreErrors()
                .onComplete(r -> System.out.println(r.content()));

        assertThatNoException().isThrownBy(() -> tokenStream.start());
    }

    @Test
    void start_onCompleteInvokedMultipleTimes_shouldThrowException() {
        tokenStream
                .onNext(System.out::println)
                .ignoreErrors()
                .onComplete(r -> System.out.println(r.content()))
                .onComplete(r -> System.out.println(r.content()));

        assertThatExceptionOfType(IllegalConfigurationException.class).isThrownBy(() -> tokenStream.start());
    }

    private AiServiceTokenStream setupAiServiceTokenStream() {
        StreamingChatLanguageModel model = mock(StreamingChatLanguageModel.class);
        AiServiceContext context = new AiServiceContext(getClass());
        context.streamingChatModel = model;
        return new AiServiceTokenStream(messages, null, null, content, context, memoryId);
    }
}
