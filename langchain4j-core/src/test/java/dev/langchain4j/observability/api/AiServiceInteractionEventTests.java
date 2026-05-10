package dev.langchain4j.observability.api;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.observability.api.event.AiServiceCompletedEvent;
import dev.langchain4j.observability.api.event.AiServiceErrorEvent;
import dev.langchain4j.observability.api.event.AiServiceInteractionEvent;
import dev.langchain4j.observability.api.event.AiServiceRequestIssuedEvent;
import dev.langchain4j.observability.api.event.AiServiceStartedEvent;
import dev.langchain4j.observability.api.listener.AiServiceInteractionListener;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AiServiceInteractionEventTests {

    private DefaultAiServiceListenerRegistrar registrar;
    private final List<AiServiceInteractionEvent> capturedInteractions = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        registrar = new DefaultAiServiceListenerRegistrar();
        registrar.register((AiServiceInteractionListener) capturedInteractions::add);
    }

    private InvocationContext ctx(UUID invocationId) {
        return InvocationContext.builder()
                .interfaceName("TestService")
                .methodName("chat")
                .invocationId(invocationId)
                .build();
    }

    private AiServiceStartedEvent started(InvocationContext ctx) {
        return AiServiceStartedEvent.builder()
                .invocationContext(ctx)
                .userMessage(UserMessage.from("hi"))
                .build();
    }

    private AiServiceCompletedEvent completed(InvocationContext ctx) {
        return AiServiceCompletedEvent.builder().invocationContext(ctx).build();
    }

    private AiServiceErrorEvent errored(InvocationContext ctx) {
        return AiServiceErrorEvent.builder()
                .invocationContext(ctx)
                .error(new RuntimeException("boom"))
                .build();
    }

    private AiServiceRequestIssuedEvent requestIssued(InvocationContext ctx) {
        return AiServiceRequestIssuedEvent.builder()
                .invocationContext(ctx)
                .request(ChatRequest.builder()
                        .messages(List.of(UserMessage.from("hi")))
                        .build())
                .build();
    }

    @Test
    void interactionEventFiredOnSuccessfulCompletion() {
        var ctx = ctx(UUID.randomUUID());

        registrar.fireEvent(started(ctx));
        registrar.fireEvent(requestIssued(ctx));
        registrar.fireEvent(completed(ctx));

        assertThat(capturedInteractions).hasSize(1);
        AiServiceInteractionEvent interaction = capturedInteractions.get(0);
        assertThat(interaction.isSuccessful()).isTrue();
        assertThat(interaction.events()).hasSize(3);
        assertThat(interaction.events().get(0)).isInstanceOf(AiServiceStartedEvent.class);
        assertThat(interaction.events().get(1)).isInstanceOf(AiServiceRequestIssuedEvent.class);
        assertThat(interaction.events().get(2)).isInstanceOf(AiServiceCompletedEvent.class);
    }

    @Test
    void interactionEventFiredOnError() {
        var ctx = ctx(UUID.randomUUID());

        registrar.fireEvent(started(ctx));
        registrar.fireEvent(errored(ctx));

        assertThat(capturedInteractions).hasSize(1);
        assertThat(capturedInteractions.get(0).isSuccessful()).isFalse();
        assertThat(capturedInteractions.get(0).events()).hasSize(2);
        assertThat(capturedInteractions.get(0).events().get(1)).isInstanceOf(AiServiceErrorEvent.class);
    }

    @Test
    void noInteractionEventFiredWithoutStartedEvent() {
        var ctx = ctx(UUID.randomUUID());

        // Fire completed without a preceding started — no interaction event should be emitted
        registrar.fireEvent(completed(ctx));

        assertThat(capturedInteractions).isEmpty();
    }

    @Test
    void separateInvocationsProduceSeparateInteractionEvents() {
        var ctx1 = ctx(UUID.randomUUID());
        var ctx2 = ctx(UUID.randomUUID());

        registrar.fireEvent(started(ctx1));
        registrar.fireEvent(started(ctx2));
        registrar.fireEvent(requestIssued(ctx1));
        registrar.fireEvent(completed(ctx1));
        registrar.fireEvent(completed(ctx2));

        assertThat(capturedInteractions).hasSize(2);
        // ctx1: started, requestIssued, completed
        assertThat(capturedInteractions.get(0).events()).hasSize(3);
        // ctx2: started, completed — must NOT capture ctx1's interaction event
        assertThat(capturedInteractions.get(1).events()).hasSize(2);
        assertThat(capturedInteractions.get(1).events()).noneMatch(e -> e instanceof AiServiceInteractionEvent);
    }

    @Test
    void concurrentInvocationsDoNotCrossContaminateEachOther() {
        var ctx1 = ctx(UUID.randomUUID());
        var ctx2 = ctx(UUID.randomUUID());

        registrar.fireEvent(started(ctx1));
        registrar.fireEvent(started(ctx2));
        registrar.fireEvent(requestIssued(ctx1));
        registrar.fireEvent(requestIssued(ctx2));
        registrar.fireEvent(completed(ctx2));
        registrar.fireEvent(completed(ctx1));

        assertThat(capturedInteractions).hasSize(2);
        // ctx2 completes first: started + requestIssued + completed
        assertThat(capturedInteractions.get(0).events()).hasSize(3);
        assertThat(capturedInteractions.get(0).invocationContext().invocationId())
                .isEqualTo(ctx2.invocationId());
        // ctx1 completes second: started + requestIssued + completed — no ctx2 events leaked
        assertThat(capturedInteractions.get(1).events()).hasSize(3);
        assertThat(capturedInteractions.get(1).invocationContext().invocationId())
                .isEqualTo(ctx1.invocationId());
    }

    @Test
    void eventsWithNullInvocationIdAreIgnoredByTracking() {
        // Events without an invocationId (e.g. legacy tests) must not break tracking
        var ctxNoId =
                InvocationContext.builder().interfaceName("I").methodName("m").build(); // no invocationId

        registrar.fireEvent(AiServiceStartedEvent.builder()
                .invocationContext(ctxNoId)
                .userMessage(UserMessage.from("hi"))
                .build());

        assertThat(capturedInteractions).isEmpty();
    }
}
