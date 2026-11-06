package dev.langchain4j.agentic.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.scope.AgenticScope;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentMonitorResumeTest {

    @Test
    void resumed_root_invocation_continues_the_same_execution() {
        AgentMonitor monitor = new AgentMonitor();
        AgentInstance root = mock(AgentInstance.class);
        when(root.agentId()).thenReturn("root");
        when(root.name()).thenReturn("root");
        AgenticScope scope = mock(AgenticScope.class);
        when(scope.memoryId()).thenReturn("session-1");
        AgentRequest request = new AgentRequest(scope, root, Map.of());

        monitor.beforeAgentInvocation(request);
        MonitoredExecution execution = monitor.ongoingExecutionFor(scope);

        monitor.beforeAgentInvocation(request);

        assertThat(monitor.ongoingExecutionFor(scope)).isSameAs(execution);
        assertThat(execution.topLevelInvocations().nestedInvocations()).isEmpty();

        monitor.afterAgentInvocation(new AgentResponse(scope, root, Map.of(), "done"));
        assertThat(monitor.ongoingExecutions()).isEmpty();
        assertThat(monitor.successfulExecutionsFor(scope)).containsExactly(execution);
    }
}
