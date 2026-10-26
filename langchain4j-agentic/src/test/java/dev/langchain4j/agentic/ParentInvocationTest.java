package dev.langchain4j.agentic;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agentic.supervisor.SupervisorAgent;
import dev.langchain4j.agentic.supervisor.SupervisorResponseStrategy;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

class ParentInvocationTest {

    public interface FirstAgent {

        @UserMessage("{{text}}")
        @Agent(description = "Answers first", outputKey = "first")
        String answer(@V("text") String text);
    }

    public interface SecondAgent {

        @UserMessage("{{text}}")
        @Agent(description = "Answers second", outputKey = "second")
        String answer(@V("text") String text);
    }

    static class Recorder {

        final List<InvocationContext> invocations = new CopyOnWriteArrayList<>();

        @Tool
        String record(InvocationContext invocationContext) {
            invocations.add(invocationContext);
            return "recorded";
        }
    }

    static class AskingAgentTool {

        final List<InvocationContext> invocations = new CopyOnWriteArrayList<>();
        final SecondAgent agent;

        AskingAgentTool(SecondAgent agent) {
            this.agent = agent;
        }

        @Tool
        String askAgent(InvocationContext invocationContext) {
            invocations.add(invocationContext);
            return agent.answer("inner question");
        }
    }

    @Test
    void should_link_agent_called_from_tool_to_calling_agent() {
        Recorder innerRecorder = new Recorder();
        SecondAgent inner = AgenticServices.agentBuilder(SecondAgent.class)
                .chatModel(callingToolThenAnswering("record"))
                .tools(innerRecorder)
                .build();
        AskingAgentTool askingAgentTool = new AskingAgentTool(inner);
        FirstAgent outer = AgenticServices.agentBuilder(FirstAgent.class)
                .chatModel(callingToolThenAnswering("askAgent"))
                .tools(askingAgentTool)
                .build();

        outer.answer("Hello");

        InvocationContext outerInvocation = askingAgentTool.invocations.get(0);
        assertThat(outerInvocation.parentInvocationId()).isNull();
        assertThat(innerRecorder.invocations)
                .singleElement()
                .satisfies(invocation ->
                        assertThat(invocation.parentInvocationId()).isEqualTo(outerInvocation.invocationId()));
    }

    @Test
    void should_not_link_sub_agents_of_sequence_to_parent_yet() {
        // no parent is the current limitation for sub-agents run by the agentic framework (to be revisited), not the
        // target behaviour
        Recorder firstRecorder = new Recorder();
        Recorder secondRecorder = new Recorder();
        FirstAgent first = AgenticServices.agentBuilder(FirstAgent.class)
                .chatModel(callingToolThenAnswering("record"))
                .tools(firstRecorder)
                .build();
        SecondAgent second = AgenticServices.agentBuilder(SecondAgent.class)
                .chatModel(callingToolThenAnswering("record"))
                .tools(secondRecorder)
                .build();
        UntypedAgent sequence = AgenticServices.sequenceBuilder()
                .subAgents(first, second)
                .outputKey("second")
                .build();

        sequence.invoke(Map.of("text", "Hello"));

        assertThat(firstRecorder.invocations).singleElement().satisfies(invocation -> assertThat(
                        invocation.parentInvocationId())
                .isNull());
        assertThat(secondRecorder.invocations).singleElement().satisfies(invocation -> assertThat(
                        invocation.parentInvocationId())
                .isNull());
    }

    @Test
    void should_not_link_sub_agent_of_supervisor_to_parent_yet() {
        // no parent is the current limitation for sub-agents run by the agentic framework (to be revisited), not the
        // target behaviour
        ChatModelMock plannerModel = ChatModelMock.thatAlwaysResponds(
                AiMessage.from("""
                {"agentName": "answer", "arguments": {"text": "Hello"}}
                """),
                AiMessage.from("""
                {"agentName": "done", "arguments": {"response": "done"}}
                """));
        Recorder recorder = new Recorder();
        FirstAgent subAgent = AgenticServices.agentBuilder(FirstAgent.class)
                .chatModel(callingToolThenAnswering("record"))
                .tools(recorder)
                .build();
        SupervisorAgent supervisor = AgenticServices.supervisorBuilder()
                .chatModel(plannerModel)
                .responseStrategy(SupervisorResponseStrategy.SUMMARY)
                .subAgents(subAgent)
                .build();

        supervisor.invoke("Hello");

        assertThat(recorder.invocations).singleElement().satisfies(invocation -> assertThat(
                        invocation.parentInvocationId())
                .isNull());
    }

    private static ChatModel callingToolThenAnswering(String toolName) {
        return ChatModelMock.thatResponds(request -> {
            List<ChatMessage> messages = request.messages();
            if (messages.get(messages.size() - 1) instanceof ToolExecutionResultMessage) {
                return AiMessage.from("answer");
            }
            return AiMessage.from(ToolExecutionRequest.builder()
                    .id("1")
                    .name(toolName)
                    .arguments("{}")
                    .build());
        });
    }
}
