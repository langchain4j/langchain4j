package dev.langchain4j.data.message;

import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

class UserMessageTest implements WithAssertions {
    @Test
    public void test_accessors() {
        UserMessage m = new UserMessage("name", "text");
        assertThat(m.type()).isEqualTo(ChatMessageType.USER);
        assertThat(m.text()).isEqualTo("text");
        assertThat(m.contents()).containsExactly(TextContent.from("text"));
        assertThat(m.name()).isEqualTo("name");
        assertThat(m).hasToString("UserMessage { name = \"name\" contents = [TextContent { text = \"text\" }] }");
    }

    @Test
    public void test_equals_hashCode() {
        UserMessage m1 = new UserMessage("name", "text");
        UserMessage m2 = new UserMessage("name", "text");
        UserMessage m3 = new UserMessage("text");
        UserMessage m4 = new UserMessage("text");

        assertThat(m1)
                .isEqualTo(m1)
                .isNotEqualTo(null)
                .isNotEqualTo(new Object())
                .isEqualTo(m2)
                .hasSameHashCodeAs(m2)
                .isNotEqualTo(m3)
                .doesNotHaveSameHashCodeAs(m3)
                .isNotEqualTo(new UserMessage("foo", "text"))
                .isNotEqualTo(new UserMessage("name", "foo"));

        assertThat(m3)
                .isEqualTo(m3)
                .isNotEqualTo(null)
                .isNotEqualTo(new Object())
                .isEqualTo(m4)
                .hasSameHashCodeAs(m4);
    }

    @Test
    public void test_builders() {
        assertThat(UserMessage.from("text"))
                .isEqualTo(new UserMessage("text"));
        assertThat(UserMessage.from("name", "text"))
                .isEqualTo(new UserMessage("name", "text"));
        assertThat(UserMessage.userMessage("text"))
                .isEqualTo(new UserMessage("text"));
        assertThat(UserMessage.userMessage("foo", "text"))
                .isEqualTo(new UserMessage("foo", "text"));
    }
}