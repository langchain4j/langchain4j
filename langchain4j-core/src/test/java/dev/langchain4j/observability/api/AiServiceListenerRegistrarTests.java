package dev.langchain4j.observability.api;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.observability.api.event.AiServiceStartedEvent;
import dev.langchain4j.observability.api.listener.AiServiceStartedListener;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AiServiceListenerRegistrarTests {

    private static final AiServiceStartedEvent STARTED_EVENT = AiServiceStartedEvent.builder()
            .invocationContext(InvocationContext.builder()
                    .interfaceName("I")
                    .methodName("m")
                    .build())
            .userMessage(UserMessage.from("hi"))
            .build();

    @Test
    void correctInstance() {
        assertThat(AiServiceListenerRegistrar.newInstance())
                .isNotNull()
                .isExactlyInstanceOf(DefaultAiServiceListenerRegistrar.class);
    }

    @Test
    void listenerRegisteredOnOneAgentDoesNotReceiveEventsFromAnotherAgent() {
        // Simulate a singleton SPI factory
        AiServiceListenerRegistrar singletonDelegate = new DefaultAiServiceListenerRegistrar();

        AiServiceListenerRegistrar agentA = new DefaultAiServiceListenerRegistrar(singletonDelegate);
        AiServiceListenerRegistrar agentB = new DefaultAiServiceListenerRegistrar(singletonDelegate);

        AtomicInteger agentACount = new AtomicInteger();
        agentA.register((AiServiceStartedListener) event -> agentACount.incrementAndGet());

        // Agent B fires an event — agent A's listener must NOT be triggered
        agentB.fireEvent(STARTED_EVENT);

        assertThat(agentACount).hasValue(0);
    }

    @Test
    void delegateReceivesEventsFromAllAgents() {
        AiServiceListenerRegistrar singletonDelegate = new DefaultAiServiceListenerRegistrar();
        AtomicInteger delegateCount = new AtomicInteger();
        singletonDelegate.register((AiServiceStartedListener) event -> delegateCount.incrementAndGet());

        AiServiceListenerRegistrar agentA = new DefaultAiServiceListenerRegistrar(singletonDelegate);
        AiServiceListenerRegistrar agentB = new DefaultAiServiceListenerRegistrar(singletonDelegate);

        agentA.fireEvent(STARTED_EVENT);
        agentB.fireEvent(STARTED_EVENT);

        assertThat(delegateCount).hasValue(2);
    }

    @Test
    void perAgentShouldThrowSettingDoesNotAffectDelegate() {
        AiServiceListenerRegistrar singletonDelegate = new DefaultAiServiceListenerRegistrar();
        singletonDelegate.register((AiServiceStartedListener) event -> {
            throw new RuntimeException("delegate error");
        });

        AiServiceListenerRegistrar agentA = new DefaultAiServiceListenerRegistrar(singletonDelegate);
        AiServiceListenerRegistrar agentB = new DefaultAiServiceListenerRegistrar(singletonDelegate);

        // Agent A enables throw-on-error — must not mutate the delegate's setting
        agentA.shouldThrowExceptionOnEventError(true);

        // Agent B fires through the same delegate — the delegate swallows exceptions by default
        // so no exception should propagate from agentB's fire
        assertThat(agentB).satisfies(r -> {
            try {
                r.fireEvent(STARTED_EVENT);
            } catch (RuntimeException e) {
                // delegate exception swallowed — this path should not be reached if isolation holds
                org.junit.jupiter.api.Assertions.fail(
                        "delegate error propagated to unrelated agent: " + e.getMessage());
            }
        });
    }
}
