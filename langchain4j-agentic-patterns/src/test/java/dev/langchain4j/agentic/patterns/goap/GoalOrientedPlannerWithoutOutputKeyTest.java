package dev.langchain4j.agentic.patterns.goap;

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

class GoalOrientedPlannerWithoutOutputKeyTest {

    static final AtomicInteger WRITER_INVOCATIONS = new AtomicInteger();

    static final ChatModel MODEL = new ChatModel() {

        @Override
        public ChatResponse chat(ChatRequest request) {
            WRITER_INVOCATIONS.incrementAndGet();
            return ChatResponse.builder().aiMessage(AiMessage.from("done")).build();
        }
    };

    public interface Writer {

        @Agent(description = "Writes the article", outputKey = "article")
        @UserMessage("Write the article about {{topic}}")
        String write(@V("topic") String topic);
    }

    public interface NoGoal {

        @Agent(description = "Coordinates the writers")
        @UserMessage("Write about {{topic}}")
        String write(@V("topic") String topic);
    }

    public interface WithGoal {

        @Agent(description = "Coordinates the writers", outputKey = "article")
        @UserMessage("Write about {{topic}}")
        String write(@V("topic") String topic);
    }

    @BeforeEach
    void resetInvocations() {
        WRITER_INVOCATIONS.set(0);
    }

    @Test
    void goal_oriented_planner_without_an_output_key_fails_fast() {
        NoGoal noGoal = AgenticServices.plannerBuilder(NoGoal.class)
                .subAgents(writer())
                .planner(GoalOrientedPlanner::new)
                .build();

        assertThatThrownBy(() -> noGoal.write("mars"))
                .isInstanceOf(AgenticSystemConfigurationException.class)
                .hasMessageContaining("needs a goal")
                .hasMessageContaining("no output key");
        assertThat(WRITER_INVOCATIONS).hasValue(0);
    }

    @Test
    void goal_oriented_planner_with_a_blank_output_key_fails_fast() {
        for (String blank : new String[] {"", "   "}) {
            NoGoal noGoal = AgenticServices.plannerBuilder(NoGoal.class)
                    .subAgents(writer())
                    .planner(GoalOrientedPlanner::new)
                    .outputKey(blank)
                    .build();

            assertThatThrownBy(() -> noGoal.write("mars"))
                    .as("output key [%s] must be treated as absent", blank)
                    .isInstanceOf(AgenticSystemConfigurationException.class)
                    .hasMessageContaining("needs a goal");
        }
        assertThat(WRITER_INVOCATIONS).hasValue(0);
    }

    @Test
    void goal_oriented_planner_with_an_output_key_still_plans_towards_it() {
        WithGoal withGoal = AgenticServices.plannerBuilder(WithGoal.class)
                .subAgents(writer())
                .planner(GoalOrientedPlanner::new)
                .build();

        assertThatCode(() -> withGoal.write("mars")).doesNotThrowAnyException();
        assertThat(WRITER_INVOCATIONS).hasPositiveValue();
    }

    private static Writer writer() {
        return AgenticServices.agentBuilder(Writer.class).chatModel(MODEL).build();
    }
}
