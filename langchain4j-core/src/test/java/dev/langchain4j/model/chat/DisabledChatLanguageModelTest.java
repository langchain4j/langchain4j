package dev.langchain4j.model.chat;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.DisabledModelTest;
import dev.langchain4j.model.chat.request.ChatRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

class DisabledChatLanguageModelTest extends DisabledModelTest<ChatLanguageModel> {

    private final ChatLanguageModel model = new DisabledChatLanguageModel();

    public DisabledChatLanguageModelTest() {
        super(ChatLanguageModel.class);
    }

    @Test
    void methodsShouldThrowException() {
        UserMessage userMessage = UserMessage.from("Hello");
        performAssertion(() -> model.chat(ChatRequest.builder().messages(userMessage).build()));
        performAssertion(() -> model.chat(userMessage.singleText()));
        performAssertion(() -> model.chat(userMessage));
        performAssertion(() -> model.chat(List.of(userMessage)));
    }
}
