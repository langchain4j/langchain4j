package dev.langchain4j.agentic.observability;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.planner.Action;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.planner.InitPlanningContext;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.planner.PlanningContext;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.UserMessage;
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
    void generates_topology_with_the_activation_condition_of_an_agent() {
        UntypedAgent router = AgenticServices.conditionalBuilder()
                .subAgents(
                        "is medical",
                        agenticScope -> true,
                        AgenticServices.nonAiAgentBuilder(scope -> "medical answer")
                                .name("medical")
                                .activationCondition("the request is urgent", scope -> true)
                                .build())
                .outputKey("response")
                .build();

        String html = HtmlReportGenerator.generateTopology(router);

        // the condition of the branch and the one of the agent itself are shown separately
        assertThat(html).contains("when: is medical").contains(">if: the request is urgent</span>");
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

    public interface Assistant {

        @UserMessage("Answer {{question}}")
        @Agent(outputKey = "answer")
        String answer(@V("question") String question);
    }

    public interface Workflow {

        @Agent
        String run(@V("question") String question);
    }

    public static class Clock {

        @Tool
        public String time() {
            return "noon";
        }
    }

    @Test
    void generates_execution_with_the_model_of_each_ai_agent() {
        Assistant assistant = AgenticServices.agentBuilder(
                        Assistant.class)
                .chatModel(ChatModelMock.thatAlwaysResponds(
                                AiMessage.from(ToolExecutionRequest.builder()
                                        .id("1")
                                        .name("time")
                                        .arguments("{}")
                                        .build()),
                                AiMessage.from("It is noon"))
                        .withModelNames("answering-model", "answering-model"))
                .tools(new Clock())
                .build();
        AgentMonitor monitor = new AgentMonitor();
        AgenticServices.sequenceBuilder(Workflow.class)
                .subAgents(assistant)
                .outputKey("answer")
                .listener(monitor)
                .build()
                .run("what time is it?");

        String html = HtmlReportGenerator.generateExecution(monitor);

        assertThat(html).contains("<th>Duration</th><th>Model</th><th>Tokens</th>");
        // only the AI agent row shows a model, not the sequence or the tool rows
        assertThat(countMatches(html, Pattern.quote("<td class=\"wf-dur\">answering-model</td>")))
                .isEqualTo(1);

        // the rows of the sequence, of the AI agent and of its tool execution all have a cell per column
        String body = html.substring(html.indexOf("<tbody>"), html.indexOf("</tbody>"));
        String[] rows = body.split("<tr");
        assertThat(rows).hasSize(4);
        for (int i = 1; i < rows.length; i++) {
            assertThat(countMatches(rows[i], "<td")).isEqualTo(7);
        }
    }

    @Test
    void does_not_show_a_model_next_to_the_tokens_of_llm_calls_answered_by_different_models() {
        Assistant assistant = AgenticServices.agentBuilder(Assistant.class)
                .chatModel(ChatModelMock.thatAlwaysResponds(
                                AiMessage.from(ToolExecutionRequest.builder()
                                        .id("1")
                                        .name("time")
                                        .arguments("{}")
                                        .build()),
                                AiMessage.from("It is noon"))
                        .withModelNames("tool-calling-model", "answering-model")
                        .withTokenUsages(new TokenUsage(100, 20), new TokenUsage(30, 5)))
                .tools(new Clock())
                .build();
        AgentMonitor monitor = new AgentMonitor();
        AgenticServices.sequenceBuilder(Workflow.class)
                .subAgents(assistant)
                .outputKey("answer")
                .listener(monitor)
                .build()
                .run("what time is it?");

        String html = HtmlReportGenerator.generateExecution(monitor);

        // the tokens column shows the total of both LLM calls, so no single model can be credited with it
        assertThat(html).doesNotContain("tool-calling-model").doesNotContain("answering-model");
        assertThat(html).contains("<td class=\"wf-dur\">155</td>");
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
