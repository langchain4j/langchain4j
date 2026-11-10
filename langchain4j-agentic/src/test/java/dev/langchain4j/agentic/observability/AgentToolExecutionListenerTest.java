package dev.langchain4j.agentic.observability;

import static dev.langchain4j.test.mock.ChatModelMock.thatAlwaysResponds;
import static dev.langchain4j.test.mock.ChatModelMock.thatResponds;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.agent.AgentBuilder;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

class AgentToolExecutionListenerTest {

    @Test
    void listener_is_notified_before_and_after_a_tool_execution() {
        List<String> calls = new CopyOnWriteArrayList<>();
        Assistant assistant = assistantBuilder(calls)
                .listener(new AgentListener() {
                    @Override
                    public void beforeAgentToolExecution(BeforeAgentToolExecution execution) {
                        calls.add(
                                "before " + execution.toolExecution().request().name());
                    }

                    @Override
                    public void afterAgentToolExecution(AfterAgentToolExecution execution) {
                        calls.add("after " + execution.toolExecution().request().name());
                    }
                })
                .build();

        assertThat(assistant.answer("weather")).isEqualTo("sunny");

        assertThat(calls).containsExactly("before weather", "weather executed", "after weather");
    }

    @Test
    void failing_listener_before_a_tool_execution_does_not_fail_the_agent() {
        List<String> calls = new CopyOnWriteArrayList<>();
        Assistant assistant = assistantBuilder(calls)
                .listener(new AgentListener() {
                    @Override
                    public void beforeAgentToolExecution(BeforeAgentToolExecution execution) {
                        calls.add(
                                "before " + execution.toolExecution().request().name());
                        throw new IllegalStateException("listener failure");
                    }

                    @Override
                    public void afterAgentToolExecution(AfterAgentToolExecution execution) {
                        calls.add("after " + execution.toolExecution().request().name());
                    }
                })
                .build();

        assertThat(assistant.answer("weather")).isEqualTo("sunny");

        assertThat(calls).containsExactly("before weather", "weather executed", "after weather");
    }

    @Test
    void failing_listener_after_a_tool_execution_does_not_fail_the_agent() {
        List<String> calls = new CopyOnWriteArrayList<>();
        Assistant assistant = assistantBuilder(calls)
                .listener(new AgentListener() {
                    @Override
                    public void beforeAgentToolExecution(BeforeAgentToolExecution execution) {
                        calls.add(
                                "before " + execution.toolExecution().request().name());
                    }

                    @Override
                    public void afterAgentToolExecution(AfterAgentToolExecution execution) {
                        calls.add("after " + execution.toolExecution().request().name());
                        throw new IllegalStateException("listener failure");
                    }
                })
                .build();

        assertThat(assistant.answer("weather")).isEqualTo("sunny");

        assertThat(calls).containsExactly("before weather", "weather executed", "after weather");
    }

    @Test
    void failing_tool_execution_listener_inherited_from_a_workflow_does_not_fail_its_subagent() {
        List<String> calls = new CopyOnWriteArrayList<>();
        UntypedAgent sequence = AgenticServices.sequenceBuilder()
                .subAgents(assistantBuilder(calls).build())
                .outputKey("answer")
                .listener(new AgentListener() {
                    @Override
                    public void beforeAgentToolExecution(BeforeAgentToolExecution execution) {
                        calls.add(
                                "before " + execution.toolExecution().request().name());
                        throw new IllegalStateException("listener failure");
                    }

                    @Override
                    public void afterAgentToolExecution(AfterAgentToolExecution execution) {
                        calls.add("after " + execution.toolExecution().request().name());
                        throw new IllegalStateException("listener failure");
                    }

                    @Override
                    public boolean inheritedBySubagents() {
                        return true;
                    }
                })
                .build();

        assertThat(sequence.invoke(Map.of("request", "weather"))).isEqualTo("sunny");

        assertThat(calls).containsExactly("before weather", "weather executed", "after weather");
    }

    private static AgentBuilder<Assistant, ?> assistantBuilder(List<String> calls) {
        return AgenticServices.agentBuilder(Assistant.class)
                .chatModel(thatResponds(weatherCall(), AiMessage.from("sunny")))
                .tools(new WeatherTools(calls));
    }

    private static AiMessage weatherCall() {
        return AiMessage.from(ToolExecutionRequest.builder()
                .id("weather-1")
                .name("weather")
                .arguments("{}")
                .build());
    }

    public interface Assistant {
        @UserMessage("{{request}}")
        @Agent(name = "assistant", outputKey = "answer")
        String answer(@V("request") String request);
    }

    public static class WeatherTools {
        private final List<String> calls;

        WeatherTools(List<String> calls) {
            this.calls = calls;
        }

        @Tool("Weather forecast")
        String weather() {
            calls.add("weather executed");
            return "sunny";
        }
    }
}
