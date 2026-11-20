package dev.langchain4j.agentic.micrometer;

import static dev.langchain4j.test.mock.MockChatModel.toolCall;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.planner.Action;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.InitPlanningContext;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.planner.PlanningContext;
import dev.langchain4j.agentic.supervisor.SupervisorAgent;
import dev.langchain4j.agentic.supervisor.SupervisorResponseStrategy;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.output.OutputParsingException;
import dev.langchain4j.test.mock.MockChatModel;
import dev.langchain4j.test.mock.MockStreamingChatModel;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class MicrometerMetricsAgentListenerTokenUsageTest {

    public interface Writer {

        @UserMessage("Write about {{topic}}")
        @Agent(outputKey = "story")
        String write(@V("topic") String topic);
    }

    public interface StreamingWriter {

        @UserMessage("Write about {{topic}}")
        @Agent(outputKey = "story")
        TokenStream write(@V("topic") String topic);
    }

    public interface Clock {

        @UserMessage("What time is it?")
        @Agent(outputKey = "time")
        String time();
    }

    public interface Pattern {

        @Agent
        String run(@V("topic") String topic);
    }

    static class ChatModelPlanner implements Planner {

        private final ChatModel chatModel;
        private List<AgentInstance> subagents;
        private int next;

        ChatModelPlanner(ChatModel chatModel) {
            this.chatModel = chatModel;
        }

        @Override
        public void init(InitPlanningContext initPlanningContext) {
            this.subagents = initPlanningContext.subagents();
        }

        @Override
        public Action nextAction(PlanningContext planningContext) {
            planningContext.reportChatModelResponse(
                    chatModel.chat(dev.langchain4j.data.message.UserMessage.from("What is the next step?")));
            return next < subagents.size() ? call(subagents.get(next++)) : done();
        }
    }

    public static class Facts {

        @Tool
        public String lookup() {
            return "facts";
        }
    }

    public static class ClockTool {

        private final Clock clock;

        ClockTool(Clock clock) {
            this.clock = clock;
        }

        @Tool
        public String lookup() {
            return clock.time();
        }
    }

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final MicrometerMetricsAgentListener listener = new MicrometerMetricsAgentListener(registry);

    @Test
    void records_the_tokens_of_every_model_call_of_an_agent_invocation() {
        Writer writer = AgenticServices.agentBuilder(Writer.class)
                .name("writer")
                .chatModel(modelCallingTool().withTokenUsage(new TokenUsage(10, 2)))
                .tools(new Facts())
                .build();

        sequence(writer).invoke(Map.of("topic", "dragons"));

        assertThat(tokens("invoke_agent", "writer", "input").totalAmount()).isEqualTo(20);
        assertThat(tokens("invoke_agent", "writer", "output").totalAmount()).isEqualTo(4);
        assertThat(tokens("invoke_agent", "writer", "input").count()).isEqualTo(1);
    }

    @Test
    void does_not_add_the_tokens_of_an_agent_called_by_a_tool_to_the_calling_agent() {
        Writer writer = AgenticServices.agentBuilder(Writer.class)
                .name("writer")
                .chatModel(modelCallingTool().withTokenUsage(new TokenUsage(10, 2)))
                .tools(new ClockTool(clock()))
                .build();

        sequence(writer).invoke(Map.of("topic", "dragons"));

        assertThat(tokens("invoke_agent", "writer", "input").totalAmount()).isEqualTo(20);
        assertThat(tokens("invoke_agent", "clock", "input").totalAmount()).isEqualTo(7);
    }

    @Test
    void records_no_tokens_for_a_model_reporting_no_token_usage() {
        Writer writer = AgenticServices.agentBuilder(Writer.class)
                .name("writer")
                .chatModel(MockChatModel.thatResponds("a story").withTokenUsage(null))
                .build();

        sequence(writer).invoke(Map.of("topic", "dragons"));

        assertThat(registry.find("langchain4j.agent.token.usage").summaries()).isEmpty();
        assertThat(registry.get("gen_ai.invoke_agent.inference_calls")
                        .tag("gen_ai.agent.name", "writer")
                        .summary()
                        .totalAmount())
                .isEqualTo(1);
    }

    @Test
    void records_the_tokens_of_a_streaming_agent_when_its_stream_completes() throws Exception {
        StreamingWriter writer = AgenticServices.agentBuilder(StreamingWriter.class)
                .name("writer")
                .streamingChatModel(
                        MockStreamingChatModel.thatStreams("a", "story").withTokenUsage(new TokenUsage(12, 3)))
                .listener(listener)
                .build();

        CompletableFuture<ChatResponse> response = new CompletableFuture<>();
        writer.write("dragons")
                .onPartialResponse(partial -> {})
                .onCompleteResponse(response::complete)
                .onError(response::completeExceptionally)
                .start();
        response.get(10, SECONDS);

        await().atMost(10, SECONDS).until(() -> listener.pendingInvocations() == 0);
        assertThat(tokens("invoke_agent", "writer", "input").totalAmount()).isEqualTo(12);
        assertThat(tokens("invoke_agent", "writer", "output").totalAmount()).isEqualTo(3);
    }

    @Test
    void records_the_tokens_of_the_planning_of_a_supervisor_on_its_own_invocation() {
        supervisor(MockChatModel.thatResponds(reply(INVOKE_WRITER, new TokenUsage(100, 20)))
                        .thenResponds(reply(DONE, new TokenUsage(30, 5))))
                .invoke("Write a story about dragons");

        assertThat(tokens("invoke_workflow", "supervisor", "input").totalAmount())
                .isEqualTo(130);
        assertThat(tokens("invoke_workflow", "supervisor", "output").totalAmount())
                .isEqualTo(25);
        assertThat(tokens("invoke_agent", "writer", "input").totalAmount()).isEqualTo(7);
    }

    @Test
    void records_the_tokens_of_a_planning_reply_that_cannot_be_parsed() {
        SupervisorAgent supervisor =
                supervisor(MockChatModel.thatResponds(reply("not an agent invocation", new TokenUsage(100, 20))));

        assertThatThrownBy(() -> supervisor.invoke("Write a story about dragons"))
                .isInstanceOf(OutputParsingException.class);

        assertThat(tokens("invoke_workflow", "supervisor", "input").totalAmount())
                .isEqualTo(100);
        assertThat(registry.get("gen_ai.invoke_agent.duration")
                        .tag("gen_ai.agent.name", "supervisor")
                        .timer()
                        .getId()
                        .getTag("outcome"))
                .isEqualTo("ERROR");
    }

    @Test
    void records_no_tokens_for_planning_replies_without_token_usage() {
        supervisor(MockChatModel.thatResponds(reply(INVOKE_WRITER, null)).thenResponds(reply(DONE, null)))
                .invoke("Write a story about dragons");

        assertThat(registry.find("langchain4j.agent.token.usage")
                        .tag("gen_ai.agent.name", "supervisor")
                        .summaries())
                .isEmpty();
    }

    @Test
    void records_the_tokens_of_a_supervisor_nested_in_a_workflow_with_the_inherited_listener() {
        SupervisorAgent supervisor = AgenticServices.supervisorBuilder()
                .name("supervisor")
                .chatModel(MockChatModel.thatResponds(reply(INVOKE_WRITER, new TokenUsage(100, 20)))
                        .thenResponds(reply(DONE, new TokenUsage(30, 5))))
                .responseStrategy(SupervisorResponseStrategy.LAST)
                .subAgents(writer())
                .build();

        AgenticServices.sequenceBuilder()
                .name("novel")
                .subAgents(supervisor)
                .outputKey("story")
                .listener(listener)
                .build()
                .invoke(Map.of("request", "Write a story about dragons"));

        assertThat(tokens("invoke_workflow", "supervisor", "input").totalAmount())
                .isEqualTo(130);
        assertThat(registry.find("langchain4j.agent.token.usage")
                        .tag("gen_ai.agent.name", "novel")
                        .summaries())
                .isEmpty();
    }

    @Test
    void records_the_tokens_of_the_model_calls_of_a_custom_planner() {
        ChatModel planningModel =
                MockChatModel.thatResponds("go on").thenRepeats().withTokenUsage(new TokenUsage(10, 2));
        Pattern pattern = AgenticServices.plannerBuilder(Pattern.class)
                .name("pattern")
                .subAgents(writer())
                .outputKey("story")
                .planner(() -> new ChatModelPlanner(planningModel))
                .listener(listener)
                .build();

        pattern.run("dragons");

        assertThat(tokens("invoke_workflow", "pattern", "input").totalAmount()).isEqualTo(20);
        assertThat(tokens("invoke_workflow", "pattern", "output").totalAmount()).isEqualTo(4);
        assertThat(tokens("invoke_agent", "writer", "input").totalAmount()).isEqualTo(7);
    }

    private static final String INVOKE_WRITER = """
            {"agentName": "writer", "arguments": {"topic": "dragons"}}
            """;

    private static final String DONE = """
            {"agentName": "done", "arguments": {"response": "a story"}}
            """;

    private SupervisorAgent supervisor(ChatModel planningModel) {
        return AgenticServices.supervisorBuilder()
                .name("supervisor")
                .chatModel(planningModel)
                .responseStrategy(SupervisorResponseStrategy.LAST)
                .subAgents(writer())
                .listener(listener)
                .build();
    }

    private static Writer writer() {
        return AgenticServices.agentBuilder(Writer.class)
                .name("writer")
                .chatModel(MockChatModel.thatResponds("a story").withTokenUsage(new TokenUsage(7, 1)))
                .build();
    }

    private Clock clock() {
        return AgenticServices.agentBuilder(Clock.class)
                .name("clock")
                .chatModel(MockChatModel.thatResponds("noon").withTokenUsage(new TokenUsage(7, 1)))
                .listener(listener)
                .build();
    }

    private UntypedAgent sequence(Object... subAgents) {
        return AgenticServices.sequenceBuilder()
                .name("novel")
                .subAgents(subAgents)
                .outputKey("story")
                .listener(listener)
                .build();
    }

    private static MockChatModel modelCallingTool() {
        return MockChatModel.thatResponds(request -> lastMessageIsToolResult(request)
                        ? AiMessage.from("a story")
                        : AiMessage.from(toolCall("lookup", Map.of())))
                .thenRepeats();
    }

    private static boolean lastMessageIsToolResult(ChatRequest request) {
        return request.messages().get(request.messages().size() - 1) instanceof ToolExecutionResultMessage;
    }

    private static ChatResponse reply(String text, TokenUsage tokenUsage) {
        return ChatResponse.builder()
                .aiMessage(AiMessage.from(text))
                .tokenUsage(tokenUsage)
                .build();
    }

    private DistributionSummary tokens(String operation, String agentName, String tokenType) {
        return registry.get("langchain4j.agent.token.usage")
                .tag("gen_ai.operation.name", operation)
                .tag("gen_ai.agent.name", agentName)
                .tag("gen_ai.token.type", tokenType)
                .summary();
    }
}
