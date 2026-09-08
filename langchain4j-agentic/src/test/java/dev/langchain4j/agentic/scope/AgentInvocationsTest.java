package dev.langchain4j.agentic.scope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentInvocationsTest {

    @Test
    void should_not_allow_modifying_agent_invocations() {
        DefaultAgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();
        AgentInvocation invocation = new AgentInvocation(Object.class, "agent", "agent-id", Map.of(), null);
        scope.registerAgentInvocation(invocation, null);

        List<AgentInvocation> invocations = scope.agentInvocations();

        assertThat(invocations).containsExactly(invocation);
        assertThatThrownBy(invocations::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThat(scope.agentInvocations()).containsExactly(invocation);
    }
}
