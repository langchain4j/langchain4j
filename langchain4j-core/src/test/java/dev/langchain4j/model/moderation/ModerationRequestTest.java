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
        assertThatThrownBy(() -> ModerationRequest.builder().texts(List.of()).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_throw_when_messages_is_null() {
        assertThatThrownBy(() -> ModerationRequest.builder().texts(null).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_create_request_with_single_message() {
        // when
        ModerationRequest request =
                ModerationRequest.builder().texts(List.of("some text")).build();

        // then
        assertThat(request.texts()).containsExactly("some text");
        assertThat(request.modelName()).isNull();
    }

    @Test
    void should_create_request_with_multiple_messages() {
        // when
        ModerationRequest request = ModerationRequest.builder()
                .texts(List.of("first", "second", "third"))
                .build();

        // then
        assertThat(request.texts()).containsExactly("first", "second", "third");
    }

    @Test
    void should_have_correct_equals_and_hashCode() {
        // given
        ModerationRequest request1 =
                ModerationRequest.builder().texts(List.of("text")).build();
        ModerationRequest request2 =
                ModerationRequest.builder().texts(List.of("text")).build();
        ModerationRequest request3 =
                ModerationRequest.builder().texts(List.of("other")).build();
        ModerationRequest request4 = ModerationRequest.builder()
                .texts(List.of("text"))
                .modelName("model-a")
                .build();
        ModerationRequest request5 = ModerationRequest.builder()
                .texts(List.of("text"))
                .modelName("model-a")
                .build();

        // then
        assertThat(request1).isEqualTo(request2);
        assertThat(request1.hashCode()).isEqualTo(request2.hashCode());
        assertThat(request1).isNotEqualTo(request3);
        assertThat(request4).isEqualTo(request5);
        assertThat(request4.hashCode()).isEqualTo(request5.hashCode());
        assertThat(request1).isNotEqualTo(request4);
    }

    @Test
    void should_create_copy_with_toBuilder() {
        // given
        ModerationRequest original = ModerationRequest.builder()
                .texts(List.of("original"))
                .modelName("original-model")
                .build();

        // when
        ModerationRequest copy = original.toBuilder().build();
        ModerationRequest modified = original.toBuilder()
                .texts(List.of("modified"))
                .modelName("modified-model")
                .build();

        // then
        assertThat(copy.texts()).containsExactly("original");
        assertThat(copy.modelName()).isEqualTo("original-model");
        assertThat(modified.texts()).containsExactly("modified");
        assertThat(modified.modelName()).isEqualTo("modified-model");
    }

    @Test
    void should_create_request_with_modelName() {
        // when
        ModerationRequest request = ModerationRequest.builder()
                .texts(List.of("some text"))
                .modelName("custom-model")
                .build();

        // then
        assertThat(request.texts()).containsExactly("some text");
        assertThat(request.modelName()).isEqualTo("custom-model");
    }
}
