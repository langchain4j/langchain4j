package dev.langchain4j.model.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ModerationResponseTest {

    @Test
    void should_throw_when_moderation_is_not_set() {
        assertThatThrownBy(() -> ModerationResponse.builder().build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("moderation cannot be null");
    }

    @Test
    void should_throw_when_moderation_is_null() {
        assertThatThrownBy(() -> ModerationResponse.builder().moderation(null).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("moderation cannot be null");
    }

    @Test
    void should_create_response_with_moderation_only() {
        // given
        Moderation moderation = Moderation.notFlagged();

        // when
        ModerationResponse response =
                ModerationResponse.builder().moderation(moderation).build();

        // then
        assertThat(response.moderation()).isEqualTo(moderation);
        assertThat(response.metadata()).isNull();
    }

    @Test
    void should_create_response_with_all_fields() {
        // given
        Moderation moderation = Moderation.flagged("bad content");
        Map<String, Object> metadata = Map.of("key", "value");

        // when
        ModerationResponse response = ModerationResponse.builder()
                .moderation(moderation)
                .metadata(metadata)
                .build();

        // then
        assertThat(response.moderation()).isEqualTo(moderation);
        assertThat(response.metadata()).containsEntry("key", "value");
    }

    @Test
    void should_create_response_with_flagged_moderation() {
        // given
        Moderation moderation = Moderation.flagged("offensive text");

        // when
        ModerationResponse response =
                ModerationResponse.builder().moderation(moderation).build();

        // then
        assertThat(response.moderation().flagged()).isTrue();
        assertThat(response.moderation().flaggedText()).isEqualTo("offensive text");
    }

    @Test
    void should_create_response_with_not_flagged_moderation() {
        // given
        Moderation moderation = Moderation.notFlagged();

        // when
        ModerationResponse response =
                ModerationResponse.builder().moderation(moderation).build();

        // then
        assertThat(response.moderation().flagged()).isFalse();
        assertThat(response.moderation().flaggedText()).isNull();
    }

    @Test
    void should_have_correct_equals_and_hashCode() {
        // given
        Moderation moderation = Moderation.notFlagged();

        ModerationResponse response1 =
                ModerationResponse.builder().moderation(moderation).build();
        ModerationResponse response2 =
                ModerationResponse.builder().moderation(moderation).build();
        ModerationResponse response3 = ModerationResponse.builder()
                .moderation(Moderation.flagged("bad"))
                .build();

        // then
        assertThat(response1).isEqualTo(response2);
        assertThat(response1.hashCode()).isEqualTo(response2.hashCode());
        assertThat(response1).isNotEqualTo(response3);
    }

    @Test
    void should_create_copy_with_toBuilder() {
        // given
        Moderation original = Moderation.notFlagged();
        Moderation updated = Moderation.flagged("bad");
        ModerationResponse originalResponse =
                ModerationResponse.builder().moderation(original).build();

        // when
        ModerationResponse copy =
                originalResponse.toBuilder().moderation(updated).build();

        // then
        assertThat(originalResponse.moderation()).isEqualTo(original);
        assertThat(copy.moderation()).isEqualTo(updated);
    }

    @Test
    void should_create_response_with_metadata() {
        // given
        Moderation moderation = Moderation.notFlagged();
        Map<String, Object> metadata = Map.of("key", "value");

        // when
        ModerationResponse response = ModerationResponse.builder()
                .moderation(moderation)
                .metadata(metadata)
                .build();

        // then
        assertThat(response.metadata()).containsEntry("key", "value");
    }
}
