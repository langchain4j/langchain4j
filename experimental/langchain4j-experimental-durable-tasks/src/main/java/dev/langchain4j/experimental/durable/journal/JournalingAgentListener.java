package dev.langchain4j.experimental.durable.journal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.Experimental;
import dev.langchain4j.agentic.observability.AgentInvocationError;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentRequest;
import dev.langchain4j.agentic.observability.AgentResponse;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.experimental.durable.store.TaskExecutionStore;
import dev.langchain4j.experimental.durable.store.event.AgentInvocationCompletedEvent;
import dev.langchain4j.experimental.durable.store.event.AgentInvocationFailedEvent;
import dev.langchain4j.experimental.durable.store.event.AgentInvocationStartedEvent;
import dev.langchain4j.experimental.durable.task.TaskId;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.Map;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An {@link AgentListener} that journals agent invocation events to a {@link TaskExecutionStore}.
 *
 * <p>This listener records three event types:
 * <ul>
 *   <li>{@link AgentInvocationStartedEvent} — before each agent invocation</li>
 *   <li>{@link AgentInvocationCompletedEvent} — after successful completion</li>
 *   <li>{@link AgentInvocationFailedEvent} — on invocation failure</li>
 * </ul>
 *
 * <p>All event recording is best-effort: exceptions during event serialization or
 * persistence are logged at WARN level and never propagated to the running task.
 *
 * <p>This listener is always inherited by sub-agents so the full invocation tree
 * is captured in the journal.
 */
@Experimental
public class JournalingAgentListener implements AgentListener {

    private static final Logger LOG = LoggerFactory.getLogger(JournalingAgentListener.class);

    private final TaskId taskId;
    private final TaskExecutionStore store;
    private final ObjectMapper objectMapper;
    private final Consumer<AgenticScope> afterAgentCheckpoint;
    private final Consumer<AgenticScope> rootCallCheckpoint;

    public JournalingAgentListener(TaskId taskId, TaskExecutionStore store) {
        this(taskId, store, null, null);
    }

    /**
     * Creates a journaling listener with optional checkpoint callbacks.
     *
     * <p>The {@code afterAgentCheckpoint} callback is invoked after each successful agent
     * invocation with the current {@link AgenticScope}, enabling
     * {@link dev.langchain4j.experimental.durable.task.CheckpointPolicy#AFTER_EACH_AGENT}
     * to capture scope state. The {@code rootCallCheckpoint} callback is invoked when the
     * root agentic scope is about to be destroyed (i.e., the root call has completed),
     * enabling {@link dev.langchain4j.experimental.durable.task.CheckpointPolicy#AFTER_ROOT_CALL}.
     *
     * @param taskId                the task identifier
     * @param store                 the event store
     * @param afterAgentCheckpoint  if non-null, invoked after each successful agent invocation with the scope
     * @param rootCallCheckpoint    if non-null, invoked when the root agentic scope is about to be destroyed
     */
    public JournalingAgentListener(
            TaskId taskId,
            TaskExecutionStore store,
            Consumer<AgenticScope> afterAgentCheckpoint,
            Consumer<AgenticScope> rootCallCheckpoint) {
        this.taskId = taskId;
        this.store = store;
        this.afterAgentCheckpoint = afterAgentCheckpoint;
        this.rootCallCheckpoint = rootCallCheckpoint;
        this.objectMapper = dev.langchain4j.experimental.durable.internal.ObjectMapperFactory.create();
    }

    @Override
    public void beforeAgentInvocation(AgentRequest agentRequest) {
        try {
            Map<String, Object> inputs = agentRequest.inputs();
            AgentInvocationStartedEvent event = new AgentInvocationStartedEvent(
                    taskId, Instant.now(), agentRequest.agentName(), agentRequest.agentId(), inputs);
            store.appendEvent(event);
        } catch (Exception e) {
            LOG.warn("Failed to journal agent invocation start for task {}: {}", taskId, e.getMessage(), e);
        }
    }

    @Override
    public void afterAgentInvocation(AgentResponse agentResponse) {
        try {
            String serializedOutput = serializeOutput(agentResponse.output());
            AgentInvocationCompletedEvent event = new AgentInvocationCompletedEvent(
                    taskId, Instant.now(), agentResponse.agentName(), agentResponse.agentId(), serializedOutput);
            store.appendEvent(event);
        } catch (Exception e) {
            LOG.warn("Failed to journal agent invocation completion for task {}: {}", taskId, e.getMessage(), e);
        }

        if (afterAgentCheckpoint != null) {
            try {
                afterAgentCheckpoint.accept(agentResponse.agenticScope());
            } catch (Exception e) {
                LOG.warn("Checkpoint after agent invocation failed for task {}: {}", taskId, e.getMessage(), e);
            }
        }
    }

    @Override
    public void beforeAgenticScopeDestroyed(AgenticScope agenticScope) {
        if (rootCallCheckpoint != null) {
            try {
                rootCallCheckpoint.accept(agenticScope);
            } catch (Exception e) {
                LOG.warn("Root-call checkpoint failed for task {}: {}", taskId, e.getMessage(), e);
            }
        }
    }

    @Override
    public void onAgentInvocationError(AgentInvocationError invocationError) {
        try {
            Throwable error = invocationError.error();
            String stackTrace = formatStackTrace(error);
            AgentInvocationFailedEvent event = new AgentInvocationFailedEvent(
                    taskId,
                    Instant.now(),
                    invocationError.agentName(),
                    invocationError.agentId(),
                    error.getMessage(),
                    stackTrace);
            store.appendEvent(event);
        } catch (Exception e) {
            LOG.warn("Failed to journal agent invocation error for task {}: {}", taskId, e.getMessage(), e);
        }
    }

    @Override
    public boolean inheritedBySubagents() {
        return true;
    }

    /**
     * Returns the task identifier this listener is journaling for.
     *
     * @return the task id
     */
    public TaskId taskId() {
        return taskId;
    }

    private String serializeOutput(Object output) {
        if (output == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(output);
        } catch (JsonProcessingException e) {
            LOG.warn("Failed to serialize agent output, falling back to toString(): {}", e.getMessage());
            return output.toString();
        }
    }

    private static String formatStackTrace(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        StringWriter sw = new StringWriter();
        throwable.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
