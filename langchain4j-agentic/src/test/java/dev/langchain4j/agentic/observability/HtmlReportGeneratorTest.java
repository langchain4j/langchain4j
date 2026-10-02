package dev.langchain4j.agentic.observability;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.planner.Action;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.planner.InitPlanningContext;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.planner.PlanningContext;
import dev.langchain4j.service.V;
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
}
