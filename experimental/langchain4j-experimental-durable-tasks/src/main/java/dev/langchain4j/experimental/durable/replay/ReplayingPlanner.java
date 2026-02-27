package dev.langchain4j.experimental.durable.replay;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.Experimental;
import dev.langchain4j.agentic.planner.Action;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.planner.InitPlanningContext;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.planner.PlanningContext;
import dev.langchain4j.agentic.scope.AgentInvocation;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.experimental.durable.store.event.AgentInvocationCompletedEvent;
import dev.langchain4j.experimental.durable.store.event.TaskEvent;
import dev.langchain4j.experimental.durable.store.event.TaskResumedEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A planner that replays previously completed agent invocations during task resume,
 * then delegates to the original planner for live execution.
 *
 * <p>On resume, the application recreates the agent topology and wraps the planner
 * with a {@code ReplayingPlanner}. During {@link #firstAction(PlanningContext)},
 * this planner:
 * <ol>
 *   <li>Writes each replayed agent output back into the scope</li>
 *   <li>Advances the delegate planner's cursor by calling its methods</li>
 *   <li>Transitions to live execution once all replayed steps are consumed</li>
 * </ol>
 *
 * <p>This avoids re-executing already-completed agent invocations while keeping
 * the delegate planner's internal state (e.g., cursor positions) synchronized.
 *
 * @see Planner
 */
@Experimental
public class ReplayingPlanner implements Planner {

    private static final Logger LOG = LoggerFactory.getLogger(ReplayingPlanner.class);

    private final Planner delegate;
    private final List<AgentInvocationCompletedEvent> completedEvents;
    private final List<TaskResumedEvent> resumedEvents;
    private final ObjectMapper objectMapper;

    private List<AgentInstance> subagents;
    private int replayedCount;
    private boolean replayComplete;

    private ReplayingPlanner(Builder builder) {
        this.delegate = builder.delegate;
        this.completedEvents = extractCompletedEvents(builder.journalEvents);
        this.resumedEvents = extractResumedEvents(builder.journalEvents);
        this.objectMapper = dev.langchain4j.experimental.durable.internal.ObjectMapperFactory.create();
        this.replayedCount = 0;
        this.replayComplete = completedEvents.isEmpty() && resumedEvents.isEmpty();
    }

    @Override
    public void init(InitPlanningContext initPlanningContext) {
        delegate.init(initPlanningContext);
        this.subagents = initPlanningContext.subagents();
    }

    /**
     * On the first action, replays all completed invocations by writing their outputs
     * into the scope and advancing the delegate planner. Returns the first live action
     * that requires actual agent execution.
     */
    @Override
    public Action firstAction(PlanningContext planningContext) {
        if (replayComplete) {
            return delegate.firstAction(planningContext);
        }

        LOG.info("Replaying {} completed invocations and {} resume events from journal", completedEvents.size(), resumedEvents.size());

        AgenticScope scope = planningContext.agenticScope();

        // Write any user input from resume events into the scope
        for (TaskResumedEvent event : resumedEvents) {
            if (event.userInput() != null) {
                for (Map.Entry<String, Object> entry : event.userInput().entrySet()) {
                    scope.writeState(entry.getKey(), entry.getValue());
                    LOG.debug("Wrote user input to scope key '{}'", entry.getKey());
                }
            }
        }

        // Get the delegate's first action to initialize its state
        Action currentAction = delegate.firstAction(planningContext);

        // Replay each completed invocation
        while (replayedCount < completedEvents.size()) {
            AgentInvocationCompletedEvent event = completedEvents.get(replayedCount);
            Object output = deserializeOutput(event.serializedOutput());

            // Write the replayed output to scope using the agent's output key
            writeOutputToScope(scope, event, output);

            // Create a synthetic invocation so the delegate can advance its state
            AgentInvocation syntheticInvocation =
                    new AgentInvocation(null, event.agentName(), event.agentId(), Map.of(), output);

            replayedCount++;

            // Advance the delegate to its next action
            currentAction = delegate.nextAction(new PlanningContext(scope, syntheticInvocation));
            LOG.debug("Replayed invocation {}/{}: agent={}", replayedCount, completedEvents.size(), event.agentName());

            if (currentAction.isDone()) {
                LOG.info("Delegate planner terminated after {} replayed invocations", replayedCount);
                break;
            }
        }

        replayComplete = true;
        LOG.info("Replay complete. {} invocations replayed. Switching to live execution.", replayedCount);
        return currentAction;
    }

    /**
     * After replay is complete, all calls delegate directly.
     */
    @Override
    public Action nextAction(PlanningContext planningContext) {
        return delegate.nextAction(planningContext);
    }

    @Override
    public AgenticSystemTopology topology() {
        return delegate.topology();
    }

    @Override
    public boolean terminated() {
        return delegate.terminated();
    }

    /**
     * Returns the number of invocations that were replayed from the journal.
     *
     * @return the replayed invocation count
     */
    public int replayedCount() {
        return replayedCount;
    }

    /**
     * Returns whether replay has completed and the planner is in live mode.
     *
     * @return {@code true} if replay is complete
     */
    public boolean isReplayComplete() {
        return replayComplete;
    }

    private void writeOutputToScope(AgenticScope scope, AgentInvocationCompletedEvent event, Object output) {
        if (output == null) {
            return;
        }
        // Find the matching agent instance to determine its output key
        String outputKey = findOutputKey(event.agentId(), event.agentName());
        if (outputKey != null) {
            scope.writeState(outputKey, output);
            LOG.debug("Wrote replayed output to scope key '{}' for agent '{}'", outputKey, event.agentName());
        } else {
            LOG.debug(
                    "No output key found for agent '{}' (id={}), skipping scope write",
                    event.agentName(),
                    event.agentId());
        }
    }

    private String findOutputKey(String agentId, String agentName) {
        if (subagents == null) {
            return null;
        }
        for (AgentInstance agent : subagents) {
            if (agentId != null && agentId.equals(agent.agentId())) {
                return agent.outputKey();
            }
            if (agentName != null && agentName.equals(agent.name())) {
                return agent.outputKey();
            }
        }
        return null;
    }

    private Object deserializeOutput(String serializedOutput) {
        if (serializedOutput == null) {
            return null;
        }
        try {
            return objectMapper.readValue(serializedOutput, Object.class);
        } catch (JsonProcessingException e) {
            LOG.warn("Failed to deserialize replayed output, returning raw string: {}", e.getMessage());
            return serializedOutput;
        }
    }

    private static List<AgentInvocationCompletedEvent> extractCompletedEvents(List<TaskEvent> events) {
        List<AgentInvocationCompletedEvent> completed = new ArrayList<>();
        for (TaskEvent event : events) {
            if (event instanceof AgentInvocationCompletedEvent completedEvent) {
                completed.add(completedEvent);
            }
        }
        return List.copyOf(completed);
    }

    private static List<TaskResumedEvent> extractResumedEvents(List<TaskEvent> events) {
        List<TaskResumedEvent> resumed = new ArrayList<>();
        for (TaskEvent event : events) {
            if (event instanceof TaskResumedEvent resumedEvent) {
                resumed.add(resumedEvent);
            }
        }
        return List.copyOf(resumed);
    }

    /**
     * Creates a new builder for {@link ReplayingPlanner}.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link ReplayingPlanner}.
     */
    public static final class Builder {

        private Planner delegate;
        private List<TaskEvent> journalEvents = List.of();

        private Builder() {}

        /**
         * Sets the delegate planner to wrap. Required.
         *
         * @param delegate the original planner
         * @return this builder
         */
        public Builder delegate(Planner delegate) {
            this.delegate = delegate;
            return this;
        }

        /**
         * Sets the journal events from which to extract completed invocations. Required.
         *
         * @param journalEvents the journal events
         * @return this builder
         */
        public Builder journalEvents(List<TaskEvent> journalEvents) {
            this.journalEvents = journalEvents;
            return this;
        }

        /**
         * Builds the {@link ReplayingPlanner}.
         *
         * @return a new replaying planner
         * @throws IllegalArgumentException if delegate is null
         */
        public ReplayingPlanner build() {
            if (delegate == null) {
                throw new IllegalArgumentException("delegate planner must not be null");
            }
            return new ReplayingPlanner(this);
        }
    }
}
