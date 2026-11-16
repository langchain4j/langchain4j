package dev.langchain4j.agentic.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.agent.AgentUnavailableException;
import dev.langchain4j.agentic.planner.Action;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.planner.InitPlanningContext;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.planner.PlanningContext;
import dev.langchain4j.agentic.supervisor.SupervisorAgent;
import dev.langchain4j.agentic.supervisor.SupervisorResponseStrategy;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.test.mock.MockChatModel;
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

    @Test
    void reports_a_suspended_execution_as_suspended_until_its_root_agent_is_invoked_again() {
        SampleReportGenerator.MockAgent root = new SampleReportGenerator.MockAgent(
                "ask",
                MedicalExpert.class,
                AgenticSystemTopology.SEQUENCE,
                "Asks the human for the question",
                List.of(),
                "response",
                String.class);
        SampleReportGenerator.MockAgent step = new SampleReportGenerator.MockAgent(
                "step",
                MedicalExpert.class,
                AgenticSystemTopology.AI_AGENT,
                "A step before the human one",
                List.of(),
                "step",
                String.class);
        step.parent = root;
        root.subagents = List.of(step);
        AgentMonitor monitor = new AgentMonitor();
        monitor.setRootAgent(root);
        SampleReportGenerator.MockScope scope = new SampleReportGenerator.MockScope("user-alice");

        monitor.beforeAgentInvocation(new AgentRequest(scope, root, Map.of()));
        monitor.beforeAgentInvocation(new AgentRequest(scope, step, Map.of()));
        monitor.afterAgentInvocation(new AgentResponse(scope, step, Map.of()));
        monitor.onAgenticSystemSuspended(scope);

        String html = HtmlReportGenerator.generateReport(monitor);
        assertThat(html)
                .contains("<span class=\"dur-badge\">suspended</span>")
                .contains("title=\"ask: suspended\"")
                .doesNotContain("running...");

        monitor.beforeAgentInvocation(new AgentRequest(scope, root, Map.of()));

        html = HtmlReportGenerator.generateReport(monitor);
        assertThat(html).contains("<span class=\"dur-badge\">running...</span>").doesNotContain(">suspended<");
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
                .chatModel(MockChatModel.thatResponds(ChatResponse.builder()
                                .aiMessage(AiMessage.from(ToolExecutionRequest.builder()
                                        .id("1")
                                        .name("time")
                                        .arguments("{}")
                                        .build()))
                                .modelName("answering-model")
                                .build())
                        .thenResponds(ChatResponse.builder()
                                .aiMessage(AiMessage.from("It is noon"))
                                .modelName("answering-model")
                                .build()))
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
                .chatModel(MockChatModel.thatResponds(ChatResponse.builder()
                                .aiMessage(AiMessage.from(ToolExecutionRequest.builder()
                                        .id("1")
                                        .name("time")
                                        .arguments("{}")
                                        .build()))
                                .modelName("tool-calling-model")
                                .tokenUsage(new TokenUsage(100, 20))
                                .build())
                        .thenResponds(ChatResponse.builder()
                                .aiMessage(AiMessage.from("It is noon"))
                                .modelName("answering-model")
                                .tokenUsage(new TokenUsage(30, 5))
                                .build()))
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

    private static final AiMessage INVOKE_CLOCK = AiMessage.from("""
            {"agentName": "clock", "arguments": {}}
            """);

    /**
     * A model making a supervisor invoke its only subagent, the clock, and then end, with its two answers given by the
     * given models.
     */
    private static MockChatModel planningModel(String firstModel, String secondModel) {
        return MockChatModel.thatResponds(ChatResponse.builder()
                        .aiMessage(INVOKE_CLOCK)
                        .modelName(firstModel)
                        .tokenUsage(new TokenUsage(100, 20))
                        .build())
                .thenResponds(ChatResponse.builder()
                        .aiMessage(AiMessage.from("""
                                {"agentName": "done", "arguments": {"response": "It is noon"}}
                                """))
                        .modelName(secondModel)
                        .tokenUsage(new TokenUsage(30, 5))
                        .build());
    }

    private static SupervisorAgent supervisorPlanningWith(MockChatModel planningModel, AgentMonitor monitor) {
        return AgenticServices.supervisorBuilder()
                .chatModel(planningModel)
                .responseStrategy(SupervisorResponseStrategy.SUMMARY)
                .subAgents(AgenticServices.nonAiAgentBuilder(scope -> "noon")
                        .name("clock")
                        .description("Tells the time")
                        .outputKey("time")
                        .build())
                .listener(monitor)
                .build();
    }

    private static String executionOfSupervisorPlanningWith(MockChatModel planningModel) {
        AgentMonitor monitor = new AgentMonitor();
        supervisorPlanningWith(planningModel, monitor).invoke("What time is it?");
        return HtmlReportGenerator.generateExecution(monitor);
    }

    @Test
    void shows_the_model_and_the_tokens_spent_by_a_pattern_whose_execution_failed() {
        // the supervisor invokes the clock, then its second planning call fails
        MockChatModel planningModel = MockChatModel.thatResponds(ChatResponse.builder()
                        .aiMessage(INVOKE_CLOCK)
                        .modelName("planning-model")
                        .tokenUsage(new TokenUsage(100, 20))
                        .build())
                .thenFails(new IllegalStateException("planning failed"));
        AgentMonitor monitor = new AgentMonitor();
        SupervisorAgent supervisor = supervisorPlanningWith(planningModel, monitor);

        assertThatThrownBy(() -> supervisor.invoke("What time is it?")).hasMessageContaining("planning failed");
        String html = HtmlReportGenerator.generateExecution(monitor);

        // the row of the supervisor, which never completed, still shows what its first planning call spent
        assertThat(html).contains("planning failed");
        String body = html.substring(html.indexOf("<tbody>"), html.indexOf("</tbody>"));
        String[] rows = body.split("<tr");
        assertThat(rows).hasSize(3);
        assertThat(rows[1])
                .contains("<td class=\"wf-dur\">planning-model</td>")
                .contains("<td class=\"wf-dur\">120</td>");
    }

    @Test
    void shows_the_model_and_the_tokens_of_the_model_calls_of_a_pattern_on_its_own_row() {
        String html = executionOfSupervisorPlanningWith(
                planningModel("planning-model", "planning-model"));

        // the supervisor is the first row: it shows the model and the total tokens of its two planning calls
        String body = html.substring(html.indexOf("<tbody>"), html.indexOf("</tbody>"));
        String[] rows = body.split("<tr");
        assertThat(rows).hasSize(3);
        assertThat(rows[1])
                .contains("<td class=\"wf-dur\">planning-model</td>")
                .contains("<td class=\"wf-dur\">155</td>");
        // the subagent, which calls no model, shows neither
        assertThat(rows[2]).doesNotContain("planning-model").doesNotContain("155");
    }

    @Test
    void does_not_show_a_model_next_to_the_tokens_of_a_pattern_whose_model_calls_were_answered_by_different_models() {
        String html = executionOfSupervisorPlanningWith(planningModel("first-model", "second-model"));

        assertThat(html).doesNotContain("first-model").doesNotContain("second-model");
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

    @Test
    void shows_the_description_of_an_agent_as_unavailable_when_it_cannot_be_read() {
        // like an A2A agent whose card cannot be fetched because its server is down
        AgentInstance remoteAgent = mock(AgentInstance.class);
        when(remoteAgent.name()).thenReturn("fixer");
        when(remoteAgent.agentId()).thenReturn("fixer$0");
        when(remoteAgent.topology()).thenReturn(AgenticSystemTopology.AI_AGENT);
        when(remoteAgent.description())
                .thenThrow(new AgentUnavailableException(
                        "fixer",
                        "Failed to fetch the agent card of A2A agent 'fixer'",
                        new IllegalStateException("connection refused")));

        String html = HtmlReportGenerator.generateTopology(remoteAgent);

        assertThat(html).contains("fixer").contains("unavailable: Failed to fetch the agent card");
    }

    @Test
    void fails_when_the_description_of_an_agent_fails_for_another_reason_than_its_unavailability() {
        // like an MCP agent whose configured tool does not exist on its server: a configuration error to report
        AgentInstance misconfiguredAgent = mock(AgentInstance.class);
        when(misconfiguredAgent.name()).thenReturn("fixer");
        when(misconfiguredAgent.agentId()).thenReturn("fixer$0");
        when(misconfiguredAgent.topology()).thenReturn(AgenticSystemTopology.AI_AGENT);
        when(misconfiguredAgent.description()).thenThrow(new IllegalStateException("Tool 'fixr' not found"));

        assertThatThrownBy(() -> HtmlReportGenerator.generateTopology(misconfiguredAgent))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'fixr' not found");
    }
}
