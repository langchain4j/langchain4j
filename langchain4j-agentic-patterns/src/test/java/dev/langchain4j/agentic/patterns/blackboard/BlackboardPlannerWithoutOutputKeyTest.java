package dev.langchain4j.agentic.patterns.blackboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.planner.AgenticSystemConfigurationException;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BlackboardPlannerWithoutOutputKeyTest {

    static final AtomicInteger WORKER_INVOCATIONS = new AtomicInteger();

    static final ChatModel MODEL = new ChatModel() {

        @Override
        public ChatResponse chat(ChatRequest request) {
            WORKER_INVOCATIONS.incrementAndGet();
            return ChatResponse.builder().aiMessage(AiMessage.from("done")).build();
        }
    };

    public interface Worker {

        @Agent(description = "Does the work", outputKey = "work")
        @UserMessage("Do the work")
        String work();
    }

    public interface Board {

        @Agent(description = "Coordinates the workers")
        @UserMessage("Solve {{question}}")
        String solve(@V("question") String question);
    }

    @BeforeEach
    void resetInvocations() {
        WORKER_INVOCATIONS.set(0);
    }

    @Test
    void blackboard_planner_without_a_goal_predicate_or_output_key_fails_fast() {
        Board board = AgenticServices.plannerBuilder(Board.class)
                .subAgents(worker())
                .planner(BlackboardPlanner::new)
                .build();

        assertThatThrownBy(() -> board.solve("hello"))
                .isInstanceOf(AgenticSystemConfigurationException.class)
                .hasMessageContaining("needs a goal")
                .hasMessageContaining("no output key");
        assertThat(WORKER_INVOCATIONS).hasValue(0);
    }

    @Test
    void blackboard_planner_with_a_blank_output_key_fails_fast() {
        for (String blank : new String[] {"", "   "}) {
            Board board = AgenticServices.plannerBuilder(Board.class)
                    .subAgents(worker())
                    .planner(BlackboardPlanner::new)
                    .outputKey(blank)
                    .build();

            assertThatThrownBy(() -> board.solve("hello"))
                    .as("output key [%s] must be treated as absent", blank)
                    .isInstanceOf(AgenticSystemConfigurationException.class)
                    .hasMessageContaining("needs a goal");
        }
        assertThat(WORKER_INVOCATIONS).hasValue(0);
    }

    @Test
    void blackboard_planner_without_a_goal_predicate_still_uses_the_output_key_when_there_is_one() {
        Board board = AgenticServices.plannerBuilder(Board.class)
                .subAgents(worker())
                .planner(BlackboardPlanner::new)
                .outputKey("goal")
                .build();

        assertThatCode(() -> board.solve("hello")).doesNotThrowAnyException();
        assertThat(WORKER_INVOCATIONS).hasPositiveValue();
    }

    @Test
    void blackboard_planner_without_an_output_key_still_works_with_an_explicit_goal_predicate() {
        Board board = AgenticServices.plannerBuilder(Board.class)
                .subAgents(worker())
                .planner(() -> new BlackboardPlanner(scope -> scope.hasState("work")))
                .build();

        assertThatCode(() -> board.solve("hello")).doesNotThrowAnyException();
        assertThat(WORKER_INVOCATIONS).hasPositiveValue();
    }

    private static Worker worker() {
        return AgenticServices.agentBuilder(Worker.class).chatModel(MODEL).build();
    }
}
