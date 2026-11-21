package dev.langchain4j.agentic.observability;

import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.tool.ToolExecution;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public class AgentInvocation {

    private final List<AgentInvocation> nestedInvocations = Collections.synchronizedList(new ArrayList<>());
    private final List<ToolExecution> toolExecutions = Collections.synchronizedList(new ArrayList<>());

    private final AgentRequest agentRequest;
    private final Instant startInstant;

    private AgentResponse agentResponse;
    private Instant finishInstant;
    private int iterationIndex = -1;
    private String skipReason;

    private Duration suspendedTime = Duration.ZERO;
    private volatile Instant suspendedAt;

    private boolean plannerModelCalled;
    private @Nullable TokenUsage plannerTokenUsage;
    private @Nullable String plannerModelName;

    AgentInvocation(AgentRequest agentRequest) {
        this.agentRequest = agentRequest;
        this.startInstant = Instant.now();
    }

    /**
     * Creates the finished, zero-duration invocation of an agent that was skipped instead of being invoked.
     */
    static AgentInvocation skipped(AgentSkipped agentSkipped) {
        AgentInvocation invocation =
                new AgentInvocation(new AgentRequest(agentSkipped.agenticScope(), agentSkipped.agent(), Map.of()));
        invocation.agentResponse = new AgentResponse(agentSkipped.agenticScope(), agentSkipped.agent(), Map.of());
        invocation.finishInstant = invocation.startInstant;
        invocation.skipReason = agentSkipped.reason();
        return invocation;
    }

    synchronized void finished(AgentResponse agentResponse) {
        cancelSuspension();
        this.agentResponse = agentResponse;
        this.finishInstant = Instant.now();
    }

    /**
     * Marks the agentic system as suspended while this invocation is in progress. No-op if it is already suspended,
     * since the suspension is notified once per level of the agents containing the suspended step, or if it is
     * already finished.
     */
    synchronized void markSuspended() {
        if (suspendedAt == null && finishInstant == null) {
            suspendedAt = Instant.now();
        }
    }

    /**
     * Ends the current suspension, if any, adding it to the suspended time: the agent is invoked again to resume the
     * agentic system, or the execution ended without invoking it again.
     */
    synchronized void markResumed() {
        Instant start = suspendedAt;
        if (start != null) {
            suspendedTime = suspendedTime.plus(Duration.between(start, Instant.now()));
            suspendedAt = null;
        }
    }

    /**
     * Forgets the current suspension, if any, of an invocation ending without its agent being invoked again: the
     * agent was still running when the agentic system was suspended, like a parallel sibling of the suspended step,
     * so it was never waiting.
     */
    synchronized void cancelSuspension() {
        suspendedAt = null;
    }

    /**
     * Records one model call made by the planner of the agent during this invocation: a planner can make several,
     * and the invocation reports the token usage of all of them, and the model name only when all the calls agree on
     * it. An invocation suspended and then continued keeps the calls made before the suspension.
     */
    synchronized void addPlannerModelResponse(PlannerModelResponse plannerModelResponse) {
        if (plannerModelCalled) {
            plannerTokenUsage = TokenUsage.sum(plannerTokenUsage, plannerModelResponse.tokenUsage());
            plannerModelName = Objects.equals(plannerModelName, plannerModelResponse.modelName()) ? plannerModelName : null;
        } else {
            plannerModelCalled = true;
            plannerTokenUsage = plannerModelResponse.tokenUsage();
            plannerModelName = plannerModelResponse.modelName();
        }
    }

    void addNestedInvocation(AgentInvocation agentInvocation) {
        this.nestedInvocations.add(agentInvocation);
    }

    void addToolExecution(ToolExecution toolExecution) {
        toolExecutions.add(toolExecution);
    }

    public boolean done() {
        return finishInstant != null;
    }

    /**
     * Returns {@code true} while the agentic system is suspended, for instance waiting for a human, with this
     * invocation in progress: the root agent and the workflows containing the suspended step stay in progress until
     * the system is resumed by invoking the root agent again, when they continue this same invocation. An agent still
     * running in another branch of a parallel workflow also returns {@code true}, until it completes.
     */
    public boolean suspended() {
        return suspendedAt != null;
    }

    /**
     * Returns the total time the agentic system spent suspended during this invocation, counting an ongoing
     * suspension until now, or zero if it never was: a loop whose step waits for a human adds up one wait per
     * iteration. Once the invocation is finished, its {@link #duration()} includes this time.
     */
    public synchronized Duration suspendedDuration() {
        Instant start = suspendedAt;
        return start == null ? suspendedTime : suspendedTime.plus(Duration.between(start, Instant.now()));
    }

    /**
     * Returns {@code true} if the agent was skipped instead of being invoked, because its activation condition was
     * false or because it is optional and one of its arguments was missing.
     */
    public boolean skipped() {
        return skipReason != null;
    }

    /**
     * Returns why the agent was skipped, or {@code null} if it was invoked.
     */
    public String skipReason() {
        return skipReason;
    }

    public LocalDateTime startTime() {
        return LocalDateTime.ofInstant(startInstant, ZoneId.systemDefault());
    }

    public LocalDateTime finishTime() {
        return finishInstant == null ? null : LocalDateTime.ofInstant(finishInstant, ZoneId.systemDefault());
    }

    /**
     * Returns how long the invocation took, measured between two instants: a daylight saving time change in the
     * meantime does not affect it, unlike the difference between {@link #startTime()} and {@link #finishTime()}.
     */
    public Duration duration() {
        if (!done()) {
            throw new IllegalStateException("Agent call is not finished yet");
        }
        return Duration.between(startInstant, finishInstant);
    }

    public AgentInstance agent() {
        return agentRequest.agent();
    }

    public AgenticScope agenticScope() {
        return agentRequest.agenticScope();
    }

    public Map<String, Object> inputs() {
        return agentRequest.inputs();
    }

    public Object output() {
        if (!done()) {
            throw new IllegalStateException("Agent call is not finished yet");
        }
        return agentResponse.output();
    }

    /**
     * Returns the total number of tokens of {@link #tokenUsage()}, or zero if it is empty. For an agent whose planner
     * calls a model itself, like a supervisor, it counts only those calls, and not the tokens of its subagents.
     */
    public int totalTokenCount() {
        return tokenUsage().map(TokenUsage::totalTokenCount).orElse(0);
    }

    /**
     * Returns the token usage of an AI agent, which is the total of all its LLM calls when it calls tools, or of the
     * decision call of a decision agent. For an agent whose planner calls a model itself, like a supervisor or a
     * decision router, it is the total of those calls, without the token usage of its subagents, which is reported
     * by their own invocations. It is empty if the agent called no model, like a workflow or a non-AI agent, if it
     * was skipped, or if the provider didn't report the token usage.
     * <p>
     * This method can be called at any time. For an agent whose planner calls a model itself it returns what those
     * calls consumed so far, also while the invocation is in progress or suspended, and after it failed. The token
     * usage of an AI agent or of a decision agent is only known when its invocation completes, so it is empty until
     * then, and it stays empty when the invocation fails.
     */
    public Optional<TokenUsage> tokenUsage() {
        synchronized (this) {
            if (plannerModelCalled) {
                return Optional.ofNullable(plannerTokenUsage);
            }
        }
        if (!done()) {
            return Optional.empty();
        }
        Optional<TokenUsage> chatTokenUsage = Optional.ofNullable(agentResponse.chatResponse())
                .map(ChatResponse::metadata)
                .map(ChatResponseMetadata::tokenUsage);
        return chatTokenUsage.isPresent() ? chatTokenUsage : decisionResponse().map(DecisionResponse::tokenUsage);
    }

    /**
     * Returns the name of the model that answered the LLM calls of an AI agent, or the decision call of a
     * decision agent, as reported by the provider in the response metadata. It is empty if the agent called no
     * model, like a workflow or a non-AI agent, if it was skipped, if the provider didn't report the model name, or
     * if the LLM calls of an AI agent (for example, the calls of a tool loop) were answered by different models,
     * since {@link #tokenUsage()} is the total of all those calls. In the same way, for an agent whose planner calls
     * a model itself, like a supervisor or a decision router, it is the name of the model that answered all those
     * calls, and it is empty if they were answered by different models.
     * <p>
     * Like {@link #tokenUsage()}, this method can be called at any time: it tells the model of the calls made so far
     * by the planner of the agent, and it is empty for an AI agent or a decision agent whose invocation didn't
     * complete.
     */
    public Optional<String> modelName() {
        synchronized (this) {
            if (plannerModelCalled) {
                return Optional.ofNullable(plannerModelName);
            }
        }
        if (!done()) {
            return Optional.empty();
        }
        Optional<String> chatModelName = Optional.ofNullable(agentResponse.chatResponse())
                .map(ChatResponse::metadata)
                .map(ChatResponseMetadata::modelName);
        return chatModelName.isPresent() ? chatModelName : decisionResponse().map(DecisionResponse::modelName);
    }

    /**
     * Returns the response of the decision model of a decision agent, with its answers and their probabilities. It
     * is empty for every other kind of agent, and if the agent was skipped.
     */
    public Optional<DecisionResponse> decisionResponse() {
        if (!done()) {
            throw new IllegalStateException("Agent call is not finished yet");
        }
        return Optional.ofNullable(agentResponse.decisionResponse());
    }

    /**
     * Returns the zero-based iteration index when this invocation is part of a loop, or -1 otherwise.
     */
    public int iterationIndex() {
        return iterationIndex;
    }

    void setIterationIndex(int iterationIndex) {
        this.iterationIndex = iterationIndex;
    }

    public List<AgentInvocation> nestedInvocations() {
        return nestedInvocations;
    }

    public List<ToolExecution> toolExecutions() {
        return toolExecutions;
    }

    @Override
    public String toString() {
        return toString("");
    }

    private String toString(String prefix) {
        StringBuilder sb = new StringBuilder(prefix + "AgentInvocation{" +
                "agent=" + agent().name() +
                (iterationIndex >= 0 ? ", iteration=" + iterationIndex : "") +
                (skipped() ? ", skipped=" + skipReason : "") +
                ", startTime=" + startTime() +
                ", finishTime=" + finishTime() +
                ", duration=" + (done() ? duration().toMillis() + " ms" : "in progress") +
                (suspendedDuration().isZero() ? "" : ", suspended=" + suspendedDuration().toMillis() + " ms") +
                ", tokens=" + (done() || totalTokenCount() > 0 ? totalTokenCount() : "in progress") +
                ", inputs=" + shortToString(inputs()) +
                ", output=" + (done() ? shortToString(output()) : "in progress") +
                '}');
        if (!toolExecutions.isEmpty()) {
            String toolPrefix = prefix.isEmpty() ? "|-> " : "    " + prefix;
            for (ToolExecution toolExec : toolExecutions) {
                sb.append("\n").append(toolPrefix).append(toolExec);
            }
        }
        if (!nestedInvocations.isEmpty()) {
            String nestedPrefix = prefix.isEmpty() ? "|=> " : "    " + prefix;
            for (AgentInvocation nestedCall : nestedInvocations) {
                sb.append("\n").append(nestedCall.toString(nestedPrefix));
            }
        }
        return sb.toString();
    }

    private String shortToString(Object o) {
        if (o == null) {
            return "null";
        }
        String s = o.toString();
        return s.substring(0, Math.min(s.length(), 15)) + (s.length() > 15 ? "..." : "");
    }

    private String shortToString(Map<?, ?> map) {
        if (map.isEmpty()) {
            return "{}";
        }

        boolean first = true;
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        for (Map.Entry<?,?> e : map.entrySet()) {
            if (first) {
                first = false;
            } else {
                sb.append(',').append(' ');
            }
            Object key = e.getKey();
            Object value = e.getValue();
            sb.append(key == this ? "(this Map)" : key);
            sb.append('=');
            sb.append(value == this ? "(this Map)" : shortToString(value));
        }
        return sb.append('}').toString();
    }
}
