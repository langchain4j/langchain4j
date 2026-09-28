package dev.langchain4j.agentic.supervisor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;

import dev.langchain4j.agentic.planner.Action;
import dev.langchain4j.agentic.planner.InitPlanningContext;
import dev.langchain4j.agentic.planner.PlanningContext;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.IllegalConfigurationException;
import java.util.List;
import org.junit.jupiter.api.Test;

class SupervisorPlannerTest {

    @Test
    void summarization_strategies_should_require_a_context_summarizer() {
        assertThatExceptionOfType(IllegalConfigurationException.class)
                .isThrownBy(() -> new SupervisorPlanner(
                        mock(ChatModel.class),
                        null,
                        5,
                        SupervisorContextStrategy.SUMMARIZATION,
                        SupervisorResponseStrategy.SUMMARY,
                        null,
                        "output",
                        null,
                        null))
                .withMessage("A ContextSummarizer is required for the SUMMARIZATION context strategy.");
    }

    @Test
    void chat_memory_strategy_should_not_require_a_context_summarizer() {
        SupervisorPlanner planner = new SupervisorPlanner(
                mock(ChatModel.class),
                null,
                5,
                SupervisorContextStrategy.CHAT_MEMORY,
                SupervisorResponseStrategy.SUMMARY,
                null,
                "output",
                null,
                null);

        assertThat(planner).isNotNull();
    }

    @Test
    void planning_response_without_agent_name_should_fail_with_a_descriptive_error() {
        SupervisorPlanner planner = new SupervisorPlanner(
                fixedResponseModel("{\"arguments\": {}}"),
                null,
                5,
                SupervisorContextStrategy.CHAT_MEMORY,
                SupervisorResponseStrategy.SUMMARY,
                null,
                null,
                null,
                null);
        AgenticScope agenticScope = DefaultAgenticScope.ephemeralAgenticScope();
        planner.init(new InitPlanningContext(agenticScope, null, List.of()));

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> planner.nextAction(new PlanningContext(agenticScope, null)))
                .withMessageContaining("no agent name specified");
    }

    @Test
    void planning_response_without_arguments_should_still_complete_the_plan() {
        SupervisorPlanner planner = new SupervisorPlanner(
                fixedResponseModel("{\"agentName\": \"done\"}"),
                null,
                5,
                SupervisorContextStrategy.CHAT_MEMORY,
                SupervisorResponseStrategy.SUMMARY,
                null,
                null,
                null,
                null);
        AgenticScope agenticScope = DefaultAgenticScope.ephemeralAgenticScope();
        planner.init(new InitPlanningContext(agenticScope, null, List.of()));

        Action action = planner.nextAction(new PlanningContext(agenticScope, null));

        assertThat(action).isNotNull();
    }

    private static ChatModel fixedResponseModel(String planningResponse) {
        return new ChatModel() {
            @Override
            public ChatResponse chat(ChatRequest chatRequest) {
                return ChatResponse.builder().aiMessage(AiMessage.from(planningResponse)).build();
            }
        };
    }
}
