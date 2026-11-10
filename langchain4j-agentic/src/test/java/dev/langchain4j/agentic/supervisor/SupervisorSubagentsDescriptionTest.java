package dev.langchain4j.agentic.supervisor;

import static dev.langchain4j.agentic.supervisor.SupervisorPlanner.MISSING_DESCRIPTION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.AgenticServices.AgenticScopeFunction;
import dev.langchain4j.agentic.agent.AgentUnavailableException;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.InitPlanningContext;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.test.mock.ChatModelMock;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/**
 * The supervisor sends the description of each subagent to its model to choose the next one. A subagent without a
 * description does not stop it: the model is explicitly told that the description is missing, and a warning naming
 * such subagents is logged once per supervisor. A subagent whose description cannot be read because the agent is
 * unavailable is left out of the invocation, with a warning, while any other failure of a description propagates.
 */
class SupervisorSubagentsDescriptionTest {

    /** A planner model that immediately reports the task as done. */
    private static ChatModelMock donePlannerModel() {
        return ChatModelMock.thatAlwaysResponds("""
                {"agentName": "done", "arguments": {"response": "nothing to do"}}
                """);
    }

    private static String systemMessage(ChatModelMock plannerModel) {
        return plannerModel.requests().get(0).messages().stream()
                .filter(SystemMessage.class::isInstance)
                .map(message -> ((SystemMessage) message).text())
                .findFirst()
                .orElseThrow();
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
    void supervisor_tells_its_model_explicitly_that_a_subagent_has_no_description() {
        ChatModelMock plannerModel = donePlannerModel();
        SupervisorAgent supervisor = AgenticServices.supervisorBuilder()
                .chatModel(plannerModel)
                .responseStrategy(SupervisorResponseStrategy.SUMMARY)
                .subAgents(
                        agent("described", "An agent with a description"), agent("first", null), agent("second", null))
                .build();

        assertThat(supervisor.invoke("do something")).isEqualTo("nothing to do");
        assertThat(plannerModel.requests()).hasSize(1);
        assertThat(systemMessage(plannerModel))
                .contains("'described$0', 'An agent with a description'")
                .contains("'first$1', '" + MISSING_DESCRIPTION + "'")
                .contains("'second$2', '" + MISSING_DESCRIPTION + "'");
    }

    @Test
    void the_descriptions_are_checked_once_per_supervisor() {
        AgentInstance supervisorAgent = mock(AgentInstance.class);
        when(supervisorAgent.name()).thenReturn("supervisor");
        AgentInstance subagent = mock(AgentInstance.class);
        when(subagent.name()).thenReturn("first");
        when(subagent.agentId()).thenReturn("first$0");
        InitPlanningContext context =
                new InitPlanningContext(mock(AgenticScope.class), supervisorAgent, List.of(subagent));

        // the flag is shared by the planners created for the invocations of the same supervisor
        AtomicBoolean subagentsDescriptionsChecked = new AtomicBoolean();
        planner(subagentsDescriptionsChecked).init(context);
        verify(subagent, atLeastOnce()).description();
        assertThat(subagentsDescriptionsChecked).isTrue();

        // the following initializations do not read the descriptions again, so the warning is logged once
        clearInvocations(subagent);
        planner(subagentsDescriptionsChecked).init(context);
        verify(subagent, never()).description();
    }

    @Test
    void a_subagent_whose_description_cannot_be_read_does_not_fail_the_initialization() {
        AgentInstance supervisorAgent = mock(AgentInstance.class);
        when(supervisorAgent.name()).thenReturn("supervisor");
        AgentInstance unreachable = mock(AgentInstance.class);
        when(unreachable.name()).thenReturn("remote");
        when(unreachable.agentId()).thenReturn("remote$0");
        when(unreachable.description())
                .thenThrow(new AgentUnavailableException(
                        "remote", "Failed to fetch the agent card", new IllegalStateException("connection refused")));
        InitPlanningContext context =
                new InitPlanningContext(mock(AgenticScope.class), supervisorAgent, List.of(unreachable));

        // the subagent is left out of the invocation with a warning, instead of failing the whole supervisor
        assertThatCode(() -> planner(new AtomicBoolean()).init(context)).doesNotThrowAnyException();
    }

    @Test
    void any_other_failure_of_the_description_of_a_subagent_fails_the_initialization() {
        AgentInstance supervisorAgent = mock(AgentInstance.class);
        when(supervisorAgent.name()).thenReturn("supervisor");
        AgentInstance misconfigured = mock(AgentInstance.class);
        when(misconfigured.name()).thenReturn("remote");
        when(misconfigured.agentId()).thenReturn("remote$0");
        when(misconfigured.description()).thenThrow(new IllegalStateException("Tool 'writr' not found"));
        InitPlanningContext context =
                new InitPlanningContext(mock(AgenticScope.class), supervisorAgent, List.of(misconfigured));

        // a configuration error is not an unavailable agent: hiding it behind a warning would leave it unnoticed
        assertThatThrownBy(() -> planner(new AtomicBoolean()).init(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'writr' not found");
    }

    private static SupervisorPlanner planner(AtomicBoolean subagentsDescriptionsChecked) {
        return new SupervisorPlanner(
                mock(ChatModel.class),
                null,
                5,
                SupervisorContextStrategy.CHAT_MEMORY,
                SupervisorResponseStrategy.LAST,
                null,
                "output",
                null,
                null,
                subagentsDescriptionsChecked);
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
