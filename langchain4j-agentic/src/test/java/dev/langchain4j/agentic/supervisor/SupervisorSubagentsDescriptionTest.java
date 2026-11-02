package dev.langchain4j.agentic.supervisor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.AgenticServices.AgenticScopeFunction;
import dev.langchain4j.agentic.planner.AgenticSystemConfigurationException;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * The supervisor sends the description of each subagent to its model to choose the next one, so a subagent without
 * a description makes it fail at its first invocation instead of silently planning over a {@code null}.
 */
class SupervisorSubagentsDescriptionTest {

    /** A planner model that immediately reports the task as done, counting its calls. */
    static class DonePlannerModel implements ChatModel {

        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            calls.incrementAndGet();
            return ChatResponse.builder().aiMessage(AiMessage.from("""
                            {"agentName": "done", "arguments": {"response": "nothing to do"}}
                            """)).build();
        }

        int calls() {
            return calls.get();
        }
    }

    private static AgenticScopeFunction<String> agent(String name, String description) {
        var builder = AgenticServices.nonAiAgentBuilder(scope -> "done by " + name)
                .name(name)
                .outputKey(name);
        if (description != null) {
            builder.description(description);
        }
        return builder.build();
    }

    @Test
    void supervisor_fails_at_its_first_invocation_when_subagents_have_no_description() {
        DonePlannerModel plannerModel = new DonePlannerModel();
        // building succeeds: the check runs when the supervisor is invoked, like the rest of its initialization
        SupervisorAgent supervisor = AgenticServices.supervisorBuilder()
                .chatModel(plannerModel)
                .responseStrategy(SupervisorResponseStrategy.SUMMARY)
                .subAgents(
                        agent("described", "An agent with a description"), agent("first", null), agent("second", null))
                .build();

        assertThatThrownBy(() -> supervisor.invoke("do something"))
                .isInstanceOf(AgenticSystemConfigurationException.class)
                .hasMessageContaining("[first, second]")
                .hasMessageContaining("have no description");
        assertThat(plannerModel.calls()).isZero();
    }

    @Test
    void supervisor_with_described_subagents_plans_normally() {
        DonePlannerModel plannerModel = new DonePlannerModel();
        SupervisorAgent supervisor = AgenticServices.supervisorBuilder()
                .chatModel(plannerModel)
                .responseStrategy(SupervisorResponseStrategy.SUMMARY)
                .subAgents(agent("described", "An agent with a description"))
                .build();

        assertThat(supervisor.invoke("do something")).isEqualTo("nothing to do");
        assertThat(plannerModel.calls()).isEqualTo(1);
    }
}
