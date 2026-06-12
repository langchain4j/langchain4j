package dev.langchain4j.agentic.patterns.htn.event;

import static dev.langchain4j.agentic.patterns.Models.baseModel;
import static dev.langchain4j.agentic.patterns.htn.TaskNode.compound;
import static dev.langchain4j.agentic.patterns.htn.TaskNode.primitive;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.observability.HtmlReportGenerator;
import dev.langchain4j.agentic.patterns.htn.HtnPlanner;
import dev.langchain4j.agentic.patterns.htn.LlmDecompositionStrategy;
import dev.langchain4j.agentic.patterns.htn.TaskNode;
import dev.langchain4j.agentic.patterns.htn.event.EventAgents.*;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class HtnEventPlanningIT {

    @Test
    void llm_driven_decomposition_for_event_planning() {
        TaskNode tree = compound("Plan Event",
                primitive(AnalyzeRequest.class),
                compound("execute-plan", new LlmDecompositionStrategy(
                        baseModel(), "eventAnalysis",
                        FindVenues.class, PlanCatering.class, ArrangeTransportation.class, DesignActivities.class)),
                primitive(CreateBudget.class),
                primitive(CompilePlan.class)
        );

        AnalyzeRequest analyzeRequest = AgenticServices.agentBuilder(AnalyzeRequest.class)
                .chatModel(baseModel())
                .outputKey("eventAnalysis")
                .name("analyzeRequest")
                .build();

        FindVenues findVenues = AgenticServices.agentBuilder(FindVenues.class)
                .chatModel(baseModel())
                .outputKey("venues")
                .name("findVenues")
                .build();

        PlanCatering planCatering = AgenticServices.agentBuilder(PlanCatering.class)
                .chatModel(baseModel())
                .outputKey("catering")
                .name("planCatering")
                .build();

        ArrangeTransportation arrangeTransportation = AgenticServices.agentBuilder(ArrangeTransportation.class)
                .chatModel(baseModel())
                .outputKey("transportation")
                .name("arrangeTransportation")
                .build();

        DesignActivities designActivities = AgenticServices.agentBuilder(DesignActivities.class)
                .chatModel(baseModel())
                .outputKey("activities")
                .name("designActivities")
                .build();

        CreateBudget createBudget = AgenticServices.agentBuilder(CreateBudget.class)
                .chatModel(baseModel())
                .outputKey("budget")
                .name("createBudget")
                .build();

        CompilePlan compilePlan = AgenticServices.agentBuilder(CompilePlan.class)
                .chatModel(baseModel())
                .outputKey("eventPlan")
                .name("compilePlan")
                .build();

        EventPlanner planner = AgenticServices.plannerBuilder(EventPlanner.class)
                .subAgents(analyzeRequest, findVenues, planCatering,
                        arrangeTransportation, designActivities, createBudget, compilePlan)
                .outputKey("eventPlan")
                .planner(() -> new HtnPlanner(tree))
                .build();

        ResultWithAgenticScope<String> result = planner.plan(
                "Plan a corporate team-building retreat for 50 people in a mountain resort. " +
                "The event should include outdoor activities, team exercises, and a gala dinner. " +
                "We need transportation from the city center.");

        assertThat(result.agenticScope().readState("eventAnalysis", "")).isNotBlank();
        assertThat(result.agenticScope().readState("venues", "")).isNotBlank();
        assertThat(result.agenticScope().readState("budget", "")).isNotBlank();
        assertThat(result.result()).isNotBlank();

        long specialistOutputs = List.of("venues", "catering", "transportation", "activities").stream()
                .filter(key -> !result.agenticScope().readState(key, "").isBlank())
                .count();
        assertThat(specialistOutputs).isGreaterThanOrEqualTo(2);

//        System.out.println(result.result());
//        HtmlReportGenerator.generateReport(planner.agentMonitor(),
//        Path.of("src", "test", "resources", "htn-planner-report.html"));
    }
}
