package dev.langchain4j.agentic.scope;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ContentType;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.service.memory.ChatMemoryAccess;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class DefaultAgenticScopeConcurrentReadTest {

    @Test
    void should_render_context_from_a_snapshot_during_concurrent_registration() throws Exception {
        DefaultAgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();
        ConcurrentAppend concurrentAppend = new ConcurrentAppend();
        Content image = () -> {
            concurrentAppend.pauseReader();
            return ContentType.IMAGE;
        };
        AgentInstance agent = mock(AgentInstance.class);
        when(agent.name()).thenReturn("agent");
        when(agent.agentId()).thenReturn("agent-id");
        ChatMemoryAccess agentObject = mock(ChatMemoryAccess.class);
        scope.registerContext(agent, agentObject, UserMessage.from(image), "first answer");

        String conversation = concurrentAppend.readWhileAppending(
                () -> scope.contextAsConversation("agent"),
                () -> scope.registerContext(agent, agentObject, UserMessage.from("follow-up"), "second answer"));

        assertThat(conversation).isEqualTo("""
                User: "[image]"
                agent agent: "first answer"
                """);
        assertThat(scope.contextAsConversation("agent")).isEqualTo(conversation + """
                User: "follow-up"
                agent agent: "second answer"
                """);
    }

    @Test
    void should_filter_invocations_by_name_from_a_snapshot_during_concurrent_registration() throws Exception {
        DefaultAgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();
        ConcurrentAppend concurrentAppend = new ConcurrentAppend();
        AgentInvocation first = mock(AgentInvocation.class);
        when(first.agentName()).thenAnswer(ignored -> {
            concurrentAppend.pauseReader();
            return "agent";
        });
        scope.registerAgentInvocation(first);
        scope.registerAgentInvocation(new AgentInvocation(Object.class, "other", "other-id", Map.of(), null));
        AgentInvocation appended = new AgentInvocation(Object.class, "agent", "agent-id", Map.of(), null);

        List<AgentInvocation> invocations = concurrentAppend.readWhileAppending(
                () -> scope.agentInvocations("agent"), () -> scope.registerAgentInvocation(appended));

        assertThat(invocations).containsExactly(first);
        assertThat(scope.agentInvocations("agent")).containsExactly(first, appended);
    }

    @Test
    void should_filter_invocations_by_type_from_a_snapshot_during_concurrent_registration() throws Exception {
        DefaultAgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();
        ConcurrentAppend concurrentAppend = new ConcurrentAppend();
        AgentInvocation first = mock(AgentInvocation.class);
        when(first.agentType()).thenAnswer(ignored -> {
            concurrentAppend.pauseReader();
            return Object.class;
        });
        scope.registerAgentInvocation(first);
        scope.registerAgentInvocation(new AgentInvocation(String.class, "agent", "agent-id", Map.of(), null));
        AgentInvocation appended = new AgentInvocation(Object.class, "agent", "agent-id", Map.of(), null);

        List<AgentInvocation> invocations = concurrentAppend.readWhileAppending(
                () -> scope.agentInvocations(Object.class), () -> scope.registerAgentInvocation(appended));

        assertThat(invocations).containsExactly(first);
        assertThat(scope.agentInvocations(Object.class)).containsExactly(first, appended);
    }

    private static class ConcurrentAppend {

        private final CountDownLatch iterationStarted = new CountDownLatch(1);
        private final CountDownLatch appendCompleted = new CountDownLatch(1);

        void pauseReader() {
            iterationStarted.countDown();
            try {
                assertThat(appendCompleted.await(10, SECONDS))
                        .as("the writer appended while the reader was paused")
                        .isTrue();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for the writer", e);
            }
        }

        <T> T readWhileAppending(Callable<T> read, Runnable append) throws Exception {
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<T> reading = executor.submit(read);
                assertThat(iterationStarted.await(10, SECONDS))
                        .as("the reader started iterating")
                        .isTrue();
                executor.submit(() -> {
                            append.run();
                            appendCompleted.countDown();
                        })
                        .get(10, SECONDS);
                return reading.get(10, SECONDS);
            } finally {
                appendCompleted.countDown();
                executor.shutdownNow();
            }
        }
    }
}
