package dev.langchain4j.model.chat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

class ChatModelTest implements WithAssertions {

    public static class UpperCaseEchoModel implements ChatModel {

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            List<ChatMessage> messages = chatRequest.messages();
            UserMessage lastMessage = (UserMessage) messages.get(messages.size() - 1);
            return ChatResponse.builder()
                    .aiMessage(new AiMessage(lastMessage.singleText().toUpperCase(Locale.ROOT)))
                    .build();
        }
    }

    @Test
    void generate() {
        ChatModel model = new UpperCaseEchoModel();

        assertThat(model.chat("how are you?")).isEqualTo("HOW ARE YOU?");

        {
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(new UserMessage("Hello"));
            messages.add(new AiMessage("Hi"));
            messages.add(new UserMessage("How are you?"));

            ChatResponse response = model.chat(messages);

            assertThat(response.aiMessage().text()).isEqualTo("HOW ARE YOU?");
            assertThat(response.tokenUsage()).isNull();
            assertThat(response.finishReason()).isNull();
        }

        {
            ChatResponse response =
                    model.chat(new UserMessage("Hello"), new AiMessage("Hi"), new UserMessage("How are you?"));

            assertThat(response.aiMessage().text()).isEqualTo("HOW ARE YOU?");
            assertThat(response.tokenUsage()).isNull();
            assertThat(response.finishReason()).isNull();
        }
    }
}
