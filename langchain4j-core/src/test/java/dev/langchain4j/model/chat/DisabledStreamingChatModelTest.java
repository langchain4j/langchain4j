package dev.langchain4j.model.chat;

import static dev.langchain4j.model.LambdaStreamingResponseHandler.onPartialResponse;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.DisabledModelTest;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.junit.jupiter.api.Test;

import java.util.List;

class DisabledStreamingChatModelTest extends DisabledModelTest<StreamingChatModel> {

    private final StreamingChatModel model = new DisabledStreamingChatModel();

    public DisabledStreamingChatModelTest() {
        super(StreamingChatModel.class);
    }

    @Test
    void methodsShouldThrowException() {
        UserMessage userMessage = UserMessage.from("Hello");
        // the failure goes to onError, and this handler's onError throws it on the calling thread
        StreamingChatResponseHandler handler = onPartialResponse(token -> {});
        performAssertion(() -> model.chat(ChatRequest.builder().messages(userMessage).build(), handler));
        performAssertion(() -> model.chat(userMessage.singleText(), handler));
        performAssertion(() -> model.chat(List.of(userMessage), handler));
    }
}
