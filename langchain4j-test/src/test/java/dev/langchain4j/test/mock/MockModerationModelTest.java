package dev.langchain4j.test.mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.model.moderation.Moderation;
import dev.langchain4j.model.moderation.listener.ModerationModelListener;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MockModerationModelTest {

    @Test
    void should_take_the_listeners_as_a_list() {

        ModerationModelListener listener = new ModerationModelListener() {};
        List<ModerationModelListener> listeners = new ArrayList<>(List.of(listener));
        MockModerationModel model = MockModerationModel.thatNeverFlags().withListeners(listeners);

        listeners.clear();

        assertThat(model.listeners()).containsExactly(listener);
    }

    @Test
    void should_never_flag() {

        assertThat(MockModerationModel.thatNeverFlags().moderate("hello").content().flagged())
                .isFalse();
    }

    @Test
    void should_always_flag_with_the_given_text() {

        Moderation moderation =
                MockModerationModel.thatAlwaysFlags("bad").moderate("hello").content();

        assertThat(moderation.flagged()).isTrue();
        assertThat(moderation.flaggedText()).isEqualTo("bad");
    }

    @Test
    void should_compute_the_moderation_from_the_request() {

        MockModerationModel model = MockModerationModel.thatModerates(
                request -> Moderation.flagged(request.texts().get(0)));

        assertThat(model.moderate("hello").content().flaggedText()).isEqualTo("hello");
    }

    @Test
    void should_record_the_requests() {

        MockModerationModel model = MockModerationModel.thatNeverFlags();

        model.moderate("first");

        assertThat(model.request().texts()).containsExactly("first");

        model.moderate("second");

        assertThat(model.requests()).hasSize(2);
        assertThatThrownBy(model::request).hasMessage("Expected exactly 1 moderation request, but received 2. To check each of them, use requests(), for example requests().get(0)");
    }

    @Test
    void should_always_throw_the_given_exception() {

        RuntimeException failure = new IllegalStateException("down");

        MockModerationModel model = MockModerationModel.thatFails(failure);

        assertThatThrownBy(() -> model.moderate("hello")).isSameAs(failure);
        assertThatThrownBy(() -> model.moderate("again")).isSameAs(failure);
    }

    @Test
    void should_not_accept_null_arguments() {

        assertThatThrownBy(() -> MockModerationModel.thatModerates(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MockModerationModel.thatFails(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
