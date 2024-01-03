package dev.langchain4j.data.message;

import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

class ChatMessageTest implements WithAssertions {
    public static class ExampleChatMessage extends ChatMessage {
        public ExampleChatMessage(String text) {
            super(text);
        }

        @Override
        public ChatMessageType type() {
            return ChatMessageType.SYSTEM;
        }
    }

    @Test
    public void test() {
        ExampleChatMessage m = new ExampleChatMessage("Hello");
        assertThat(m.text()).isEqualTo("Hello");
    }

}