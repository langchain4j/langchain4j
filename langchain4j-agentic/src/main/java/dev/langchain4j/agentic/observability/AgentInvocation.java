package dev.langchain4j.agentic.observability;

import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.tool.ToolExecution;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class AgentInvocation {

    private final List<AgentInvocation> nestedInvocations = Collections.synchronizedList(new ArrayList<>());
    private final List<ToolExecution> toolExecutions = Collections.synchronizedList(new ArrayList<>());

    private final AgentRequest agentRequest;
    private final LocalDateTime startTime;

    private AgentResponse agentResponse;
    private LocalDateTime finishTime;
    private int iterationIndex = -1;
    private String skipReason;

    private Duration suspendedTime = Duration.ZERO;
    private volatile LocalDateTime suspendedAt;

    AgentInvocation(AgentRequest agentRequest) {
        this.agentRequest = agentRequest;
        this.startTime = LocalDateTime.now();
    }

    /**
     * Creates the finished, zero-duration invocation of an agent that was skipped instead of being invoked.
     */
    static AgentInvocation skipped(AgentSkipped agentSkipped) {
        AgentInvocation invocation =
                new AgentInvocation(new AgentRequest(agentSkipped.agenticScope(), agentSkipped.agent(), Map.of()));
        invocation.agentResponse = new AgentResponse(agentSkipped.agenticScope(), agentSkipped.agent(), Map.of());
        invocation.finishTime = invocation.startTime;
        invocation.skipReason = agentSkipped.reason();
        return invocation;
    }

    synchronized void finished(AgentResponse agentResponse) {
        cancelSuspension();
        this.agentResponse = agentResponse;
        this.finishTime = LocalDateTime.now();
    }

    /**
     * Marks the agentic system as suspended while this invocation is in progress. No-op if it is already suspended,
     * since the suspension is notified once per level of the agents containing the suspended step, or if it is
     * already finished.
     */
    synchronized void markSuspended() {
        if (suspendedAt == null && finishTime == null) {
            suspendedAt = LocalDateTime.now();
        }
    }

    /**
     * Ends the current suspension, if any, adding it to the suspended time: the agent is invoked again to resume the
     * agentic system, or the execution ended without invoking it again.
     */
    synchronized void markResumed() {
        LocalDateTime start = suspendedAt;
        if (start != null) {
            suspendedTime = suspendedTime.plus(Duration.between(start, LocalDateTime.now()));
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

    void addNestedInvocation(AgentInvocation agentInvocation) {
        this.nestedInvocations.add(agentInvocation);
    }

    void addToolExecution(ToolExecution toolExecution) {
        toolExecutions.add(toolExecution);
    }

    public boolean done() {
        return finishTime != null;
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
        LocalDateTime start = suspendedAt;
        return start == null ? suspendedTime : suspendedTime.plus(Duration.between(start, LocalDateTime.now()));
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
        return startTime;
    }

    public LocalDateTime finishTime() {
        return finishTime;
    }

    public Duration duration() {
        if (!done()) {
            throw new IllegalStateException("Agent call is not finished yet");
        }
        return Duration.between(startTime, finishTime);
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

    public int totalTokenCount() {
        return tokenUsage().map(TokenUsage::totalTokenCount).orElse(0);
    }

    /**
     * Returns the token usage of the last LLM call of an AI agent, or of the decision call of a decision agent. It is
     * empty if the agent called no model, like a workflow or a non-AI agent, if it was skipped, or if the provider
     * didn't report the token usage.
     */
    public Optional<TokenUsage> tokenUsage() {
        if (!done()) {
            throw new IllegalStateException("Agent call is not finished yet");
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
     * since {@link #tokenUsage()} is the total of all those calls.
     */
    public Optional<String> modelName() {
        if (!done()) {
            throw new IllegalStateException("Agent call is not finished yet");
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
                ", startTime=" + startTime +
                ", finishTime=" + finishTime +
                ", duration=" + (done() ? duration().toMillis() + " ms" : "in progress") +
                (suspendedDuration().isZero() ? "" : ", suspended=" + suspendedDuration().toMillis() + " ms") +
                ", tokens=" + (done() ? totalTokenCount() : "in progress") +
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
