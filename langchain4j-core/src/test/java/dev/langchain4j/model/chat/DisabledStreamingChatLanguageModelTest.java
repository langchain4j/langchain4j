package dev.langchain4j.model.chat;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.DisabledModelTest;
import dev.langchain4j.model.chat.request.ChatRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

class DisabledStreamingChatLanguageModelTest extends DisabledModelTest<StreamingChatLanguageModel> {

    private final StreamingChatLanguageModel model = new DisabledStreamingChatLanguageModel();

    public DisabledStreamingChatLanguageModelTest() {
        super(StreamingChatLanguageModel.class);
    }

    @Test
    void methodsShouldThrowException() {
        UserMessage userMessage = UserMessage.from("Hello");
        performAssertion(() -> model.chat(ChatRequest.builder().messages(userMessage).build(), null));
        performAssertion(() -> model.chat(userMessage.singleText(), null));
        performAssertion(() -> model.chat(List.of(userMessage), null));
    }
}
