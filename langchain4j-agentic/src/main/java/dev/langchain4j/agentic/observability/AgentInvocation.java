package dev.langchain4j.agentic.observability;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.tool.ToolExecution;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class AgentInvocation {

    private final List<AgentInvocation> nestedInvocations = Collections.synchronizedList(new ArrayList<>());
    private final List<MonitoredToolExecution> toolExecutions = Collections.synchronizedList(new ArrayList<>());

    private final AgentRequest agentRequest;
    private final LocalDateTime startTime;

    private AgentResponse agentResponse;
    private LocalDateTime finishTime;
    private int iterationIndex = -1;

    AgentInvocation(AgentRequest agentRequest) {
        this.agentRequest = agentRequest;
        this.startTime = LocalDateTime.now();
    }

    void finished(AgentResponse agentResponse) {
        this.agentResponse = agentResponse;
        this.finishTime = LocalDateTime.now();
    }

    void addNestedInvocation(AgentInvocation agentInvocation) {
        this.nestedInvocations.add(agentInvocation);
    }

    MonitoredToolExecution beforeToolExecution(ToolExecutionRequest request) {
        MonitoredToolExecution monitoredToolExecution = new MonitoredToolExecution(request);
        toolExecutions.add(monitoredToolExecution);
        return monitoredToolExecution;
    }

    void afterToolExecution(ToolExecution toolExecution) {
        ToolExecutionRequest completedRequest = toolExecution.request();
        for (int i = toolExecutions.size() - 1; i >= 0; i--) {
            MonitoredToolExecution monitored = toolExecutions.get(i);
            if (!monitored.done() && matchesRequest(monitored.request(), completedRequest)) {
                monitored.finished(toolExecution);
                return;
            }
        }
    }

    private static boolean matchesRequest(ToolExecutionRequest pending, ToolExecutionRequest completed) {
        if (pending.id() != null && completed.id() != null) {
            return pending.id().equals(completed.id());
        }
        return pending.name().equals(completed.name());
    }

    public boolean done() {
        return finishTime != null;
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

    public int tokenCount() {
        if (!done()) {
            throw new IllegalStateException("Agent call is not finished yet");
        }
        ChatResponse chatResponse = agentResponse.chatResponse();
        Integer tokenCount = chatResponse == null ? null : chatResponse.metadata().tokenUsage().totalTokenCount();
        return tokenCount == null ? 0 : tokenCount;
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

    public List<MonitoredToolExecution> toolExecutions() {
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
                ", startTime=" + startTime +
                ", finishTime=" + finishTime +
                ", duration=" + (done() ? duration().toMillis() + " ms" : "in progress") +
                ", tokens=" + (done() ? tokenCount() : "in progress") +
                ", inputs=" + shortToString(inputs()) +
                ", output=" + (done() ? shortToString(output()) : "in progress") +
                '}');
        if (!toolExecutions.isEmpty()) {
            String toolPrefix = prefix.isEmpty() ? "|-> " : "    " + prefix;
            for (MonitoredToolExecution toolExec : toolExecutions) {
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
