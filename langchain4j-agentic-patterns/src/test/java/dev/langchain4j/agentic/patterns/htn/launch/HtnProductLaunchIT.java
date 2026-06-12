package dev.langchain4j.agentic.patterns.htn.launch;

import static dev.langchain4j.agentic.patterns.Models.baseModel;
import static dev.langchain4j.agentic.patterns.htn.TaskNode.compound;
import static dev.langchain4j.agentic.patterns.htn.TaskNode.primitive;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.observability.HtmlReportGenerator;
import dev.langchain4j.agentic.patterns.htn.HtnPlanner;
import dev.langchain4j.agentic.patterns.htn.LlmDecompositionStrategy;
import dev.langchain4j.agentic.patterns.htn.TaskNode;
import dev.langchain4j.agentic.patterns.htn.launch.LaunchAgents.*;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class HtnProductLaunchIT {

    @Test
    void recursive_decomposition_for_product_launch() {
        TaskNode tree = compound("Launch Product",
                primitive(AnalyzeProduct.class),
                compound("launch-strategy", new LlmDecompositionStrategy(
                        baseModel(), "productAnalysis", 3,
                        DefineAudience.class, CraftMessaging.class, PlanDigitalMarketing.class,
                        CreatePRStrategy.class, PlanLaunchEvent.class)),
                primitive(CompileLaunchPlan.class)
        );

        AnalyzeProduct analyzeProduct = AgenticServices.agentBuilder(AnalyzeProduct.class)
                .chatModel(baseModel())
                .outputKey("productAnalysis")
                .name("analyzeProduct")
                .build();

        DefineAudience defineAudience = AgenticServices.agentBuilder(DefineAudience.class)
                .chatModel(baseModel())
                .outputKey("audience")
                .name("defineAudience")
                .build();

        CraftMessaging craftMessaging = AgenticServices.agentBuilder(CraftMessaging.class)
                .chatModel(baseModel())
                .outputKey("messaging")
                .name("craftMessaging")
                .build();

        PlanDigitalMarketing planDigitalMarketing = AgenticServices.agentBuilder(PlanDigitalMarketing.class)
                .chatModel(baseModel())
                .outputKey("digitalMarketing")
                .name("planDigitalMarketing")
                .build();

        CreatePRStrategy createPRStrategy = AgenticServices.agentBuilder(CreatePRStrategy.class)
                .chatModel(baseModel())
                .outputKey("prStrategy")
                .name("createPRStrategy")
                .build();

        PlanLaunchEvent planLaunchEvent = AgenticServices.agentBuilder(PlanLaunchEvent.class)
                .chatModel(baseModel())
                .outputKey("launchEvent")
                .name("planLaunchEvent")
                .build();

        CompileLaunchPlan compileLaunchPlan = AgenticServices.agentBuilder(CompileLaunchPlan.class)
                .chatModel(baseModel())
                .outputKey("launchPlan")
                .name("compileLaunchPlan")
                .build();

        ProductLauncher launcher = AgenticServices.plannerBuilder(ProductLauncher.class)
                .subAgents(analyzeProduct, defineAudience, craftMessaging,
                        planDigitalMarketing, createPRStrategy, planLaunchEvent, compileLaunchPlan)
                .outputKey("launchPlan")
                .planner(() -> new HtnPlanner(tree))
                .build();

        ResultWithAgenticScope<String> result = launcher.launch(
                "A developer productivity SaaS tool called 'CodeFlow' that uses AI to automate code reviews, " +
                "suggest refactorings, and generate documentation. Target: engineering teams at mid-size companies. " +
                "Pricing: $29/developer/month. Differentiator: works with any IDE and any language.");

        assertThat(result.agenticScope().readState("productAnalysis", "")).isNotBlank();
        assertThat(result.result()).isNotBlank();

        long specialistOutputs = List.of("audience", "messaging", "digitalMarketing", "prStrategy", "launchEvent").stream()
                .filter(key -> !result.agenticScope().readState(key, "").isBlank())
                .count();
        assertThat(specialistOutputs).isGreaterThanOrEqualTo(2);

//        System.out.println(result.result());
//        HtmlReportGenerator.generateReport(launcher.agentMonitor(),
//        Path.of("src", "test", "resources", "htn-launcher-report.html"));
    }
}
