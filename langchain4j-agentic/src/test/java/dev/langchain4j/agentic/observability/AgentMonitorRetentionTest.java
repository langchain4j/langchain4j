package dev.langchain4j.agentic.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.scope.AgenticScope;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentMonitorRetentionTest {

    private static AgentMonitor monitorWithMax(int max) {
        AgentMonitor monitor = new AgentMonitor();
        monitor.setMaxRetainedSessions(max);
        return monitor;
    }

    @Test
    void evicts_oldest_sessions_when_max_is_exceeded() {
        int max = 5;
        AgentMonitor monitor = monitorWithMax(max);
        AgentInstance agent = stubAgent("root");

        for (int i = 0; i < 10; i++) {
            playOneRun(monitor, agent, "session-" + i);
        }

        assertThat(monitor.successfulExecutions()).hasSize(max);
        // oldest sessions should have been evicted, newest retained
        for (int i = 0; i < 5; i++) {
            assertThat(monitor.successfulExecutionsFor("session-" + i)).isEmpty();
        }
        for (int i = 5; i < 10; i++) {
            assertThat(monitor.successfulExecutionsFor("session-" + i)).hasSize(1);
        }
    }

    @Test
    void successful_and_failed_maps_are_bounded_independently() {
        int max = 2;
        AgentMonitor monitor = monitorWithMax(max);
        AgentInstance agent = stubAgent("root");

        playOneRun(monitor, agent, "ok-0");
        playOneRun(monitor, agent, "ok-1");
        playOneFailedRun(monitor, agent, "fail-0");
        playOneFailedRun(monitor, agent, "fail-1");
        // one more successful evicts oldest successful (ok-0), not any failed
        playOneRun(monitor, agent, "ok-2");

        assertThat(monitor.successfulExecutionsFor("ok-0")).isEmpty();
        assertThat(monitor.successfulExecutionsFor("ok-1")).hasSize(1);
        assertThat(monitor.successfulExecutionsFor("ok-2")).hasSize(1);
        assertThat(monitor.failedExecutionsFor("fail-0")).hasSize(1);
        assertThat(monitor.failedExecutionsFor("fail-1")).hasSize(1);
    }

    @Test
    void zero_max_retains_nothing() {
        AgentMonitor monitor = monitorWithMax(0);
        AgentInstance agent = stubAgent("root");

        playOneRun(monitor, agent, "session-0");
        playOneFailedRun(monitor, agent, "session-1");

        assertThat(monitor.successfulExecutions()).isEmpty();
        assertThat(monitor.failedExecutions()).isEmpty();
    }

    @Test
    void clear_removes_all_retained_executions() {
        AgentMonitor monitor = new AgentMonitor();
        AgentInstance agent = stubAgent("root");

        for (int i = 0; i < 10; i++) {
            playOneRun(monitor, agent, "session-" + i);
        }
        assertThat(monitor.successfulExecutions()).isNotEmpty();

        monitor.clear();

        assertThat(monitor.successfulExecutions()).isEmpty();
        assertThat(monitor.failedExecutions()).isEmpty();
    }

    @Test
    void clear_does_not_affect_ongoing_executions() {
        AgentMonitor monitor = new AgentMonitor();
        AgentInstance agent = stubAgent("root");
        AgenticScope scope = stubScope("ongoing");
        monitor.beforeAgentInvocation(new AgentRequest(scope, agent, Map.of()));

        monitor.clear();

        assertThat(monitor.ongoingExecutionFor("ongoing")).isNotNull();
    }

    @Test
    void new_executions_work_after_clear() {
        AgentMonitor monitor = monitorWithMax(5);
        AgentInstance agent = stubAgent("root");

        for (int i = 0; i < 5; i++) {
            playOneRun(monitor, agent, "before-" + i);
        }
        monitor.clear();

        for (int i = 0; i < 3; i++) {
            playOneRun(monitor, agent, "after-" + i);
        }
        assertThat(monitor.successfulExecutions()).hasSize(3);
    }

    @Test
    void setMaxRetainedSessions_trims_and_takes_effect_on_subsequent_additions() {
        AgentMonitor monitor = monitorWithMax(10);
        AgentInstance agent = stubAgent("root");

        for (int i = 0; i < 10; i++) {
            playOneRun(monitor, agent, "session-" + i);
        }
        assertThat(monitor.successfulExecutions()).hasSize(10);

        monitor.setMaxRetainedSessions(3);

        // existing entries beyond new max are evicted as new ones arrive
        playOneRun(monitor, agent, "session-10");
        assertThat(monitor.successfulExecutions()).hasSize(3);
    }

    @Test
    void setMaxRetainedSessions_negative_throws() {
        AgentMonitor monitor = new AgentMonitor();
        assertThatThrownBy(() -> monitor.setMaxRetainedSessions(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static void playOneRun(AgentMonitor monitor, AgentInstance agent, Object memoryId) {
        AgenticScope scope = stubScope(memoryId);
        Map<String, Object> inputs = Map.of();
        monitor.beforeAgentInvocation(new AgentRequest(scope, agent, inputs));
        monitor.afterAgentInvocation(new AgentResponse(scope, agent, inputs, "ok", null, null));
    }

    private static void playOneFailedRun(AgentMonitor monitor, AgentInstance agent, Object memoryId) {
        AgenticScope scope = stubScope(memoryId);
        Map<String, Object> inputs = Map.of();
        monitor.beforeAgentInvocation(new AgentRequest(scope, agent, inputs));
        monitor.onAgentInvocationError(new AgentInvocationError(scope, agent, inputs, new RuntimeException("boom")));
    }

    private static AgentInstance stubAgent(String agentId) {
        AgentInstance agent = mock(AgentInstance.class);
        when(agent.agentId()).thenReturn(agentId);
        when(agent.name()).thenReturn(agentId);
        return agent;
    }

    private static AgenticScope stubScope(Object memoryId) {
        AgenticScope scope = mock(AgenticScope.class);
        when(scope.memoryId()).thenReturn(memoryId);
        return scope;
    }
}
