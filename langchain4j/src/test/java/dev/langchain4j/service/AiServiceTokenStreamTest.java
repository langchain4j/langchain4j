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

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class AiServiceTokenStreamTest {

    List<ChatMessage> messages = new ArrayList<>();

    @Mock
    List<Content> content;

    @Mock
    Object memoryId;

    AiServiceTokenStream tokenStream;

    @BeforeEach
    void prepareMessages() {
        this.messages.add(dev.langchain4j.data.message.SystemMessage.from("A system message"));
        this.tokenStream = setupAiServiceTokenStream();
    }

    @Test
    void start_with_onPartialResponse_shouldNotThrowException() {
        tokenStream
                .onPartialResponse(System.out::println)
                .ignoreErrors();

        assertThatNoException().isThrownBy(() -> tokenStream.start());
    }

    @Test
    void start_withOnNext_shouldNotThrowException() {
        tokenStream
                .onNext(System.out::println)
                .ignoreErrors();

        assertThatNoException().isThrownBy(() -> tokenStream.start());
    }

    @Test
    void start_onPartialResponseNotInvoked_shouldThrowException() {
        tokenStream
                .ignoreErrors();

        assertThatThrownBy(() -> tokenStream.start())
                .isExactlyInstanceOf(IllegalConfigurationException.class)
                .hasMessage("One of [onPartialResponse, onNext] must be invoked on TokenStream exactly 1 time");
    }

    @Test
    void start_onPartialResponseInvokedMultipleTimes_shouldThrowException() {
        tokenStream
                .onPartialResponse(System.out::println)
                .onPartialResponse(System.out::println)
                .ignoreErrors();

        assertThatThrownBy(() -> tokenStream.start())
                .isExactlyInstanceOf(IllegalConfigurationException.class)
                .hasMessage("One of [onPartialResponse, onNext] must be invoked on TokenStream exactly 1 time");
    }

    @Test
    void start_onNextInvokedMultipleTimes_shouldThrowException() {
        tokenStream
                .onNext(System.out::println)
                .onNext(System.out::println)
                .ignoreErrors();

        assertThatThrownBy(() -> tokenStream.start())
                .isExactlyInstanceOf(IllegalConfigurationException.class)
                .hasMessage("One of [onPartialResponse, onNext] must be invoked on TokenStream exactly 1 time");
    }

    @Test
    void start_onPartialResponseAndOnNextInvoked_shouldThrowException() {
        tokenStream
                .onPartialResponse(System.out::println)
                .onNext(System.out::println)
                .ignoreErrors();

        assertThatThrownBy(() -> tokenStream.start())
                .isExactlyInstanceOf(IllegalConfigurationException.class)
                .hasMessage("One of [onPartialResponse, onNext] must be invoked on TokenStream exactly 1 time");
    }

    @Test
    void start_onErrorNorIgnoreErrorsInvoked_shouldThrowException() {
        tokenStream
                .onPartialResponse(System.out::println);

        assertThatThrownBy(() -> tokenStream.start())
                .isExactlyInstanceOf(IllegalConfigurationException.class)
                .hasMessage("One of [onError, ignoreErrors] must be invoked on TokenStream exactly 1 time");
    }

    @Test
    void start_onErrorAndIgnoreErrorsInvoked_shouldThrowException() {
        tokenStream
                .onPartialResponse(System.out::println)
                .onError(Throwable::printStackTrace)
                .ignoreErrors();

        assertThatThrownBy(() -> tokenStream.start())
                .isExactlyInstanceOf(IllegalConfigurationException.class)
                .hasMessage("One of [onError, ignoreErrors] must be invoked on TokenStream exactly 1 time");
    }

    @Test
    void start_onCompleteInvokedOneTime_shouldNotThrowException() {
        tokenStream
                .onPartialResponse(System.out::println)
                .ignoreErrors()
                .onComplete(r -> System.out.println(r.content()));

        assertThatNoException().isThrownBy(() -> tokenStream.start());
    }

    @Test
    void start_onCompleteResponseInvokedMultipleTimes_shouldThrowException() {
        tokenStream
                .onPartialResponse(System.out::println)
                .ignoreErrors()
                .onCompleteResponse(r -> System.out.println(r.aiMessage()))
                .onCompleteResponse(r -> System.out.println(r.aiMessage()));

        assertThatThrownBy(() -> tokenStream.start())
                .isExactlyInstanceOf(IllegalConfigurationException.class)
                .hasMessage("One of [onCompleteResponse, onComplete] can be invoked on TokenStream at most 1 time");
    }

    @Test
    void start_onCompleteInvokedMultipleTimes_shouldThrowException() {
        tokenStream
                .onPartialResponse(System.out::println)
                .ignoreErrors()
                .onComplete(r -> System.out.println(r.content()))
                .onComplete(r -> System.out.println(r.content()));

        assertThatThrownBy(() -> tokenStream.start())
                .isExactlyInstanceOf(IllegalConfigurationException.class)
                .hasMessage("One of [onCompleteResponse, onComplete] can be invoked on TokenStream at most 1 time");
    }

    @Test
    void start_onCompleteResponseAndOnCompleteInvoked_shouldThrowException() {
        tokenStream
                .onPartialResponse(System.out::println)
                .ignoreErrors()
                .onCompleteResponse(r -> System.out.println(r.aiMessage()))
                .onComplete(r -> System.out.println(r.content()));

        assertThatThrownBy(() -> tokenStream.start())
                .isExactlyInstanceOf(IllegalConfigurationException.class)
                .hasMessage("One of [onCompleteResponse, onComplete] can be invoked on TokenStream at most 1 time");
    }

    private AiServiceTokenStream setupAiServiceTokenStream() {
        StreamingChatLanguageModel model = mock(StreamingChatLanguageModel.class);
        AiServiceContext context = new AiServiceContext(getClass());
        context.streamingChatModel = model;
        return new AiServiceTokenStream(messages, null, null, content, context, memoryId);
    }
}
