package dev.langchain4j.model.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.message.UserMessage;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModerationRequestTest {

    @Test
    void should_throw_when_neither_text_nor_messages_set() {
        assertThatThrownBy(() -> ModerationRequest.builder().build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Either text or messages must be set");
    }

    @Test
    void should_throw_when_messages_is_empty_list() {
        assertThatThrownBy(() -> ModerationRequest.builder().messages(List.of()).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Either text or messages must be set");
    }

    @Test
    void should_throw_when_messages_is_null() {
        assertThatThrownBy(() -> ModerationRequest.builder().messages(null).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Either text or messages must be set");
    }

    @Test
    void should_create_request_with_text() {
        // when
        ModerationRequest request =
                ModerationRequest.builder().text("some text").build();

        // then
        assertThat(request.text()).isEqualTo("some text");
        assertThat(request.messages()).isNull();
        assertThat(request.hasText()).isTrue();
        assertThat(request.hasMessages()).isFalse();
    }

    @Test
    void should_create_request_with_messages() {
        // given
        UserMessage message = UserMessage.from("hello");

        // when
        ModerationRequest request =
                ModerationRequest.builder().messages(List.of(message)).build();

        // then
        assertThat(request.text()).isNull();
        assertThat(request.messages()).containsExactly(message);
        assertThat(request.hasText()).isFalse();
        assertThat(request.hasMessages()).isTrue();
    }

    @Test
    void should_create_request_with_both_text_and_messages() {
        // given
        UserMessage message = UserMessage.from("hello");

        // when
        ModerationRequest request = ModerationRequest.builder()
                .text("some text")
                .messages(List.of(message))
                .build();

        // then
        assertThat(request.text()).isEqualTo("some text");
        assertThat(request.messages()).containsExactly(message);
        assertThat(request.hasText()).isTrue();
        assertThat(request.hasMessages()).isTrue();
    }

    @Test
    void should_preserve_order_of_messages() {
        // given
        UserMessage first = UserMessage.from("first");
        UserMessage second = UserMessage.from("second");
        UserMessage third = UserMessage.from("third");

        // when
        ModerationRequest request = ModerationRequest.builder()
                .messages(List.of(first, second, third))
                .build();

        // then
        assertThat(request.messages()).containsExactly(first, second, third);
    }

    @Test
    void should_have_correct_equals_and_hashCode() {
        // given
        ModerationRequest request1 = ModerationRequest.builder().text("text").build();
        ModerationRequest request2 = ModerationRequest.builder().text("text").build();
        ModerationRequest request3 = ModerationRequest.builder().text("other").build();

        // then
        assertThat(request1).isEqualTo(request2);
        assertThat(request1.hashCode()).isEqualTo(request2.hashCode());
        assertThat(request1).isNotEqualTo(request3);
    }

    @Test
    void should_create_copy_with_toBuilder() {
        // given
        ModerationRequest original =
                ModerationRequest.builder().text("original").build();

        // when
        ModerationRequest copy = original.toBuilder().text("modified").build();

        // then
        assertThat(original.text()).isEqualTo("original");
        assertThat(copy.text()).isEqualTo("modified");
    }

    @Test
    void should_create_request_with_multiple_messages() {
        // given
        UserMessage first = UserMessage.from("hello");
        UserMessage second = UserMessage.from("world");

        // when
        ModerationRequest request =
                ModerationRequest.builder().messages(List.of(first, second)).build();

        // then
        assertThat(request.messages()).containsExactly(first, second);
    }
}
