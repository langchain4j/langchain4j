package dev.langchain4j.agentic.observability;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.service.tool.ToolExecution;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Represents a monitored tool execution within an agent invocation,
 * tracking the request, result, and timing information.
 */
public class MonitoredToolExecution {

    private final ToolExecutionRequest request;
    private final LocalDateTime startTime;

    private ToolExecution toolExecution;
    private LocalDateTime finishTime;

    MonitoredToolExecution(ToolExecutionRequest request) {
        this.request = request;
        this.startTime = LocalDateTime.now();
    }

    void finished(ToolExecution toolExecution) {
        this.toolExecution = toolExecution;
        this.finishTime = LocalDateTime.now();
    }

    public ToolExecutionRequest request() {
        return request;
    }

    public String toolName() {
        return request.name();
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
            throw new IllegalStateException("Tool execution is not finished yet");
        }
        return Duration.between(startTime, finishTime);
    }

    public ToolExecution toolExecution() {
        return toolExecution;
    }

    public boolean hasFailed() {
        return done() && toolExecution.hasFailed();
    }

    @Override
    public String toString() {
        return "ToolExecution{" +
                "tool=" + request.name() +
                ", startTime=" + startTime +
                ", finishTime=" + finishTime +
                ", duration=" + (done() ? duration().toMillis() + " ms" : "in progress") +
                '}';
    }
}
