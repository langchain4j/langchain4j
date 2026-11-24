package dev.langchain4j.observability.api.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.rag.content.Content;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RetrievalAugmentedEventTests {

    private static final InvocationContext INVOCATION_CONTEXT = InvocationContext.builder()
            .interfaceName("SomeInterface")
            .methodName("someMethod")
            .build();
    private static final UserMessage USER_MESSAGE = UserMessage.from("Can I cancel my booking?");

    @Test
    void toBuilderRoundTripsUserMessageAndContents() {
        final List<Content> contents = List.of(Content.from("Bookings can be cancelled for free."));
        final RetrievalAugmentedEvent event = RetrievalAugmentedEvent.builder()
                .invocationContext(INVOCATION_CONTEXT)
                .chatMessage(USER_MESSAGE)
                .contents(contents)
                .build();

        final RetrievalAugmentedEvent copy = event.toBuilder().build();

        assertThat(copy.invocationContext()).isEqualTo(INVOCATION_CONTEXT);
        assertThat(copy.chatMessage()).isEqualTo(USER_MESSAGE);
        assertThat(copy.contents()).isEqualTo(contents);
        assertThat(copy.eventClass()).isEqualTo(RetrievalAugmentedEvent.class);
    }

    @Test
    void contentsAreCopied() {
        final List<Content> contents = new ArrayList<>(List.of(Content.from("first")));
        final RetrievalAugmentedEvent event = RetrievalAugmentedEvent.builder()
                .invocationContext(INVOCATION_CONTEXT)
                .chatMessage(USER_MESSAGE)
                .contents(contents)
                .build();

        contents.add(Content.from("second"));

        assertThat(event.contents()).hasSize(1);
    }

    @Test
    void chatMessageIsRequired() {
        assertThatThrownBy(() -> RetrievalAugmentedEvent.builder()
                        .invocationContext(INVOCATION_CONTEXT)
                        .contents(List.of())
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chatMessage");
    }

    @Test
    void contentsAreRequired() {
        assertThatThrownBy(() -> RetrievalAugmentedEvent.builder()
                        .invocationContext(INVOCATION_CONTEXT)
                        .chatMessage(USER_MESSAGE)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contents");
    }
}
