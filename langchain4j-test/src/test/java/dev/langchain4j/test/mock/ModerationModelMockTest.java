package dev.langchain4j.test.mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.moderation.Moderation;
import org.junit.jupiter.api.Test;

class ModerationModelMockTest {

    @Test
    void should_never_flag() {

        assertThat(ModerationModelMock.thatNeverFlags().moderate("hello").content().flagged())
                .isFalse();
    }

    @Test
    void should_always_flag_with_the_given_text() {

        Moderation moderation =
                ModerationModelMock.thatAlwaysFlags("bad").moderate("hello").content();

        assertThat(moderation.flagged()).isTrue();
        assertThat(moderation.flaggedText()).isEqualTo("bad");
    }

    @Test
    void should_compute_the_moderation_from_the_request() {

        ModerationModelMock model = ModerationModelMock.thatModerates(
                request -> Moderation.flagged(request.texts().get(0)));

        assertThat(model.moderate("hello").content().flaggedText()).isEqualTo("hello");
    }

    @Test
    void should_record_the_requests() {

        ModerationModelMock model = ModerationModelMock.thatNeverFlags();

        model.moderate("first");

        assertThat(model.request().texts()).containsExactly("first");

        model.moderate("second");

        assertThat(model.requests()).hasSize(2);
        assertThatThrownBy(model::request).hasMessage("Expected exactly 1 moderation request, but received 2");
    }

    @Test
    void should_always_throw_the_given_exception() {

        RuntimeException failure = new IllegalStateException("down");

        assertThatThrownBy(() -> ModerationModelMock.thatThrows(failure).moderate("hello"))
                .isSameAs(failure);
    }

    @Test
    void should_report_the_given_provider() {

        assertThat(ModerationModelMock.thatNeverFlags().withProvider(ModelProvider.OPEN_AI).provider())
                .isEqualTo(ModelProvider.OPEN_AI);
    }

    @Test
    void should_not_accept_null_arguments() {

        assertThatThrownBy(() -> ModerationModelMock.thatModerates(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ModerationModelMock.thatThrows(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ModerationModelMock.thatNeverFlags().withProvider(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
