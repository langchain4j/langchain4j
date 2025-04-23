package dev.langchain4j.service;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.rag.content.Content;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiServiceTokenStreamTest {

    static Consumer<String> DUMMY_PARTIAL_RESPONSE_HANDLER = (partialResponse) -> {};

    static Consumer<Throwable> DUMMY_ERROR_HANDLER = (error) -> {};

    static Consumer<ChatResponse> DUMMY_CHAT_RESPONSE_HANDLER = (chatResponse) -> {};

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
        tokenStream.onPartialResponse(DUMMY_PARTIAL_RESPONSE_HANDLER).ignoreErrors();

        assertThatNoException().isThrownBy(() -> tokenStream.start());
    }

    @Test
    void start_onPartialResponseNotInvoked_shouldThrowException() {
        tokenStream.ignoreErrors();

        assertThatThrownBy(() -> tokenStream.start())
                .isExactlyInstanceOf(IllegalConfigurationException.class)
                .hasMessage("onPartialResponse must be invoked on TokenStream exactly 1 time");
    }

    @Test
    void start_onPartialResponseInvokedMultipleTimes_shouldThrowException() {
        tokenStream
                .onPartialResponse(DUMMY_PARTIAL_RESPONSE_HANDLER)
                .onPartialResponse(DUMMY_PARTIAL_RESPONSE_HANDLER)
                .ignoreErrors();

        assertThatThrownBy(() -> tokenStream.start())
                .isExactlyInstanceOf(IllegalConfigurationException.class)
                .hasMessage("onPartialResponse must be invoked on TokenStream exactly 1 time");
    }

    @Test
    void start_onErrorNorIgnoreErrorsInvoked_shouldThrowException() {
        tokenStream.onPartialResponse(DUMMY_PARTIAL_RESPONSE_HANDLER);

        assertThatThrownBy(() -> tokenStream.start())
                .isExactlyInstanceOf(IllegalConfigurationException.class)
                .hasMessage("One of [onError, ignoreErrors] must be invoked on TokenStream exactly 1 time");
    }

    @Test
    void start_onErrorAndIgnoreErrorsInvoked_shouldThrowException() {
        tokenStream
                .onPartialResponse(DUMMY_PARTIAL_RESPONSE_HANDLER)
                .onError(DUMMY_ERROR_HANDLER)
                .ignoreErrors();

        assertThatThrownBy(() -> tokenStream.start())
                .isExactlyInstanceOf(IllegalConfigurationException.class)
                .hasMessage("One of [onError, ignoreErrors] must be invoked on TokenStream exactly 1 time");
    }

    @Test
    void start_onCompleteResponseInvokedMultipleTimes_shouldThrowException() {
        tokenStream
                .onPartialResponse(DUMMY_PARTIAL_RESPONSE_HANDLER)
                .ignoreErrors()
                .onCompleteResponse(DUMMY_CHAT_RESPONSE_HANDLER)
                .onCompleteResponse(DUMMY_CHAT_RESPONSE_HANDLER);

        assertThatThrownBy(() -> tokenStream.start())
                .isExactlyInstanceOf(IllegalConfigurationException.class)
                .hasMessage("onCompleteResponse can be invoked on TokenStream at most 1 time");
    }

    private AiServiceTokenStream setupAiServiceTokenStream() {
        StreamingChatModel model = mock(StreamingChatModel.class);
        AiServiceContext context = new AiServiceContext(getClass());
        context.streamingChatModel = model;
        return new AiServiceTokenStream(AiServiceTokenStreamParameters.builder()
                .messages(messages)
                .retrievedContents(content)
                .context(context)
                .memoryId(memoryId)
                .build());
    }
}
