package dev.langchain4j.agentic;

import dev.langchain4j.agentic.cognisphere.Cognisphere;
import dev.langchain4j.agentic.cognisphere.ResultWithCognisphere;
import dev.langchain4j.agentic.internal.AgentCall;
import dev.langchain4j.agentic.supervisor.SupervisorAgent;
import dev.langchain4j.agentic.supervisor.SupervisorResponseStrategy;
import dev.langchain4j.service.V;
import org.junit.jupiter.api.Test;
import java.util.List;

import dev.langchain4j.agentic.Agents.CreativeWriter;
import dev.langchain4j.agentic.Agents.StyleEditor;
import dev.langchain4j.agentic.Agents.StyleReviewLoop;
import dev.langchain4j.agentic.Agents.StyleScorer;

import static dev.langchain4j.agentic.Models.BASE_MODEL;
import static dev.langchain4j.agentic.Models.PLANNER_MODEL;
import static org.assertj.core.api.Assertions.assertThat;

public class SupervisorAndWorkflowAgentsIT {

    public interface SupervisorStyledWriter {

        @Agent
        ResultWithCognisphere<String> write(@V("topic") String topic, @V("style") String style);
    }

    @Test
    void supervisor_with_composite_agents_test() {
        supervisor_with_composite_agents(false);
    }

    @Test
    void typed_supervisor_with_composite_agents_test() {
        supervisor_with_composite_agents(true);
    }

    void supervisor_with_composite_agents(boolean typedSupervisor) {
        CreativeWriter creativeWriter = AgenticServices.agentBuilder(CreativeWriter.class)
                .chatModel(BASE_MODEL)
                .outputName("story")
                .build();

        StyleEditor styleEditor = AgenticServices.agentBuilder(StyleEditor.class)
                .chatModel(BASE_MODEL)
                .outputName("story")
                .build();

        StyleScorer styleScorer = AgenticServices.agentBuilder(StyleScorer.class)
                .chatModel(BASE_MODEL)
                .outputName("score")
                .build();

        StyleReviewLoop styleReviewLoop = AgenticServices.loopBuilder(StyleReviewLoop.class)
                .subAgents(styleScorer, styleEditor)
                .outputName("story")
                .maxIterations(5)
                .exitCondition(cognisphere -> cognisphere.readState("score", 0.0) >= 0.8)
                .build();

        ResultWithCognisphere<String> result;

        if (typedSupervisor) {
            SupervisorStyledWriter styledWriter = AgenticServices.supervisorBuilder(SupervisorStyledWriter.class)
                    .chatModel(PLANNER_MODEL)
                    .requestGenerator(cognisphere -> "Write a story about " + cognisphere.readState("topic") + " in the style of a " + cognisphere.readState("style"))
                    .responseStrategy(SupervisorResponseStrategy.LAST)
                    .subAgents(creativeWriter, styleReviewLoop)
                    .maxAgentsInvocations(5)
                    .outputName("story")
                    .build();

            result = styledWriter.write("dragons and wizards", "comedy");

        } else {
            SupervisorAgent styledWriter = AgenticServices.supervisorBuilder()
                    .chatModel(PLANNER_MODEL)
                    .responseStrategy(SupervisorResponseStrategy.LAST)
                    .subAgents(creativeWriter, styleReviewLoop)
                    .maxAgentsInvocations(5)
                    .outputName("story")
                    .build();

            result = styledWriter.invokeWithCognisphere("Write a story about dragons and wizards in the style of a comedy");
        }

        String story = result.result();
        System.out.println(story);

        Cognisphere cognisphere = result.cognisphere();
        assertThat(cognisphere.readState("topic", "")).contains("dragons and wizards");
        assertThat(cognisphere.readState("style", "")).contains("comedy");
        assertThat(story).isEqualTo(cognisphere.readState("story"));
        assertThat(cognisphere.readState("score", 0.0)).isGreaterThanOrEqualTo(0.8);

        assertThat(cognisphere.agentCalls("generateStory")).hasSize(1);

        List<AgentCall> scoreAgentCalls = cognisphere.agentCalls("scoreStyle");
        assertThat(scoreAgentCalls).hasSizeBetween(1, 5);
        System.out.println("Score agent invocations: " + scoreAgentCalls);
        assertThat((Double) scoreAgentCalls.get(scoreAgentCalls.size() - 1).output()).isGreaterThanOrEqualTo(0.8);
    }
}
