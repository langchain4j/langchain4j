package dev.langchain4j.agentic.supervisor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.AgenticServices.AgenticScopeFunction;
import dev.langchain4j.agentic.planner.AgenticSystemConfigurationException;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import org.junit.jupiter.api.Test;

/**
 * The supervisor sends the description of each subagent to its model to choose the next one, so a subagent without
 * a description makes it fail at its first invocation instead of silently planning over a {@code null}.
 */
class SupervisorSubagentsDescriptionTest {

    /** A planner model that immediately reports the task as done. */
    private static ChatModelMock donePlannerModel() {
        return ChatModelMock.thatAlwaysResponds("""
                {"agentName": "done", "arguments": {"response": "nothing to do"}}
                """);
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
        ChatModelMock plannerModel = donePlannerModel();
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
        assertThat(plannerModel.requests()).isEmpty();
    }

    @Test
    void supervisor_with_described_subagents_plans_normally() {
        ChatModelMock plannerModel = donePlannerModel();
        SupervisorAgent supervisor = AgenticServices.supervisorBuilder()
                .chatModel(plannerModel)
                .responseStrategy(SupervisorResponseStrategy.SUMMARY)
                .subAgents(agent("described", "An agent with a description"))
                .build();

        assertThat(supervisor.invoke("do something")).isEqualTo("nothing to do");
        assertThat(plannerModel.requests()).hasSize(1);
    }
}
