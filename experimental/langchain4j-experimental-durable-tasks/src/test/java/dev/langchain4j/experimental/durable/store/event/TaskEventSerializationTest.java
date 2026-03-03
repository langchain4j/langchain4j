package dev.langchain4j.experimental.durable.store.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.langchain4j.experimental.durable.task.TaskId;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TaskEventSerializationTest {

    private ObjectMapper objectMapper;
    private TaskId taskId;
    private Instant now;

    @BeforeEach
    void setup() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        taskId = new TaskId("test-task-1");
        now = Instant.now();
    }

    @Test
    void should_round_trip_task_started_event() throws Exception {
        TaskEvent event = new TaskStartedEvent(taskId, now, Map.of("input", "hello"));
        String json = objectMapper.writeValueAsString(event);
        TaskEvent deserialized = objectMapper.readValue(json, TaskEvent.class);

        assertThat(deserialized).isInstanceOf(TaskStartedEvent.class);
        TaskStartedEvent typed = (TaskStartedEvent) deserialized;
        assertThat(typed.taskId()).isEqualTo(taskId);
        assertThat(typed.initialInputs()).containsEntry("input", "hello");
    }

    @Test
    void should_round_trip_agent_invocation_started_event() throws Exception {
        TaskEvent event =
                new AgentInvocationStartedEvent(taskId, now, "summarizer", "sum-1", Map.of("text", "content"));
        String json = objectMapper.writeValueAsString(event);
        TaskEvent deserialized = objectMapper.readValue(json, TaskEvent.class);

        assertThat(deserialized).isInstanceOf(AgentInvocationStartedEvent.class);
        AgentInvocationStartedEvent typed = (AgentInvocationStartedEvent) deserialized;
        assertThat(typed.agentName()).isEqualTo("summarizer");
        assertThat(typed.agentId()).isEqualTo("sum-1");
    }

    @Test
    void should_round_trip_agent_invocation_completed_event() throws Exception {
        TaskEvent event = new AgentInvocationCompletedEvent(taskId, now, "summarizer", "sum-1", "\"summary text\"");
        String json = objectMapper.writeValueAsString(event);
        TaskEvent deserialized = objectMapper.readValue(json, TaskEvent.class);

        assertThat(deserialized).isInstanceOf(AgentInvocationCompletedEvent.class);
        AgentInvocationCompletedEvent typed = (AgentInvocationCompletedEvent) deserialized;
        assertThat(typed.serializedOutput()).isEqualTo("\"summary text\"");
    }

    @Test
    void should_round_trip_agent_invocation_failed_event() throws Exception {
        TaskEvent event =
                new AgentInvocationFailedEvent(taskId, now, "agent1", "a1", "NullPointerException", "stack trace here");
        String json = objectMapper.writeValueAsString(event);
        TaskEvent deserialized = objectMapper.readValue(json, TaskEvent.class);

        assertThat(deserialized).isInstanceOf(AgentInvocationFailedEvent.class);
        AgentInvocationFailedEvent typed = (AgentInvocationFailedEvent) deserialized;
        assertThat(typed.errorMessage()).isEqualTo("NullPointerException");
    }

    @Test
    void should_round_trip_task_paused_event() throws Exception {
        TaskEvent event = new TaskPausedEvent(taskId, now, "Waiting for approval", "approvalKey");
        String json = objectMapper.writeValueAsString(event);
        TaskEvent deserialized = objectMapper.readValue(json, TaskEvent.class);

        assertThat(deserialized).isInstanceOf(TaskPausedEvent.class);
        TaskPausedEvent typed = (TaskPausedEvent) deserialized;
        assertThat(typed.reason()).isEqualTo("Waiting for approval");
        assertThat(typed.pendingAgentName()).isEqualTo("approvalKey");
    }

    @Test
    void should_round_trip_task_resumed_event() throws Exception {
        TaskEvent event = new TaskResumedEvent(taskId, now, Map.of("approval", "yes"));
        String json = objectMapper.writeValueAsString(event);
        TaskEvent deserialized = objectMapper.readValue(json, TaskEvent.class);

        assertThat(deserialized).isInstanceOf(TaskResumedEvent.class);
        TaskResumedEvent typed = (TaskResumedEvent) deserialized;
        assertThat(typed.userInput()).containsEntry("approval", "yes");
    }

    @Test
    void should_round_trip_task_completed_event() throws Exception {
        TaskEvent event = new TaskCompletedEvent(taskId, now, "\"final result\"");
        String json = objectMapper.writeValueAsString(event);
        TaskEvent deserialized = objectMapper.readValue(json, TaskEvent.class);

        assertThat(deserialized).isInstanceOf(TaskCompletedEvent.class);
        TaskCompletedEvent typed = (TaskCompletedEvent) deserialized;
        assertThat(typed.serializedResult()).isEqualTo("\"final result\"");
    }

    @Test
    void should_round_trip_task_failed_event() throws Exception {
        TaskEvent event = new TaskFailedEvent(taskId, now, "Out of memory", "java.lang.OOM...");
        String json = objectMapper.writeValueAsString(event);
        TaskEvent deserialized = objectMapper.readValue(json, TaskEvent.class);

        assertThat(deserialized).isInstanceOf(TaskFailedEvent.class);
        TaskFailedEvent typed = (TaskFailedEvent) deserialized;
        assertThat(typed.errorMessage()).isEqualTo("Out of memory");
    }

    @Test
    void should_round_trip_task_cancelled_event() throws Exception {
        TaskEvent event = new TaskCancelledEvent(taskId, now);
        String json = objectMapper.writeValueAsString(event);
        TaskEvent deserialized = objectMapper.readValue(json, TaskEvent.class);

        assertThat(deserialized).isInstanceOf(TaskCancelledEvent.class);
        TaskCancelledEvent typed = (TaskCancelledEvent) deserialized;
        assertThat(typed.taskId()).isEqualTo(taskId);
    }

    @Test
    void should_include_type_discriminator_in_json() throws Exception {
        TaskEvent event = new TaskStartedEvent(taskId, now, Map.of());
        String json = objectMapper.writeValueAsString(event);

        assertThat(json).contains("\"type\":");
        assertThat(json).contains("\"task_started\"");
    }

    @Test
    void should_have_default_schema_version() {
        TaskEvent event = new TaskStartedEvent(taskId, now, Map.of());
        assertThat(event.schemaVersion()).isEqualTo(1);
    }

    @Test
    void should_round_trip_task_retry_event() throws Exception {
        TaskEvent event = new TaskRetryEvent(taskId, now, 2, 5, "Connection refused", 4000L);
        String json = objectMapper.writeValueAsString(event);
        TaskEvent deserialized = objectMapper.readValue(json, TaskEvent.class);

        assertThat(deserialized).isInstanceOf(TaskRetryEvent.class);
        TaskRetryEvent typed = (TaskRetryEvent) deserialized;
        assertThat(typed.taskId()).isEqualTo(taskId);
        assertThat(typed.attempt()).isEqualTo(2);
        assertThat(typed.maxRetries()).isEqualTo(5);
        assertThat(typed.errorMessage()).isEqualTo("Connection refused");
        assertThat(typed.delayMillis()).isEqualTo(4000L);
    }
}
