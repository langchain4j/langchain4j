package dev.langchain4j.agentic.observability;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.AgenticServices.AgenticScopeFunction;
import dev.langchain4j.agentic.UntypedAgent;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * A non-AI agent inherits the listener of its parent when the parent is set, like AI and composed agents do: a
 * non-AI agent that is a direct sub-agent of the monitored root used to be missing from the monitor, while one
 * nested under a composed sub-agent received the monitor through that sub-agent.
 */
class NonAiAgentListenerInheritanceTest {

    @Test
    void non_ai_agent_directly_under_the_monitored_root_is_recorded() {
        AgentMonitor monitor = new AgentMonitor();
        AgenticScopeFunction<String> fetch = AgenticServices.nonAiAgentBuilder(scope -> "fetched")
                .name("fetch")
                .outputKey("data")
                .build();
        UntypedAgent root = AgenticServices.sequenceBuilder()
                .subAgents(fetch)
                .outputKey("data")
                .listener(monitor)
                .build();

        root.invoke(Map.of());

        assertThat(monitor.successfulExecutions()).hasSize(1);
        List<AgentInvocation> nested =
                monitor.successfulExecutions().get(0).topLevelInvocations().nestedInvocations();
        assertThat(nested).extracting(invocation -> invocation.agent().name()).containsExactly("fetch");
    }

    @Test
    void non_ai_agent_under_a_nested_sequence_is_recorded() {
        AgentMonitor monitor = new AgentMonitor();
        AgenticScopeFunction<String> fetch = AgenticServices.nonAiAgentBuilder(scope -> "fetched")
                .name("fetch")
                .outputKey("data")
                .build();
        UntypedAgent inner = AgenticServices.sequenceBuilder()
                .subAgents(fetch)
                .outputKey("data")
                .build();
        UntypedAgent root = AgenticServices.sequenceBuilder()
                .subAgents(inner)
                .outputKey("data")
                .listener(monitor)
                .build();

        root.invoke(Map.of());

        List<AgentInvocation> innerInvocations = monitor.successfulExecutions()
                .get(0)
                .topLevelInvocations()
                .nestedInvocations()
                .get(0)
                .nestedInvocations();
        assertThat(innerInvocations)
                .extracting(invocation -> invocation.agent().name())
                .containsExactly("fetch");
    }
}
