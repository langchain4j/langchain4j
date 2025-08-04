package dev.langchain4j.agentic;

import dev.langchain4j.agentic.cognisphere.Cognisphere;
import dev.langchain4j.agentic.cognisphere.ResultWithCognisphere;
import dev.langchain4j.agentic.internal.AgentCall;
import dev.langchain4j.agentic.supervisor.SupervisorAgent;
import dev.langchain4j.service.V;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import java.util.List;

import dev.langchain4j.agentic.Agents.StyleEditor;
import dev.langchain4j.agentic.Agents.StyleScorer;
import dev.langchain4j.agentic.Agents.StyleReviewLoop;
import dev.langchain4j.agentic.Agents.StyledWriter;

import static dev.langchain4j.agentic.Models.baseModel;
import static dev.langchain4j.agentic.Models.plannerModel;
import static org.assertj.core.api.Assertions.assertThat;

public class A2AAgentIT {

    private static final String A2A_SERVER_URL = "http://localhost:8080";

    @Test
    @Disabled("Requires A2A server to be running")
    void a2a_agent_loop_tests() {
        UntypedAgent creativeWriter = AgenticServices.a2aBuilder(A2A_SERVER_URL)
                .inputNames("topic")
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

        UntypedAgent styleReviewLoop = AgenticServices.loopBuilder()
                .subAgents(styleScorer, styleEditor)
                .maxIterations(5)
                .exitCondition( cognisphere -> cognisphere.readState("score", 0.0) >= 0.8)
                .build();

        StyledWriter styledWriter = AgenticServices.sequenceBuilder(StyledWriter.class)
                .subAgents(creativeWriter, styleReviewLoop)
                .outputName("story")
                .build();

        ResultWithCognisphere<String> result = styledWriter.writeStoryWithStyle("dragons and wizards", "comedy");
        String story = result.result();
        System.out.println(story);

        Cognisphere cognisphere = result.cognisphere();
        assertThat(story).isEqualTo(cognisphere.readState("story"));
        assertThat(cognisphere.readState("score", 0.0)).isGreaterThanOrEqualTo(0.8);
    }

    public interface A2ACreativeWriter {

        @Agent
        String generateStory(@V("topic") String topic);
    }

    @Test
    @Disabled("Requires A2A server to be running")
    void a2a_agent_supervisor_tests() {
        A2ACreativeWriter creativeWriter = AgenticServices.a2aBuilder(A2A_SERVER_URL, A2ACreativeWriter.class)
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

        Agents.StyleReviewLoop styleReviewLoop = AgenticServices.loopBuilder(StyleReviewLoop.class)
                .subAgents(styleScorer, styleEditor)
                .outputName("story")
                .maxIterations(5)
                .exitCondition(cognisphere -> cognisphere.readState("score", 0.0) >= 0.8)
                .build();

        SupervisorAgent styledWriter = AgenticServices.supervisorBuilder()
                .chatModel(plannerModel())
                .subAgents(creativeWriter, styleReviewLoop)
                .maxAgentsInvocations(5)
                .outputName("story")
                .build();

        ResultWithCognisphere<String> result = styledWriter.invokeWithCognisphere("Write a story about dragons and wizards in the style of a comedy");
        String story = result.result();
        System.out.println(story);

        Cognisphere cognisphere = result.cognisphere();
        assertThat(cognisphere.readState("topic")).isEqualTo("dragons and wizards");
        assertThat(cognisphere.readState("style")).isEqualTo("comedy");
        assertThat(story).isEqualTo(cognisphere.readState("story"));
        assertThat(cognisphere.readState("score", 0.0)).isGreaterThanOrEqualTo(0.8);

        assertThat(cognisphere.agentCalls("Creative Writer")).hasSize(1);

        List<AgentCall> scoreAgentCalls = cognisphere.agentCalls("scoreStyle");
        assertThat(scoreAgentCalls).hasSizeBetween(1, 5);
        System.out.println("Score agent invocations: " + scoreAgentCalls);
        assertThat((Double) scoreAgentCalls.get(scoreAgentCalls.size() - 1).output()).isGreaterThanOrEqualTo(0.8);
    }
}
