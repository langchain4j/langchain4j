package dev.langchain4j.agentic.observability;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.planner.Action;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.planner.InitPlanningContext;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.planner.PlanningContext;
import dev.langchain4j.service.V;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class HtmlReportGeneratorTest {

    public static class MedicalExpert {

        @Agent(description = "A medical expert", outputKey = "response")
        public String medical(@V("request") String request) {
            return "medical answer";
        }
    }

    public static class LegalExpert {

        @Agent(description = "A legal expert", outputKey = "response")
        public String legal(@V("request") String request) {
            return "legal answer";
        }
    }

    public static class CustomRouterPlanner implements Planner {

        private InitPlanningContext initPlanningContext;

        @Override
        public void init(InitPlanningContext initPlanningContext) {
            this.initPlanningContext = initPlanningContext;
        }

        @Override
        public Action firstAction(PlanningContext planningContext) {
            return call(initPlanningContext.subagents().get(0));
        }

        @Override
        public Action nextAction(PlanningContext planningContext) {
            return done();
        }

        @Override
        public AgenticSystemTopology topology() {
            return AgenticSystemTopology.ROUTER;
        }
    }

    @Test
    void generates_topology_of_router_built_on_custom_planner() {
        UntypedAgent router = AgenticServices.plannerBuilder()
                .subAgents(new MedicalExpert(), new LegalExpert())
                .outputKey("response")
                .planner(CustomRouterPlanner::new)
                .build();

        String html = HtmlReportGenerator.generateTopology(router);

        assertThat(html)
                .contains("<span class=\"topology-badge rtr\">Router</span>")
                .contains("<span class=\"agent-name\">medical</span>")
                .contains("<span class=\"agent-name\">legal</span>")
                .doesNotContain("when: ");
    }

    @Test
    void generates_topology_of_conditional_workflow_with_conditions() {
        UntypedAgent router = AgenticServices.conditionalBuilder()
                .subAgents("is medical", agenticScope -> true, new MedicalExpert())
                .subAgents("is legal", agenticScope -> false, new LegalExpert())
                .outputKey("response")
                .build();

        String html = HtmlReportGenerator.generateTopology(router);

        assertThat(html).contains("when: is medical").contains("when: is legal");
    }

    @Test
    void generates_execution_timeline_bars_in_a_locale_independent_way() {
        Locale defaultLocale = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("de-DE"));
        try {
            SampleReportGenerator.MockAgent root = new SampleReportGenerator.MockAgent(
                    "ask",
                    MedicalExpert.class,
                    AgenticSystemTopology.SEQUENCE,
                    "Routes the question to the appropriate domain expert",
                    List.of(),
                    "response",
                    String.class);
            SampleReportGenerator.MockAgent expert = new SampleReportGenerator.MockAgent(
                    "medical",
                    MedicalExpert.class,
                    AgenticSystemTopology.AI_AGENT,
                    "A medical expert",
                    List.of(new AgentArgument(String.class, "question")),
                    "response",
                    String.class);
            expert.parent = root;
            root.subagents = List.of(expert);

            AgentMonitor monitor = new AgentMonitor();
            monitor.setRootAgent(root);

            SampleReportGenerator.MockScope scope = new SampleReportGenerator.MockScope("user-alice");
            Map<String, Object> inputs = Map.of("question", "I broke my leg while hiking, what should I do?");

            monitor.beforeAgentInvocation(new AgentRequest(scope, root, inputs));
            monitor.beforeAgentInvocation(new AgentRequest(scope, expert, inputs));
            monitor.afterAgentInvocation(new AgentResponse(scope, expert, inputs, "Seek immediate medical attention."));
            monitor.afterAgentInvocation(new AgentResponse(scope, root, inputs, "Seek immediate medical attention."));

            String html = HtmlReportGenerator.generateReport(monitor);

            assertThat(html).contains("wf-bar");

            // the 'left'/'width' percentages are embedded into an inline CSS style, so a
            // locale-dependent decimal separator makes browsers drop the whole 'style'
            // attribute and the timeline bars disappear from the report
            assertThat(countMatches(html, "style=\"left:\\d+\\.\\d+%;width:\\d+\\.\\d+%;\""))
                    .isPositive();
            assertThat(countMatches(html, "style=\"left:\\d+,\\d+%;width:\\d+,\\d+%;\""))
                    .isZero();
        } finally {
            Locale.setDefault(defaultLocale);
        }
    }

    private static int countMatches(String html, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(html);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }
}
