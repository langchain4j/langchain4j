package dev.langchain4j.agentic.patterns.htn.coding;

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
import dev.langchain4j.agentic.patterns.htn.coding.CodingAgents.*;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class HtnCodingAgentIT {

    @Test
    void coding_agent_full_pipeline() {
        TaskNode tree = compound("Generate Code",
                primitive(AnalyzeSpecs.class, null,
                        scope -> scope.writeState("complexity", "HIGH")),
                compound("design",
                        decompose(
                                scope -> scope.readState("complexity", "").contains("HIGH"),
                                primitive(DesignArchitecture.class),
                                primitive(DefineAPIs.class),
                                primitive(DesignDataModel.class)),
                        decompose(
                                primitive(DesignArchitecture.class))),
                compound("implement", new LlmDecompositionStrategy(
                        baseModel(), "architecture", 2,
                        ImplementDataLayer.class, ImplementServiceLayer.class,
                        ImplementController.class, ImplementUI.class)),
                primitive(WriteTests.class),
                primitive(RunTests.class, null,
                        scope -> scope.writeState("testResult", "PASS")),
                compound("quality-gate",
                        decompose(
                                scope -> scope.readState("testResult", "").contains("FAIL"),
                                primitive(FixIssues.class),
                                primitive(RunTests.class)),
                        decompose(
                                primitive(ReviewCode.class),
                                primitive(GenerateDocumentation.class)))
        );

        AnalyzeSpecs analyzeSpecs = AgenticServices.agentBuilder(AnalyzeSpecs.class)
                .chatModel(baseModel())
                .outputKey("specAnalysis")
                .name("analyzeSpecs")
                .build();

        DesignArchitecture designArchitecture = AgenticServices.agentBuilder(DesignArchitecture.class)
                .chatModel(baseModel())
                .outputKey("architecture")
                .name("designArchitecture")
                .build();

        DefineAPIs defineAPIs = AgenticServices.agentBuilder(DefineAPIs.class)
                .chatModel(baseModel())
                .outputKey("apiContracts")
                .name("defineAPIs")
                .build();

        DesignDataModel designDataModel = AgenticServices.agentBuilder(DesignDataModel.class)
                .chatModel(baseModel())
                .outputKey("dataModel")
                .name("designDataModel")
                .build();

        ImplementDataLayer implementDataLayer = AgenticServices.agentBuilder(ImplementDataLayer.class)
                .chatModel(baseModel())
                .outputKey("dataLayerCode")
                .name("implementDataLayer")
                .build();

        ImplementServiceLayer implementServiceLayer = AgenticServices.agentBuilder(ImplementServiceLayer.class)
                .chatModel(baseModel())
                .outputKey("serviceCode")
                .name("implementServiceLayer")
                .build();

        ImplementController implementController = AgenticServices.agentBuilder(ImplementController.class)
                .chatModel(baseModel())
                .outputKey("controllerCode")
                .name("implementController")
                .build();

        ImplementUI implementUI = AgenticServices.agentBuilder(ImplementUI.class)
                .chatModel(baseModel())
                .outputKey("uiCode")
                .name("implementUI")
                .build();

        WriteTests writeTests = AgenticServices.agentBuilder(WriteTests.class)
                .chatModel(baseModel())
                .outputKey("tests")
                .name("writeTests")
                .build();

        Object runTests = new RunTests();

        ReviewCode reviewCode = AgenticServices.agentBuilder(ReviewCode.class)
                .chatModel(baseModel())
                .outputKey("reviewResult")
                .name("reviewCode")
                .build();

        FixIssues fixIssues = AgenticServices.agentBuilder(FixIssues.class)
                .chatModel(baseModel())
                .outputKey("fixedCode")
                .name("fixIssues")
                .build();

        GenerateDocumentation generateDocumentation = AgenticServices.agentBuilder(GenerateDocumentation.class)
                .chatModel(baseModel())
                .outputKey("documentation")
                .name("generateDocumentation")
                .build();

        CodingAgent codingAgent = AgenticServices.plannerBuilder(CodingAgent.class)
                .subAgents(analyzeSpecs, designArchitecture, defineAPIs, designDataModel,
                        implementDataLayer, implementServiceLayer, implementController, implementUI,
                        writeTests, runTests, reviewCode, fixIssues, generateDocumentation)
                .outputKey("documentation")
                .planner(() -> new HtnPlanner(tree))
                .build();

        ResultWithAgenticScope<String> result = codingAgent.generate(
                "Build a REST API for a task management system with user authentication, " +
                "CRUD operations for tasks and projects, role-based access control, " +
                "and webhook notifications. Use Quarkus and its necessary extensions.");

        assertThat(result.agenticScope().readState("specAnalysis", "")).isNotBlank();
        assertThat(result.agenticScope().readState("architecture", "")).isNotBlank();
        assertThat(result.agenticScope().readState("apiContracts", "")).isNotBlank();
        assertThat(result.agenticScope().readState("testResult", "")).contains("PASS");
        assertThat(result.agenticScope().readState("reviewResult", "")).isNotBlank();
        assertThat(result.agenticScope().readState("documentation", "")).isNotBlank();

        long implOutputs = List.of("dataLayerCode", "serviceCode", "controllerCode", "uiCode").stream()
                .filter(key -> !result.agenticScope().readState(key, "").isBlank())
                .count();
        assertThat(implOutputs).isGreaterThanOrEqualTo(1);

//        System.out.println(result.result());
//        HtmlReportGenerator.generateReport(codingAgent.agentMonitor(),
//        Path.of("src", "test", "resources", "htn-coding-report.html"));
    }
}
