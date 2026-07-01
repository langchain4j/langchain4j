package dev.langchain4j.agentic.patterns.htn.hybrid;

import static dev.langchain4j.agentic.patterns.Models.baseModel;
import static dev.langchain4j.agentic.patterns.htn.DecompositionMethod.decompose;
import static dev.langchain4j.agentic.patterns.htn.TaskNode.compound;
import static dev.langchain4j.agentic.patterns.htn.TaskNode.primitive;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.observability.HtmlReportGenerator;
import dev.langchain4j.agentic.patterns.htn.HtnPlanner;
import dev.langchain4j.agentic.patterns.htn.LlmDecompositionStrategy;
import dev.langchain4j.agentic.patterns.htn.TaskNode;
import dev.langchain4j.agentic.patterns.htn.hybrid.HybridAgents.*;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class HtnHybridIT {

    @Test
    void hybrid_domain_and_llm_decomposition() {
        TaskNode tree = compound("Design System",
                primitive(AnalyzeRequirements.class, null,
                        scope -> scope.writeState("complexity", "HIGH")),
                compound("design-phase",
                        decompose(
                                scope -> scope.readState("complexity", "").contains("HIGH"),
                                primitive(ArchitectureReview.class),
                                compound("detailed-design", new LlmDecompositionStrategy(
                                        baseModel(), "requirements",
                                        DesignDatabase.class, DesignAPI.class, DesignUI.class))),
                        decompose(
                                primitive(QuickDesign.class))),
                primitive(CreateTimeline.class)
        );

        AnalyzeRequirements analyzeRequirements = AgenticServices.agentBuilder(AnalyzeRequirements.class)
                .chatModel(baseModel())
                .outputKey("requirements")
                .name("analyzeRequirements")
                .build();

        ArchitectureReview architectureReview = AgenticServices.agentBuilder(ArchitectureReview.class)
                .chatModel(baseModel())
                .outputKey("architectureReview")
                .name("architectureReview")
                .build();

        DesignDatabase designDatabase = AgenticServices.agentBuilder(DesignDatabase.class)
                .chatModel(baseModel())
                .outputKey("databaseDesign")
                .name("designDatabase")
                .build();

        DesignAPI designAPI = AgenticServices.agentBuilder(DesignAPI.class)
                .chatModel(baseModel())
                .outputKey("apiDesign")
                .name("designAPI")
                .build();

        DesignUI designUI = AgenticServices.agentBuilder(DesignUI.class)
                .chatModel(baseModel())
                .outputKey("uiDesign")
                .name("designUI")
                .build();

        QuickDesign quickDesign = AgenticServices.agentBuilder(QuickDesign.class)
                .chatModel(baseModel())
                .outputKey("quickDesign")
                .name("quickDesign")
                .build();

        CreateTimeline createTimeline = AgenticServices.agentBuilder(CreateTimeline.class)
                .chatModel(baseModel())
                .outputKey("timeline")
                .name("createTimeline")
                .build();

        SystemDesigner designer = AgenticServices.plannerBuilder(SystemDesigner.class)
                .subAgents(analyzeRequirements, architectureReview, designDatabase,
                        designAPI, designUI, quickDesign, createTimeline)
                .outputKey("timeline")
                .planner(() -> new HtnPlanner(tree))
                .build();

        ResultWithAgenticScope<String> result = designer.design(
                "Build an e-commerce platform with user accounts, product catalog, shopping cart, " +
                "order processing, payment integration, and real-time inventory management. " +
                "Must support 10,000 concurrent users with sub-second response times.");

        assertThat(result.agenticScope().readState("requirements", "")).isNotBlank();
        assertThat(result.agenticScope().readState("architectureReview", "")).isNotBlank();
        assertThat(result.result()).isNotBlank();

        long designOutputs = List.of("databaseDesign", "apiDesign", "uiDesign").stream()
                .filter(key -> !result.agenticScope().readState(key, "").isBlank())
                .count();
        assertThat(designOutputs).isGreaterThanOrEqualTo(1);

//        System.out.println(result.result());
//        HtmlReportGenerator.generateReport(designer.agentMonitor(),
//        Path.of("src", "test", "resources", "htn-hybrid-report.html"));
    }
}
