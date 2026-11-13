package dev.langchain4j.test.mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.moderation.Moderation;
import dev.langchain4j.model.moderation.ModerationRequest;
import dev.langchain4j.model.moderation.ModerationResponse;
import dev.langchain4j.model.moderation.listener.ModerationModelListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class MockModerationModelTest {

    @Test
    void should_take_the_listeners_as_a_list() {

        ModerationModelListener listener = new ModerationModelListener() {};
        List<ModerationModelListener> listeners = new ArrayList<>(List.of(listener));
        MockModerationModel model = MockModerationModel.thatDoesNotFlag().withListeners(listeners);

        listeners.clear();

        assertThat(model.listeners()).containsExactly(listener);
    }

    @Test
    void should_not_flag() {

        assertThat(MockModerationModel.thatDoesNotFlag().moderate("hello").content().flagged())
                .isFalse();
    }

    @Test
    void should_flag_with_the_given_text() {

        Moderation moderation = MockModerationModel.thatFlags("bad").moderate("hello").content();

        assertThat(moderation.flagged()).isTrue();
        assertThat(moderation.flaggedText()).isEqualTo("bad");
    }

    @Test
    void should_compute_the_moderation_from_the_request() {

        MockModerationModel model =
                MockModerationModel.thatModerates(request -> Moderation.flagged(request.texts().get(0)));

        assertThat(model.moderate("hello").content().flaggedText()).isEqualTo("hello");
    }

    @Test
    void should_record_the_requests() {

        MockModerationModel model = MockModerationModel.thatDoesNotFlag().thenRepeats();

        model.moderate("first");

        assertThat(model.request().texts()).containsExactly("first");

        model.moderate("second");

        assertThat(model.requests()).hasSize(2);
        assertThatThrownBy(model::request)
                .hasMessage("Expected exactly 1 moderation request, but received 2. To check the last one, use"
                        + " lastRequest(); to check each of them, use requests(), for example requests().get(0)");
        assertThat(model.lastRequest()).isSameAs(model.requests().get(1));
    }

    @Test
    void should_throw_the_given_exception_for_every_request_when_repeating() {

        RuntimeException failure = new IllegalStateException("down");

        MockModerationModel model = MockModerationModel.thatFails(failure).thenRepeats();

        assertThatThrownBy(() -> model.moderate("hello")).isSameAs(failure);
        assertThatThrownBy(() -> model.moderate("again")).isSameAs(failure);
    }

    @Test
    void should_not_accept_null_arguments() {

        assertThatThrownBy(() -> MockModerationModel.thatModerates(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MockModerationModel.thatFails(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MockModerationModel.thatFlags(null)).isInstanceOf(IllegalArgumentException.class);
        MockModerationModel model = MockModerationModel.thatDoesNotFlag();
        assertThatThrownBy(() -> model.thenModerates((Function<ModerationRequest, Moderation>) null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> model.thenFails(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> model.thenFlags(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_answer_one_request_per_step_and_fail_a_request_after_the_last_one() {

        MockModerationModel model = MockModerationModel.thatFlags("bad")
                .thenDoesNotFlag()
                .thenModerates(request -> Moderation.flagged(request.texts().get(0)));

        assertThat(model.moderate("first").content().flaggedText()).isEqualTo("bad");
        assertThat(model.moderate("second").content().flagged()).isFalse();
        assertThat(model.moderate("third").content().flaggedText()).isEqualTo("third");
        assertThatThrownBy(() -> model.moderate(List.of(UserMessage.from("one"), UserMessage.from("two"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MockModerationModel received request 4, but its script has only 3 steps")
                .hasMessageContaining("thenRepeats()")
                .hasMessageContaining("The texts of request 4: \"one\" \"two\"");
        assertThat(model.requests()).hasSize(4);
    }

    @Test
    void should_show_at_most_three_texts_of_a_request_after_the_last_step() {

        MockModerationModel model = MockModerationModel.thatDoesNotFlag();
        model.moderate("first");

        assertThatThrownBy(() -> model.moderate(List.of(
                        UserMessage.from("a"),
                        UserMessage.from("b"),
                        UserMessage.from("c"),
                        UserMessage.from("d"),
                        UserMessage.from("e"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("The texts of request 2: \"a\" \"b\" \"c\" and 2 more");
    }

    @Test
    void should_let_a_request_fail_and_the_next_one_succeed() {

        RuntimeException timeout = new IllegalStateException("Read timed out");
        MockModerationModel model =
                MockModerationModel.thatFails(timeout).thenDoesNotFlag().thenRepeats();

        assertThatThrownBy(() -> model.moderate("hello")).isSameAs(timeout);
        assertThat(model.moderate("hello").content().flagged()).isFalse();
        assertThat(model.moderate("again").content().flagged()).isFalse();
    }

    @Test
    void should_fail_a_later_step_with_the_given_exception() {

        RuntimeException failure = new IllegalStateException("down");
        MockModerationModel model = MockModerationModel.thatDoesNotFlag().thenFails(failure);

        assertThat(model.moderate("hello").content().flagged()).isFalse();
        assertThatThrownBy(() -> model.moderate("again")).isSameAs(failure);
    }

    @Test
    void should_flag_the_first_message_and_accept_the_next_ones() {

        MockModerationModel model = MockModerationModel.thatFlags("How do I hack ...")
                .thenDoesNotFlag()
                .thenRepeats();

        assertThat(model.moderate("How do I hack ...").content().flagged()).isTrue();
        assertThat(model.moderate("Hi").content().flagged()).isFalse();
        assertThat(model.moderate("Thanks").content().flagged()).isFalse();
    }

    @Test
    void should_repeat_only_the_last_step_and_compute_it_again_for_each_request() {

        AtomicInteger computed = new AtomicInteger();
        MockModerationModel model = MockModerationModel.thatFlags("bad")
                .thenModerates(request -> Moderation.flagged("call " + computed.incrementAndGet()))
                .thenRepeats();

        assertThat(model.moderate("first").content().flaggedText()).isEqualTo("bad");
        assertThat(model.moderate("second").content().flaggedText()).isEqualTo("call 1");
        assertThat(model.moderate("third").content().flaggedText()).isEqualTo("call 2");
    }

    @Test
    void should_not_accept_a_step_after_repeating() {

        MockModerationModel model = MockModerationModel.thatDoesNotFlag().thenRepeats();

        assertThatThrownBy(() -> model.thenFlags("bad"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("thenRepeats() must be the last call");
        assertThatThrownBy(model::thenRepeats).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void should_not_record_a_null_request() {

        MockModerationModel model = MockModerationModel.thatDoesNotFlag();

        assertThatThrownBy(() -> model.doModerate(null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(model.requests()).isEmpty();
        assertThat(model.moderate("hello").content().flagged()).isFalse();
    }

    @Test
    void should_answer_with_exactly_the_given_response_including_its_metadata() {

        ModerationResponse given = ModerationResponse.builder()
                .moderation(Moderation.flagged("bad"))
                .metadata(Map.of("category", "illicit"))
                .build();
        MockModerationModel model = MockModerationModel.thatResponds(given).thenRepeats();

        assertThat(model.moderate(ModerationRequest.builder().texts(List.of("bad")).build()))
                .isEqualTo(given);
        assertThat(model.moderate("bad").metadata()).containsEntry("category", "illicit");
    }

    @Test
    void should_give_the_whole_response_in_a_later_step() {

        ModerationResponse given =
                ModerationResponse.builder().moderation(Moderation.notFlagged()).build();
        MockModerationModel model = MockModerationModel.thatFlags("bad").thenResponds(given);

        assertThat(model.moderate("first").content().flagged()).isTrue();
        assertThat(model.moderate("second").content().flagged()).isFalse();
        assertThatThrownBy(() -> model.moderate("third")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> MockModerationModel.thatResponds(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> model.thenResponds(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
