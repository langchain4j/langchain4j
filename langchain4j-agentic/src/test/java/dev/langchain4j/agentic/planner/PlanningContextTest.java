package dev.langchain4j.agentic.planner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.decision.response.YesNoAnswer;
import org.junit.jupiter.api.Test;

class PlanningContextTest {

    @Test
    void context_created_without_a_planner_agent_ignores_the_reported_model_responses() {
        // how the unit tests of a planner create the context, without an agentic system around it
        PlanningContext planningContext = new PlanningContext(null, null);

        assertThat(planningContext.plannerAgent()).isNull();
        assertThatCode(() -> {
                    planningContext.reportChatModelResponse(ChatResponse.builder()
                            .aiMessage(AiMessage.from("go on"))
                            .build());
                    planningContext.reportDecisionModelResponse(DecisionResponse.builder()
                            .answer("invoke", YesNoAnswer.from(0.9))
                            .build());
                })
                .doesNotThrowAnyException();
    }
}
