package dev.langchain4j.model.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class ModerationRequestTest {

    @Test
    void should_throw_when_messages_not_set() {
        assertThatThrownBy(() -> ModerationRequest.builder().build()).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_throw_when_messages_is_empty_list() {
        assertThatThrownBy(() -> ModerationRequest.builder().messages(List.of()).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_throw_when_messages_is_null() {
        assertThatThrownBy(() -> ModerationRequest.builder().messages(null).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_create_request_with_single_message() {
        // when
        ModerationRequest request =
                ModerationRequest.builder().messages(List.of("some text")).build();

        // then
        assertThat(request.messages()).containsExactly("some text");
    }

    @Test
    void should_create_request_with_multiple_messages() {
        // when
        ModerationRequest request = ModerationRequest.builder()
                .messages(List.of("first", "second", "third"))
                .build();

        // then
        assertThat(request.messages()).containsExactly("first", "second", "third");
    }

    @Test
    void should_preserve_order_of_messages() {
        // when
        ModerationRequest request = ModerationRequest.builder()
                .messages(List.of("first", "second", "third"))
                .build();

        // then
        assertThat(request.messages()).containsExactly("first", "second", "third");
    }

    @Test
    void should_have_correct_equals_and_hashCode() {
        // given
        ModerationRequest request1 =
                ModerationRequest.builder().messages(List.of("text")).build();
        ModerationRequest request2 =
                ModerationRequest.builder().messages(List.of("text")).build();
        ModerationRequest request3 =
                ModerationRequest.builder().messages(List.of("other")).build();

        // then
        assertThat(request1).isEqualTo(request2);
        assertThat(request1.hashCode()).isEqualTo(request2.hashCode());
        assertThat(request1).isNotEqualTo(request3);
    }

    @Test
    void should_create_copy_with_toBuilder() {
        // given
        ModerationRequest original =
                ModerationRequest.builder().messages(List.of("original")).build();

        // when
        ModerationRequest copy =
                original.toBuilder().messages(List.of("modified")).build();

        // then
        assertThat(original.messages()).containsExactly("original");
        assertThat(copy.messages()).containsExactly("modified");
    }
}
