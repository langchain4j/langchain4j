package dev.langchain4j.observability.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.observability.api.event.AiServiceCancelledEvent;
import dev.langchain4j.observability.api.event.AiServiceCompletedEvent;
import dev.langchain4j.observability.api.event.AiServiceErrorEvent;
import dev.langchain4j.observability.api.event.AiServiceEvent;
import dev.langchain4j.observability.api.event.AiServiceInvocationSummaryEvent;
import dev.langchain4j.observability.api.event.AiServiceStartedEvent;
import dev.langchain4j.observability.api.listener.AiServiceCompletedListener;
import dev.langchain4j.observability.api.listener.AiServiceInvocationSummaryListener;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class NestedInvocationSummaryTest {

    enum End {
        COMPLETED,
        ERROR,
        CANCELLED
    }

    @ParameterizedTest
    @EnumSource(End.class)
    void should_nest_a_child_from_another_registrar_without_a_child_listener(End end) {
        var parentRegistrar = new DefaultAiServiceListenerRegistrar();
        var childRegistrar = new DefaultAiServiceListenerRegistrar();
        var summaries = new ArrayList<AiServiceInvocationSummaryEvent>();
        parentRegistrar.register((AiServiceInvocationSummaryListener) summaries::add);
        InvocationContext parent = context(null);
        InvocationContext child = context(parent);
        var parentStarted = started(parent);
        var childStarted = started(child);
        AiServiceEvent childEnd =
                switch (end) {
                    case COMPLETED -> completed(child);
                    case ERROR ->
                        AiServiceErrorEvent.builder()
                                .invocationContext(child)
                                .error(new IllegalStateException("child failure"))
                                .build();
                    case CANCELLED ->
                        AiServiceCancelledEvent.builder()
                                .invocationContext(child)
                                .build();
                };
        var parentEnd = completed(parent);

        parentRegistrar.fireEvent(parentStarted);
        childRegistrar.fireEvent(childStarted);
        childRegistrar.fireEvent(childEnd);
        parentRegistrar.fireEvent(parentEnd);

        assertThat(summaries).hasSize(1);
        AiServiceInvocationSummaryEvent nested =
                (AiServiceInvocationSummaryEvent) summaries.get(0).events().get(1);
        assertThat(summaries.get(0).events()).containsExactly(parentStarted, nested, parentEnd);
        assertThat(nested.invocationContext()).isEqualTo(child);
        assertThat(nested.events()).containsExactly(childStarted, childEnd);
    }

    @Test
    void should_collect_multiple_levels_with_only_a_root_listener() {
        var rootRegistrar = new DefaultAiServiceListenerRegistrar();
        var childRegistrar = new DefaultAiServiceListenerRegistrar();
        var leafRegistrar = new DefaultAiServiceListenerRegistrar();
        var summaries = new ArrayList<AiServiceInvocationSummaryEvent>();
        rootRegistrar.register((AiServiceInvocationSummaryListener) summaries::add);
        InvocationContext root = context(null);
        InvocationContext child = context(root);
        InvocationContext leaf = context(child);

        rootRegistrar.fireEvent(started(root));
        childRegistrar.fireEvent(started(child));
        leafRegistrar.fireEvent(started(leaf));
        leafRegistrar.fireEvent(completed(leaf));
        childRegistrar.fireEvent(completed(child));
        rootRegistrar.fireEvent(completed(root));

        assertThat(summaries).hasSize(1);
        var childSummary =
                (AiServiceInvocationSummaryEvent) summaries.get(0).events().get(1);
        var leafSummary =
                (AiServiceInvocationSummaryEvent) childSummary.events().get(1);
        assertThat(childSummary.invocationContext()).isEqualTo(child);
        assertThat(leafSummary.invocationContext()).isEqualTo(leaf);
    }

    @Test
    void should_not_modify_a_finished_parent_when_a_child_ends_later() {
        var registrar = new DefaultAiServiceListenerRegistrar();
        var summaries = new ArrayList<AiServiceInvocationSummaryEvent>();
        registrar.register((AiServiceInvocationSummaryListener) summaries::add);
        InvocationContext parent = context(null);
        InvocationContext child = context(parent);

        registrar.fireEvent(started(parent));
        registrar.fireEvent(started(child));
        registrar.fireEvent(completed(parent));
        registrar.fireEvent(completed(child));

        assertThat(summaries).hasSize(2);
        assertThat(summaries.get(0).events()).noneMatch(AiServiceInvocationSummaryEvent.class::isInstance);
        assertThat(summaries.get(1).invocationContext()).isEqualTo(child);

        // Ending a parent removes its cross-registrar registration; an unobserved late child is not collected.
        var unobserved = new DefaultAiServiceListenerRegistrar();
        InvocationContext lateChild = context(parent);
        unobserved.fireEvent(started(lateChild));
        var lateSummaries = new ArrayList<AiServiceInvocationSummaryEvent>();
        unobserved.register((AiServiceInvocationSummaryListener) lateSummaries::add);
        unobserved.fireEvent(completed(lateChild));
        assertThat(lateSummaries).isEmpty();
    }

    private static InvocationContext context(InvocationContext parent) {
        return InvocationContext.builder()
                .invocationId(UUID.randomUUID())
                .parentInvocationId(parent == null ? null : parent.invocationId())
                .build();
    }

    @Test
    void should_remove_the_parent_registration_even_if_a_terminal_listener_throws() {
        var parentRegistrar = new DefaultAiServiceListenerRegistrar();
        parentRegistrar.shouldThrowExceptionOnEventError(true);
        parentRegistrar.register((AiServiceInvocationSummaryListener) event -> {});
        parentRegistrar.register((AiServiceCompletedListener) event -> {
            throw new IllegalStateException("listener failure");
        });
        InvocationContext parent = context(null);
        parentRegistrar.fireEvent(started(parent));
        assertThatThrownBy(() -> parentRegistrar.fireEvent(completed(parent))).hasMessage("listener failure");

        var childRegistrar = new DefaultAiServiceListenerRegistrar();
        InvocationContext child = context(parent);
        childRegistrar.fireEvent(started(child));
        var summaries = new ArrayList<AiServiceInvocationSummaryEvent>();
        childRegistrar.register((AiServiceInvocationSummaryListener) summaries::add);
        childRegistrar.fireEvent(completed(child));
        assertThat(summaries).isEmpty();
    }

    private static AiServiceStartedEvent started(InvocationContext context) {
        return AiServiceStartedEvent.builder()
                .invocationContext(context)
                .userMessage(UserMessage.from("question"))
                .build();
    }

    private static AiServiceCompletedEvent completed(InvocationContext context) {
        return AiServiceCompletedEvent.builder()
                .invocationContext(context)
                .result("answer")
                .build();
    }
}
