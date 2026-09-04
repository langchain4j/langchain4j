package dev.langchain4j.agentic.supervisor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.tool.ToolErrorHandlerResult;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProviderResult;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SupervisorToolsTest {

    private static final String DELEGATE = """
            {"agentName":"answer","arguments":{"request":"what time is it"}}""";
    private static final String DONE = """
            {"agentName":"done","arguments":{"response":"final answer"}}""";

    public interface Expert {

        @UserMessage("Answer: {{request}}")
        @Agent(description = "An expert", outputKey = "response")
        String answer(@V("request") String request);
    }

    public static class ClockTools {

        @Tool("returns the current time")
        String currentTime() {
            return "12:00";
        }
    }

    public static class LoopingTools {

        final AtomicInteger invocations = new AtomicInteger();

        @Tool("always asks to be called again")
        String again() {
            invocations.incrementAndGet();
            return "call me again";
        }
    }

    public static class FailingTools {

        @Tool("always fails")
        String explode() {
            throw new IllegalStateException("tool failure");
        }
    }

    public static class ExpertTools {

        @Tool("looks up a rate")
        String rate() {
            return "1.0";
        }
    }

    static class AlwaysCallsToolModel implements ChatModel {

        final AtomicInteger modelCalls = new AtomicInteger();

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            int n = modelCalls.incrementAndGet();
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from(ToolExecutionRequest.builder()
                            .id("call-" + n)
                            .name("again")
                            .arguments("{}")
                            .build()))
                    .build();
        }
    }

    static class RecordingModel implements ChatModel {

        final List<ChatRequest> requests = new CopyOnWriteArrayList<>();
        private final Queue<AiMessage> responses = new ConcurrentLinkedQueue<>();

        RecordingModel(AiMessage... responses) {
            this.responses.addAll(List.of(responses));
        }

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            requests.add(chatRequest);
            AiMessage next = responses.isEmpty() ? AiMessage.from(DONE) : responses.poll();
            return ChatResponse.builder().aiMessage(next).build();
        }
    }

    private static RecordingModel plannerModel() {
        return new RecordingModel(AiMessage.from(DELEGATE), AiMessage.from(DONE));
    }

    private static ChatRequest plannerRequest(RecordingModel model) {
        return model.requests.stream()
                .filter(SupervisorToolsTest::isPlannerRequest)
                .findFirst()
                .orElseThrow();
    }

    private static boolean isPlannerRequest(ChatRequest request) {
        return request.messages().stream().anyMatch(SupervisorToolsTest::isPlannerSystemMessage);
    }

    private static boolean isPlannerSystemMessage(ChatMessage message) {
        return message instanceof SystemMessage systemMessage
                && systemMessage.text().contains("planner expert");
    }

    private static List<String> toolNames(ChatRequest request) {
        return request.toolSpecifications() == null
                ? List.of()
                : request.toolSpecifications().stream()
                        .map(ToolSpecification::name)
                        .toList();
    }

    private static String plannerSystemMessage(RecordingModel model) {
        return plannerRequest(model).messages().stream()
                .filter(SupervisorToolsTest::isPlannerSystemMessage)
                .map(message -> ((SystemMessage) message).text())
                .findFirst()
                .orElseThrow();
    }

    private static SupervisorAgent supervisorWith(ChatModel plannerModel, ChatModel expertModel, Object... tools) {
        Expert expert = AgenticServices.agentBuilder(Expert.class)
                .chatModel(expertModel)
                .build();
        SupervisorAgentService<SupervisorAgent> builder =
                AgenticServices.supervisorBuilder().chatModel(plannerModel).subAgents(expert);
        if (tools.length > 0) {
            builder.tools(tools);
        }
        return builder.build();
    }

    @Test
    void should_declare_planner_tools_to_the_planner_model() {
        RecordingModel planner = plannerModel();
        supervisorWith(planner, new RecordingModel(AiMessage.from("expert answer")), new ClockTools())
                .invoke("what time is it");

        assertThat(toolNames(plannerRequest(planner))).contains("currentTime");
    }

    @Test
    void should_not_declare_any_tool_when_none_is_registered() {
        RecordingModel planner = plannerModel();
        supervisorWith(planner, new RecordingModel(AiMessage.from("expert answer")))
                .invoke("what time is it");

        assertThat(toolNames(plannerRequest(planner))).isEmpty();
    }

    @Test
    void should_not_declare_planner_tools_to_sub_agent_models() {
        RecordingModel planner = plannerModel();
        RecordingModel expertModel = new RecordingModel(AiMessage.from("expert answer"));
        supervisorWith(planner, expertModel, new ClockTools()).invoke("what time is it");

        assertThat(expertModel.requests).isNotEmpty();
        assertThat(expertModel.requests)
                .allSatisfy(request -> assertThat(toolNames(request)).doesNotContain("currentTime"));
    }

    @Test
    void should_not_declare_sub_agent_tools_to_the_planner_model() {
        RecordingModel planner = plannerModel();
        Expert expert = AgenticServices.agentBuilder(Expert.class)
                .chatModel(new RecordingModel(AiMessage.from("expert answer")))
                .tools(new ExpertTools())
                .build();
        AgenticServices.supervisorBuilder()
                .chatModel(planner)
                .subAgents(expert)
                .tools(new ClockTools())
                .build()
                .invoke("what time is it");

        assertThat(toolNames(plannerRequest(planner))).contains("currentTime").doesNotContain("rate");
    }

    @Test
    void should_declare_programmatic_tools_to_the_planner_model() {
        RecordingModel planner = plannerModel();
        ToolSpecification specification = ToolSpecification.builder()
                .name("lookup")
                .description("looks something up")
                .build();
        ToolExecutor executor = (request, memoryId) -> "looked up";

        Expert expert = AgenticServices.agentBuilder(Expert.class)
                .chatModel(new RecordingModel(AiMessage.from("expert answer")))
                .build();
        AgenticServices.supervisorBuilder()
                .chatModel(planner)
                .subAgents(expert)
                .tools(Map.of(specification, executor))
                .build()
                .invoke("what time is it");

        assertThat(toolNames(plannerRequest(planner))).contains("lookup");
    }

    @Test
    void should_declare_tools_supplied_by_a_tool_provider() {
        RecordingModel planner = plannerModel();
        ToolSpecification specification = ToolSpecification.builder()
                .name("provided")
                .description("supplied by a provider")
                .build();

        Expert expert = AgenticServices.agentBuilder(Expert.class)
                .chatModel(new RecordingModel(AiMessage.from("expert answer")))
                .build();
        AgenticServices.supervisorBuilder()
                .chatModel(planner)
                .subAgents(expert)
                .toolProvider(request -> ToolProviderResult.builder()
                        .add(specification, (executionRequest, memoryId) -> "provided result")
                        .build())
                .build()
                .invoke("what time is it");

        assertThat(toolNames(plannerRequest(planner))).contains("provided");
    }

    @Test
    void should_execute_a_planner_tool_before_delegating() {
        ClockTools clock = new ClockTools();
        RecordingModel planner = new RecordingModel(
                AiMessage.from(ToolExecutionRequest.builder()
                        .id("call-1")
                        .name("currentTime")
                        .arguments("{}")
                        .build()),
                AiMessage.from(DELEGATE),
                AiMessage.from(DONE));

        supervisorWith(planner, new RecordingModel(AiMessage.from("expert answer")), clock)
                .invoke("what time is it");

        assertThat(planner.requests)
                .anySatisfy(request -> assertThat(request.messages())
                        .anyMatch(message -> message.toString().contains("12:00")));
    }

    @Test
    void should_route_a_hallucinated_tool_name_through_the_configured_strategy() {
        AtomicBoolean strategyCalled = new AtomicBoolean();
        RecordingModel planner = new RecordingModel(
                AiMessage.from(ToolExecutionRequest.builder()
                        .id("call-1")
                        .name("noSuchTool")
                        .arguments("{}")
                        .build()),
                AiMessage.from(DELEGATE),
                AiMessage.from(DONE));

        Expert expert = AgenticServices.agentBuilder(Expert.class)
                .chatModel(new RecordingModel(AiMessage.from("expert answer")))
                .build();
        AgenticServices.supervisorBuilder()
                .chatModel(planner)
                .subAgents(expert)
                .tools(new ClockTools())
                .hallucinatedToolNameStrategy(request -> {
                    strategyCalled.set(true);
                    return ToolExecutionResultMessage.from(request, "no such tool");
                })
                .build()
                .invoke("what time is it");

        assertThat(strategyCalled).isTrue();
    }

    @Test
    void should_route_a_failing_planner_tool_through_the_configured_error_handler() {
        AtomicBoolean handlerCalled = new AtomicBoolean();
        RecordingModel planner = new RecordingModel(
                AiMessage.from(ToolExecutionRequest.builder()
                        .id("call-1")
                        .name("explode")
                        .arguments("{}")
                        .build()),
                AiMessage.from(DELEGATE),
                AiMessage.from(DONE));

        Expert expert = AgenticServices.agentBuilder(Expert.class)
                .chatModel(new RecordingModel(AiMessage.from("expert answer")))
                .build();
        AgenticServices.supervisorBuilder()
                .chatModel(planner)
                .subAgents(expert)
                .tools(new FailingTools())
                .toolExecutionErrorHandler((error, context) -> {
                    handlerCalled.set(true);
                    return ToolErrorHandlerResult.text("recovered");
                })
                .build()
                .invoke("what time is it");

        assertThat(handlerCalled).isTrue();
    }

    @Test
    void should_declare_planner_tools_when_concurrent_execution_is_enabled() {
        RecordingModel planner = plannerModel();
        Expert expert = AgenticServices.agentBuilder(Expert.class)
                .chatModel(new RecordingModel(AiMessage.from("expert answer")))
                .build();
        AgenticServices.supervisorBuilder()
                .chatModel(planner)
                .subAgents(expert)
                .tools(new ClockTools())
                .executeToolsConcurrently()
                .build()
                .invoke("what time is it");

        assertThat(toolNames(plannerRequest(planner))).contains("currentTime");
    }

    @Test
    void should_bound_tool_round_trips_within_a_single_planning_turn() {
        LoopingTools loopingTools = new LoopingTools();
        AlwaysCallsToolModel planner = new AlwaysCallsToolModel();
        AlwaysCallsToolModel expertModel = new AlwaysCallsToolModel();

        Expert expert = AgenticServices.agentBuilder(Expert.class)
                .chatModel(expertModel)
                .build();
        SupervisorAgent supervisor = AgenticServices.supervisorBuilder()
                .chatModel(planner)
                .subAgents(expert)
                .tools(loopingTools)
                .maxToolCallingRoundTrips(3)
                .maxAgentsInvocations(7)
                .build();

        assertThatThrownBy(() -> supervisor.invoke("what time is it"))
                .hasMessageContaining("exceeded 3 tool calling round trips");

        assertThat(loopingTools.invocations).hasValue(3);
        assertThat(expertModel.modelCalls).hasValue(0);
    }

    @Test
    void should_keep_the_planning_prompt_unchanged_when_no_tool_is_registered() {
        RecordingModel planner = plannerModel();
        supervisorWith(planner, new RecordingModel(AiMessage.from("expert answer")))
                .invoke("what time is it");

        assertThat(plannerSystemMessage(planner))
                .contains("the only thing that you can do is rely on the provided agents")
                .doesNotContain("tool");
    }

    @Test
    void should_use_the_tool_aware_planning_prompt_when_a_tool_is_registered() {
        RecordingModel planner = plannerModel();
        supervisorWith(planner, new RecordingModel(AiMessage.from("expert answer")), new ClockTools())
                .invoke("what time is it");

        assertThat(plannerSystemMessage(planner))
                .contains("relying on the provided agents and calling the provided tools")
                .contains("Call a tool only to obtain a fact you need");
    }
}
