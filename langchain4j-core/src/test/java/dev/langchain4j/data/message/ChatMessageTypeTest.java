package dev.langchain4j.data.message;

import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

class ChatMessageTypeTest implements WithAssertions {
    @Test
    void test() {
        assertThat(ChatMessageType.SYSTEM.messageClass()).isEqualTo(SystemMessage.class);
        assertThat(ChatMessageType.USER.messageClass()).isEqualTo(UserMessage.class);
        assertThat(ChatMessageType.AI.messageClass()).isEqualTo(AiMessage.class);
        assertThat(ChatMessageType.TOOL_EXECUTION_RESULT.messageClass()).isEqualTo(ToolExecutionResultMessage.class);
    }
}
