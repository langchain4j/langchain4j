package dev.langchain4j.observability.api.event;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.isA;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.observability.api.event.AiServiceResponseReceivedEvent.AiServiceResponseReceivedEventBuilder;
import dev.langchain4j.observability.event.DefaultAiServiceResponseReceivedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class AiServiceResponseReceivedEventTests {

    private static final InvocationContext INVOCATION_CONTEXT = InvocationContext.builder().interfaceName("SomeInterface").methodName("someMethod").methodArgument("one").methodArgument("two").chatMemoryId("one").build();

    private static final ChatRequest CHAT_REQUEST = ChatRequest.builder().messages(UserMessage.userMessage("Hi")).build();
    public static final ChatResponse CHAT_RESPONSE = ChatResponse.builder().aiMessage(AiMessage.from("Message!")).build();

    @Test
    void buildWithoutChatRequestThrowsIllegalArgumentException() {
        assertIaeWithMessage(() -> AiServiceResponseReceivedEvent.builder()
                .invocationContext(INVOCATION_CONTEXT)
                .response(CHAT_RESPONSE).build(), "request cannot be null");
    }


    @Test
    void buildWithoutChatResponseThrowsIllegalArgumentException() {
        assertIaeWithMessage(() -> AiServiceResponseReceivedEvent.builder()
                .invocationContext(INVOCATION_CONTEXT)
                .request(CHAT_REQUEST)
                .build(), "response cannot be null");
    }

    @Test
    void buildWithoutContextThrowsIllegalArgumentException() {
        assertIaeWithMessage(() -> AiServiceResponseReceivedEvent.builder()
                .request(CHAT_REQUEST)
                .response(CHAT_RESPONSE)
                .build(), "invocationContext cannot be null");
    }

    @Test
    void buildHasCorrectType() {
        final AiServiceResponseReceivedEvent event = AiServiceResponseReceivedEvent.builder()
                .request(CHAT_REQUEST)
                .response(CHAT_RESPONSE)
                .invocationContext(INVOCATION_CONTEXT)
                .build();
        assertThat(event, isA(DefaultAiServiceResponseReceivedEvent.class));
    }

    @Test
    void createWithoutChatRequestThrowsIllegalArgumentException() {

        final AiServiceResponseReceivedEventBuilder noRequestBuilder = AiServiceResponseReceivedEvent.builder()
                .invocationContext(INVOCATION_CONTEXT)
                .response(CHAT_RESPONSE);

        assertIaeWithMessage(() -> new DefaultAiServiceResponseReceivedEvent(noRequestBuilder), "request cannot be null");
    }

    @Test
    void createWithoutChatResponseThrowsIllegalArgumentException() {

        final AiServiceResponseReceivedEventBuilder noRequestBuilder = AiServiceResponseReceivedEvent.builder()
                .invocationContext(INVOCATION_CONTEXT)
                .request(CHAT_REQUEST);

        assertIaeWithMessage(() -> new DefaultAiServiceResponseReceivedEvent(noRequestBuilder), "response cannot be null");
    }

    @Test
    void createWithoutContextThrowsIllegalArgumentException() {

        final AiServiceResponseReceivedEventBuilder noRequestBuilder = AiServiceResponseReceivedEvent.builder()
                .response(CHAT_RESPONSE).request(CHAT_REQUEST);

        assertIaeWithMessage(() -> new DefaultAiServiceResponseReceivedEvent(noRequestBuilder), "invocationContext cannot be null");
    }

    @Test
    void gettersDoNotReturnNull() {
        final AiServiceResponseReceivedEvent event = AiServiceResponseReceivedEvent.builder()
                .request(CHAT_REQUEST)
                .response(CHAT_RESPONSE)
                .invocationContext(INVOCATION_CONTEXT)
                .build();

        assertThat(event.invocationContext(), notNullValue());
        assertThat(event.request(), notNullValue());
        assertThat(event.response(), notNullValue());
    }

    private void assertIaeWithMessage(Executable executable, String expectedMessage) {
        final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, executable);
        final String message = exception.getMessage();
        assertThat(message, message.contains(expectedMessage));
    }


}
