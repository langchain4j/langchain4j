package dev.langchain4j.test.judge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class JudgeContextTest {

    @Test
    void should_create_user_message_context() {
        UserMessageContext context = JudgeContext.from("Where is my booking?");

        assertThat(context.text()).isEqualTo("Where is my booking?");
        assertThat(context).isEqualTo(UserMessageContext.from("Where is my booking?"));
        assertThat(context).hasSameHashCodeAs(UserMessageContext.from("Where is my booking?"));
        assertThat(context).isNotEqualTo(UserMessageContext.from("Cancel my booking"));
        assertThat(context).hasToString("UserMessageContext{text=Where is my booking?}");
    }

    @Test
    void should_reject_blank_user_message() {
        assertThatThrownBy(() -> JudgeContext.from((String) null))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userMessage");
        assertThatThrownBy(() -> UserMessageContext.from(" "))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userMessage");
    }

    @Test
    void should_create_conversation_context_with_an_immutable_copy() {
        List<ChatMessage> messages =
                new ArrayList<>(List.of(UserMessage.from("Where is my booking?"), AiMessage.from("Which one?")));

        ConversationContext context = JudgeContext.from(messages);
        messages.clear();

        assertThat(context.messages())
                .containsExactly(UserMessage.from("Where is my booking?"), AiMessage.from("Which one?"));
        assertThatThrownBy(() -> context.messages().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void should_compare_conversation_contexts() {
        ConversationContext context = ConversationContext.from(List.of(UserMessage.from("Where is my booking?")));
        ConversationContext same = ConversationContext.from(List.of(UserMessage.from("Where is my booking?")));

        assertThat(context).isEqualTo(same).hasSameHashCodeAs(same);
        assertThat(context).isNotEqualTo(ConversationContext.from(List.of(UserMessage.from("Cancel it"))));
        assertThat(context).isNotEqualTo(UserMessageContext.from("Where is my booking?"));
        assertThat(context.toString()).startsWith("ConversationContext{messages=[");
    }

    @Test
    void should_reject_empty_conversation_or_null_message() {
        assertThatThrownBy(() -> JudgeContext.from((List<ChatMessage>) null))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("conversation");
        assertThatThrownBy(() -> JudgeContext.from(List.of()))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("conversation");
        assertThatThrownBy(() -> ConversationContext.from(Arrays.asList(UserMessage.from("Hi"), null)))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("message");
    }
}
