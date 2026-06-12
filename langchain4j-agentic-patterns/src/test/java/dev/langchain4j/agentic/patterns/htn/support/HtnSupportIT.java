package dev.langchain4j.agentic.patterns.htn.support;

import static dev.langchain4j.agentic.patterns.Models.baseModel;
import static dev.langchain4j.agentic.patterns.htn.DecompositionMethod.decompose;
import static dev.langchain4j.agentic.patterns.htn.TaskNode.compound;
import static dev.langchain4j.agentic.patterns.htn.TaskNode.primitive;
import static dev.langchain4j.agentic.patterns.htn.TaskNode.primitives;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.observability.HtmlReportGenerator;
import dev.langchain4j.agentic.patterns.htn.HtnPlanner;
import dev.langchain4j.agentic.patterns.htn.TaskNode;
import dev.langchain4j.agentic.patterns.htn.support.SupportAgents.*;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.nio.file.Path;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class HtnSupportIT {

    @Test
    void handle_billing_ticket() {
        TaskNode tree = supportTree("BILLING");

        SupportHandler handler = buildHandler(tree);

        ResultWithAgenticScope<String> result = handler.handle(
                "I was charged twice for my subscription last month. Order #12345. Please refund the duplicate charge.");

        AgenticScope scope = result.agenticScope();
        assertThat(scope.readState("category", "")).containsIgnoringCase("BILLING");
        assertThat(scope.readState("billingInfo", "")).isNotBlank();
        assertThat(scope.readState("billingResolution", "")).isNotBlank();
        assertThat(scope.readState("resolutionContext", "")).isNotBlank();
        assertThat(result.result()).isNotBlank();

//        System.out.println(result.result());
//        HtmlReportGenerator.generateReport(handler.agentMonitor(),
//        Path.of("src", "test", "resources", "htn-support-billing-report.html"));
    }

    @Test
    void handle_technical_ticket() {
        TaskNode tree = supportTree("TECHNICAL");

        SupportHandler handler = buildHandler(tree);

        ResultWithAgenticScope<String> result = handler.handle(
                "The app keeps crashing when I try to upload files larger than 10MB. I'm using version 3.2 on Windows 11.");

        AgenticScope scope = result.agenticScope();
        assertThat(scope.readState("category", "")).containsIgnoringCase("TECHNICAL");
        assertThat(scope.readState("diagnosis", "")).isNotBlank();
        assertThat(scope.readState("suggestedFix", "")).isNotBlank();
        assertThat(scope.readState("resolutionContext", "")).isNotBlank();
        assertThat(result.result()).isNotBlank();

//        System.out.println(result.result());
//        HtmlReportGenerator.generateReport(handler.agentMonitor(),
//        Path.of("src", "test", "resources", "htn-support-technical-report.html"));
    }

    private static TaskNode supportTree(String expectedCategory) {
        return compound("Handle Support Ticket",
                primitive(ClassifyTicket.class,
                        scope -> scope.writeState("categoryResolved", expectedCategory)),
                compound("resolve",
                        decompose(
                                scope -> "BILLING".equals(scope.readState("categoryResolved", "")),
                                compound("billing-workflow",
                                        primitives(LookupBillingInfo.class, ResolveBillingIssue.class))),
                        decompose(
                                scope -> "TECHNICAL".equals(scope.readState("categoryResolved", "")),
                                compound("technical-workflow",
                                        primitives(DiagnoseProblem.class, SuggestFix.class))),
                        decompose(
                                primitive(SearchKnowledgeBase.class))),
                primitive(PrepareContext.class),
                primitive(DraftResponse.class));
    }

    private static SupportHandler buildHandler(TaskNode tree) {
        ClassifyTicket classifyTicket = AgenticServices.agentBuilder(ClassifyTicket.class)
                .chatModel(baseModel())
                .outputKey("category")
                .name("classifyTicket")
                .build();

        LookupBillingInfo lookupBillingInfo = AgenticServices.agentBuilder(LookupBillingInfo.class)
                .chatModel(baseModel())
                .outputKey("billingInfo")
                .name("lookupBillingInfo")
                .build();

        ResolveBillingIssue resolveBillingIssue = AgenticServices.agentBuilder(ResolveBillingIssue.class)
                .chatModel(baseModel())
                .outputKey("billingResolution")
                .name("resolveBillingIssue")
                .build();

        DiagnoseProblem diagnoseProblem = AgenticServices.agentBuilder(DiagnoseProblem.class)
                .chatModel(baseModel())
                .outputKey("diagnosis")
                .name("diagnoseProblem")
                .build();

        SuggestFix suggestFix = AgenticServices.agentBuilder(SuggestFix.class)
                .chatModel(baseModel())
                .outputKey("suggestedFix")
                .name("suggestFix")
                .build();

        SearchKnowledgeBase searchKnowledgeBase = AgenticServices.agentBuilder(SearchKnowledgeBase.class)
                .chatModel(baseModel())
                .outputKey("kbAnswer")
                .name("searchKnowledgeBase")
                .build();

        Object prepareContext = new PrepareContext();

        DraftResponse draftResponse = AgenticServices.agentBuilder(DraftResponse.class)
                .chatModel(baseModel())
                .outputKey("response")
                .name("draftResponse")
                .build();

        return AgenticServices.plannerBuilder(SupportHandler.class)
                .subAgents(classifyTicket, lookupBillingInfo, resolveBillingIssue,
                        diagnoseProblem, suggestFix, searchKnowledgeBase,
                        prepareContext, draftResponse)
                .outputKey("response")
                .planner(() -> new HtnPlanner(tree))
                .build();
    }

    public static class PrepareContext {
        @Agent(name = "prepareContext",
                value = "Gather resolution info into a single context key")
        public void run(AgenticScope scope) {
            StringBuilder ctx = new StringBuilder();
            appendIfPresent(ctx, scope, "billingInfo", "Billing Info");
            appendIfPresent(ctx, scope, "billingResolution", "Billing Resolution");
            appendIfPresent(ctx, scope, "diagnosis", "Diagnosis");
            appendIfPresent(ctx, scope, "suggestedFix", "Suggested Fix");
            appendIfPresent(ctx, scope, "kbAnswer", "Knowledge Base Answer");
            scope.writeState("resolutionContext", ctx.toString());
        }

        private static void appendIfPresent(StringBuilder ctx, AgenticScope scope,
                                             String key, String label) {
            String value = scope.readState(key, "");
            if (!value.isBlank()) {
                ctx.append(label).append(": ").append(value).append("\n");
            }
        }
    }
}
