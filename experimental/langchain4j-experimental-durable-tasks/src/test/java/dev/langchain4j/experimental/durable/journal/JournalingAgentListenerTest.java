package dev.langchain4j.experimental.durable.journal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.langchain4j.agentic.observability.AgentInvocationError;
import dev.langchain4j.agentic.observability.AgentRequest;
import dev.langchain4j.agentic.observability.AgentResponse;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.experimental.durable.store.InMemoryTaskExecutionStore;
import dev.langchain4j.experimental.durable.store.event.AgentInvocationCompletedEvent;
import dev.langchain4j.experimental.durable.store.event.AgentInvocationFailedEvent;
import dev.langchain4j.experimental.durable.store.event.AgentInvocationStartedEvent;
import dev.langchain4j.experimental.durable.store.event.TaskEvent;
import dev.langchain4j.experimental.durable.task.TaskId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JournalingAgentListenerTest {

    private InMemoryTaskExecutionStore store;
    private TaskId taskId;
    private JournalingAgentListener listener;

    @BeforeEach
    void setup() {
        store = new InMemoryTaskExecutionStore();
        taskId = new TaskId("journal-task-1");
        listener = new JournalingAgentListener(taskId, store);
    }

    /**
     * Creates a mockable AgentInstance (interface) with the given name and id.
     * AgentRequest/AgentResponse/AgentInvocationError are Java records and cannot be
     * mocked by Mockito on JVM versions that enforce record integrity (Java 17+).
     * We therefore construct the records directly, using a mocked AgentInstance for name/id.
     */
    private AgentInstance agentInstance(String name, String id) {
        AgentInstance agent = mock(AgentInstance.class);
        when(agent.name()).thenReturn(name);
        when(agent.agentId()).thenReturn(id);
        return agent;
    }

    @Test
    void should_return_task_id() {
        assertThat(listener.taskId()).isEqualTo(taskId);
    }

    @Test
    void should_be_inherited_by_subagents() {
        assertThat(listener.inheritedBySubagents()).isTrue();
    }

    @Test
    void should_record_agent_invocation_started_event() {
        AgentRequest request = new AgentRequest(
                null, agentInstance("summarizer", "sum-1"), Map.<String, Object>of("text", "hello world"));

        listener.beforeAgentInvocation(request);

        List<TaskEvent> events = store.loadEvents(taskId);
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(AgentInvocationStartedEvent.class);

        AgentInvocationStartedEvent started = (AgentInvocationStartedEvent) events.get(0);
        assertThat(started.taskId()).isEqualTo(taskId);
        assertThat(started.agentName()).isEqualTo("summarizer");
        assertThat(started.agentId()).isEqualTo("sum-1");
        assertThat(started.inputs()).containsEntry("text", "hello world");
    }

    @Test
    void should_record_agent_invocation_completed_event() {
        AgentResponse response = new AgentResponse(null, agentInstance("classifier", "cls-1"), Map.of(), "positive");

        listener.afterAgentInvocation(response);

        List<TaskEvent> events = store.loadEvents(taskId);
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(AgentInvocationCompletedEvent.class);

        AgentInvocationCompletedEvent completed = (AgentInvocationCompletedEvent) events.get(0);
        assertThat(completed.taskId()).isEqualTo(taskId);
        assertThat(completed.agentName()).isEqualTo("classifier");
        assertThat(completed.agentId()).isEqualTo("cls-1");
        assertThat(completed.serializedOutput()).isNotNull();
    }

    @Test
    void should_record_agent_invocation_failed_event() {
        RuntimeException error = new RuntimeException("connection timeout");
        AgentInvocationError invocationError =
                new AgentInvocationError(null, agentInstance("fetcher", "fetch-1"), Map.of(), error);

        listener.onAgentInvocationError(invocationError);

        List<TaskEvent> events = store.loadEvents(taskId);
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(AgentInvocationFailedEvent.class);

        AgentInvocationFailedEvent failed = (AgentInvocationFailedEvent) events.get(0);
        assertThat(failed.taskId()).isEqualTo(taskId);
        assertThat(failed.agentName()).isEqualTo("fetcher");
        assertThat(failed.errorMessage()).isEqualTo("connection timeout");
        assertThat(failed.stackTrace()).contains("RuntimeException");
    }

    @Test
    void should_record_events_in_order() {
        AgentRequest request = new AgentRequest(null, agentInstance("agent", "a-1"), Map.of());
        AgentResponse response = new AgentResponse(null, agentInstance("agent", "a-1"), Map.of(), "output");

        listener.beforeAgentInvocation(request);
        listener.afterAgentInvocation(response);

        List<TaskEvent> events = store.loadEvents(taskId);
        assertThat(events).hasSize(2);
        assertThat(events.get(0)).isInstanceOf(AgentInvocationStartedEvent.class);
        assertThat(events.get(1)).isInstanceOf(AgentInvocationCompletedEvent.class);
    }

    @Test
    void should_handle_null_output_gracefully() {
        AgentResponse response = new AgentResponse(null, agentInstance("nullOutputAgent", "null-1"), Map.of(), null);

        // Should not throw
        listener.afterAgentInvocation(response);

        List<TaskEvent> events = store.loadEvents(taskId);
        assertThat(events).hasSize(1);
        AgentInvocationCompletedEvent completed = (AgentInvocationCompletedEvent) events.get(0);
        assertThat(completed.serializedOutput()).isNull();
    }

    // ---- Scope-aware checkpoint callbacks ----

    @Test
    void should_call_after_agent_checkpoint_with_scope() {
        AtomicReference<AgenticScope> receivedScope = new AtomicReference<>();
        AgenticScope mockScope = mock(AgenticScope.class);

        JournalingAgentListener listenerWithCallback =
                new JournalingAgentListener(taskId, store, receivedScope::set, null);

        // Construct record directly — agenticScope() returns the first record component
        AgentResponse response = new AgentResponse(mockScope, agentInstance("agent", "a-1"), Map.of(), "result");

        listenerWithCallback.afterAgentInvocation(response);

        assertThat(receivedScope.get()).isSameAs(mockScope);
    }

    @Test
    void should_not_call_after_agent_checkpoint_when_null() {
        // The 2-arg constructor sets afterAgentCheckpoint to null — no NPE expected
        AgentResponse response = new AgentResponse(null, agentInstance("agent", "a-1"), Map.of(), "result");

        listener.afterAgentInvocation(response);

        // No exception means success — only event should be the completion event
        assertThat(store.loadEvents(taskId)).hasSize(1);
    }

    @Test
    void should_call_root_call_checkpoint_on_scope_destroyed() {
        AtomicReference<AgenticScope> receivedScope = new AtomicReference<>();
        AgenticScope mockScope = mock(AgenticScope.class);

        JournalingAgentListener listenerWithRootCallback =
                new JournalingAgentListener(taskId, store, null, receivedScope::set);

        listenerWithRootCallback.beforeAgenticScopeDestroyed(mockScope);

        assertThat(receivedScope.get()).isSameAs(mockScope);
    }

    @Test
    void should_not_call_root_call_checkpoint_when_null() {
        // The 2-arg constructor sets rootCallCheckpoint to null — no NPE expected
        listener.beforeAgenticScopeDestroyed(mock(AgenticScope.class));
        // If we get here without exception, pass
    }
}
