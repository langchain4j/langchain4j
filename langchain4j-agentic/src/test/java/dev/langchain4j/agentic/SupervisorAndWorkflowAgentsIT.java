package dev.langchain4j.agentic;

import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
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
        ResultWithAgenticScope<String> write(@V("topic") String topic, @V("style") String style);
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
                .exitCondition(agenticScope -> agenticScope.readState("score", 0.0) >= 0.8)
                .build();

        ResultWithAgenticScope<String> result;

        if (typedSupervisor) {
            SupervisorStyledWriter styledWriter = AgenticServices.supervisorBuilder(SupervisorStyledWriter.class)
                    .chatModel(plannerModel())
                    .requestGenerator(agenticScope -> "Write a story about " + agenticScope.readState("topic") + " in the style of a " + agenticScope.readState("style"))
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

            result = styledWriter.invokeWithAgenticScope("Write a story about dragons and wizards in the style of a comedy");
        }

        String story = result.result();
        System.out.println(story);

        DefaultAgenticScope agenticScope = (DefaultAgenticScope) result.agenticScope();
        assertThat(agenticScope.readState("topic", "")).contains("dragons and wizards");
        assertThat(agenticScope.readState("style", "")).contains("comedy");
        assertThat(story).isEqualTo(agenticScope.readState("story"));
        assertThat(agenticScope.readState("score", 0.0)).isGreaterThanOrEqualTo(0.8);

        assertThat(agenticScope.agentInvocations("generateStory")).hasSize(1);

        List<AgentInvocation> scoreAgentCalls = agenticScope.agentInvocations("scoreStyle");
        assertThat(scoreAgentCalls).hasSizeBetween(1, 5);
        System.out.println("Score agent invocations: " + scoreAgentCalls);
        assertThat((Double) scoreAgentCalls.get(scoreAgentCalls.size() - 1).output()).isGreaterThanOrEqualTo(0.8);
    }
}
