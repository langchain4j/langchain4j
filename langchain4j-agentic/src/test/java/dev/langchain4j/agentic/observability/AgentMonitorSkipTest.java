package dev.langchain4j.agentic.observability;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.AgenticServices.AgenticScopeFunction;
import dev.langchain4j.agentic.UntypedAgent;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentMonitorSkipTest {

    private static UntypedAgent loopSkippingEvenIterations(AgentMonitor monitor) {
        AgenticScopeFunction<Integer> counter = AgenticServices.nonAiAgentBuilder(
                        scope -> scope.readState("count", 0) + 1)
                .name("counter")
                .outputKey("count")
                .build();
        AgenticScopeFunction<String> oddOnly = AgenticServices.nonAiAgentBuilder(scope -> "odd")
                .name("oddOnly")
                .outputKey("odd")
                .activationCondition("count is odd", scope -> scope.readState("count", 0) % 2 == 1)
                .build();

        UntypedAgent loop = AgenticServices.loopBuilder()
                .subAgents(counter, oddOnly)
                .maxIterations(3)
                .outputKey("count")
                .build();

        // the monitor is inherited through the loop, nested under the monitored root
        return AgenticServices.sequenceBuilder()
                .subAgents(loop)
                .outputKey("count")
                .listener(monitor)
                .build();
    }

    @Test
    void skipped_agent_is_a_finished_node_under_its_parent_with_the_iteration_index() {
        AgentMonitor monitor = new AgentMonitor();

        loopSkippingEvenIterations(monitor).invoke(Map.of());

        assertThat(monitor.ongoingExecutions()).isEmpty();
        assertThat(monitor.successfulExecutions()).hasSize(1);
        MonitoredExecution execution = monitor.successfulExecutions().get(0);
        assertThat(execution.hasError()).isFalse();

        AgentInvocation loop =
                execution.topLevelInvocations().nestedInvocations().get(0);
        assertThat(loop.agent().name()).isEqualTo("Loop");

        List<AgentInvocation> oddOnly = loop.nestedInvocations().stream()
                .filter(invocation -> invocation.agent().name().equals("oddOnly"))
                .toList();
        assertThat(oddOnly).extracting(AgentInvocation::iterationIndex).containsExactly(0, 1, 2);
        assertThat(oddOnly).extracting(AgentInvocation::skipped).containsExactly(false, true, false);
        // the counter, run in every iteration, keeps its own indexes
        assertThat(loop.nestedInvocations().stream()
                        .filter(invocation -> invocation.agent().name().equals("counter")))
                .extracting(AgentInvocation::iterationIndex)
                .containsExactly(0, 1, 2);

        AgentInvocation skipped = oddOnly.get(1);
        assertThat(skipped.done()).isTrue();
        assertThat(skipped.duration()).isEqualTo(Duration.ZERO);
        assertThat(skipped.skipReason()).isEqualTo("activation condition is false: count is odd");
        assertThat(skipped.output()).isNull();
        assertThat(skipped.nestedInvocations()).isEmpty();
        assertThat(skipped.toString()).contains("skipped=activation condition is false: count is odd");
    }

    @Test
    void report_shows_the_skipped_agent_and_the_activation_condition() {
        AgentMonitor monitor = new AgentMonitor();
        UntypedAgent root = loopSkippingEvenIterations(monitor);

        root.invoke(Map.of());
        String html = HtmlReportGenerator.generateReport(monitor);

        assertThat(html)
                .contains("<span class=\"activation-tag\" title=\"count is odd\">if: count is odd</span>")
                .contains("<tr class=\"skipped-row\"")
                .contains(
                        "<span class=\"skip-tag\" title=\"activation condition is false: count is odd\">skipped</span>");
        assertThat(HtmlReportGenerator.generateTopology(root)).contains("if: count is odd");
    }
}
