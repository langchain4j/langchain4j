package dev.langchain4j.data.message;

import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@SuppressWarnings("deprecation")
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

    @SafeVarargs
    public static <T> List<T> listOf(T... elements) {
        return Collections.unmodifiableList(new ArrayList<>(Arrays.asList(elements)));
    }

    @Test
    public void test_hasSingleText() {
        assertThat(new UserMessage("text").hasSingleText()).isTrue();
        assertThat(new UserMessage("name", "text").hasSingleText()).isTrue();
        assertThat(new UserMessage("name", listOf(new TextContent("text"))).hasSingleText()).isTrue();
        assertThat(new UserMessage("name", listOf(new TextContent("abc"), new TextContent("def"))).hasSingleText()).isFalse();
        assertThat(new UserMessage(listOf(new TextContent("text"))).hasSingleText()).isTrue();
        assertThat(new UserMessage(listOf(new TextContent("abc"), new TextContent("def"))).hasSingleText()).isFalse();

        assertThat(new UserMessage("text").text()).isEqualTo("text");

        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> new UserMessage("name", listOf(new TextContent("abc"), new TextContent("def"))).text())
                .withMessageContaining("Expecting single text content, but got:");
    }

    @Test
    public void test_builders() {
        assertThat(new UserMessage("text"))
            .isEqualTo(UserMessage.from("text"))
            .isEqualTo(UserMessage.from(new TextContent("text")))
            .isEqualTo(UserMessage.from(listOf(new TextContent("text"))))
            .isEqualTo(UserMessage.userMessage("text"))
            .isEqualTo(UserMessage.userMessage(new TextContent("text")))
            .isEqualTo(UserMessage.userMessage(listOf(new TextContent("text"))));

        assertThat(new UserMessage("name", "text"))
            .isEqualTo(UserMessage.from("name", "text"))
            .isEqualTo(UserMessage.from("name", new TextContent("text")))
            .isEqualTo(UserMessage.from("name", listOf(new TextContent("text"))))
            .isEqualTo(UserMessage.userMessage("name", "text"))
            .isEqualTo(UserMessage.userMessage("name", new TextContent("text")))
            .isEqualTo(UserMessage.userMessage("name", listOf(new TextContent("text"))));

        assertThat(new UserMessage("name", listOf(new TextContent("abc"), new TextContent("def"))))
            .isEqualTo(UserMessage.from("name", listOf(new TextContent("abc"), new TextContent("def"))))
            .isEqualTo(UserMessage.from("name", new TextContent("abc"), new TextContent("def")))
            .isEqualTo(UserMessage.userMessage("name", listOf(new TextContent("abc"), new TextContent("def"))))
            .isEqualTo(UserMessage.userMessage("name", new TextContent("abc"), new TextContent("def")));

        assertThat(new UserMessage(listOf(new TextContent("abc"), new TextContent("def"))))
            .isEqualTo(UserMessage.from(listOf(new TextContent("abc"), new TextContent("def"))))
            .isEqualTo(UserMessage.from(new TextContent("abc"), new TextContent("def")))
            .isEqualTo(UserMessage.userMessage(listOf(new TextContent("abc"), new TextContent("def"))))
            .isEqualTo(UserMessage.userMessage(new TextContent("abc"), new TextContent("def")));
    }
}