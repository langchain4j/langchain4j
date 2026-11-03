package dev.langchain4j.agentic.supervisor;

import static dev.langchain4j.agentic.supervisor.SupervisorPlanner.MISSING_DESCRIPTION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.AgenticServices.AgenticScopeFunction;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.InitPlanningContext;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/**
 * The supervisor sends the description of each subagent to its model to choose the next one. A subagent without a
 * description does not stop it: the model is explicitly told that the description is missing, and a warning naming
 * such subagents is logged once per supervisor.
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
        planner(subagentsDescriptionsChecked).init(context);

        // only the first initialization reads the descriptions and logs the warning about the missing one
        verify(subagent, times(1)).description();
        assertThat(subagentsDescriptionsChecked).isTrue();
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
