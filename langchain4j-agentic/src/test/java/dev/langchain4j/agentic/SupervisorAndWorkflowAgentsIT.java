package dev.langchain4j.agentic;

import dev.langchain4j.agentic.cognisphere.Cognisphere;
import dev.langchain4j.agentic.cognisphere.DefaultCognisphere;
import dev.langchain4j.agentic.cognisphere.ResultWithCognisphere;
import dev.langchain4j.agentic.internal.AgentInvocation;
import dev.langchain4j.agentic.supervisor.SupervisorAgent;
import dev.langchain4j.agentic.supervisor.SupervisorResponseStrategy;
import dev.langchain4j.service.V;
import org.junit.jupiter.api.Test;
import java.util.List;

import dev.langchain4j.agentic.Agents.CreativeWriter;
import dev.langchain4j.agentic.Agents.StyleEditor;
import dev.langchain4j.agentic.Agents.StyleReviewLoop;
import dev.langchain4j.agentic.Agents.StyleScorer;

import static dev.langchain4j.agentic.Models.baseModel;
import static dev.langchain4j.agentic.Models.plannerModel;
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
                .chatModel(baseModel())
                .outputName("story")
                .build();

        StyleEditor styleEditor = AgenticServices.agentBuilder(StyleEditor.class)
                .chatModel(baseModel())
                .outputName("story")
                .build();

        StyleScorer styleScorer = AgenticServices.agentBuilder(StyleScorer.class)
                .chatModel(baseModel())
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
                    .chatModel(plannerModel())
                    .requestGenerator(cognisphere -> "Write a story about " + cognisphere.readState("topic") + " in the style of a " + cognisphere.readState("style"))
                    .responseStrategy(SupervisorResponseStrategy.LAST)
                    .subAgents(creativeWriter, styleReviewLoop)
                    .maxAgentsInvocations(5)
                    .outputName("story")
                    .build();

            result = styledWriter.write("dragons and wizards", "comedy");

        } else {
            SupervisorAgent styledWriter = AgenticServices.supervisorBuilder()
                    .chatModel(plannerModel())
                    .responseStrategy(SupervisorResponseStrategy.LAST)
                    .subAgents(creativeWriter, styleReviewLoop)
                    .maxAgentsInvocations(5)
                    .outputName("story")
                    .build();

            result = styledWriter.invokeWithCognisphere("Write a story about dragons and wizards in the style of a comedy");
        }

        String story = result.result();
        System.out.println(story);

        DefaultCognisphere cognisphere = (DefaultCognisphere) result.cognisphere();
        assertThat(cognisphere.readState("topic", "")).contains("dragons and wizards");
        assertThat(cognisphere.readState("style", "")).contains("comedy");
        assertThat(story).isEqualTo(cognisphere.readState("story"));
        assertThat(cognisphere.readState("score", 0.0)).isGreaterThanOrEqualTo(0.8);

        assertThat(cognisphere.agentInvocations("generateStory")).hasSize(1);

        List<AgentInvocation> scoreAgentCalls = cognisphere.agentInvocations("scoreStyle");
        assertThat(scoreAgentCalls).hasSizeBetween(1, 5);
        System.out.println("Score agent invocations: " + scoreAgentCalls);
        assertThat((Double) scoreAgentCalls.get(scoreAgentCalls.size() - 1).output()).isGreaterThanOrEqualTo(0.8);
    }
}
