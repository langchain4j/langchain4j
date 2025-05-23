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

import static dev.langchain4j.agentic.Models.BASE_MODEL;
import static dev.langchain4j.agentic.Models.PLANNER_MODEL;
import static org.assertj.core.api.Assertions.assertThat;

public class A2AAgentIT {

    private static final String A2A_SERVER_URL = "http://localhost:8080";

    @Test
    @Disabled("Requires A2A server to be running")
    void a2a_agent_loop_tests() {
        UntypedAgent creativeWriter = AgentServices.a2aBuilder(A2A_SERVER_URL)
                .inputNames("topic")
                .outputName("story")
                .build();

        StyleEditor styleEditor = AgentServices.agentBuilder(StyleEditor.class)
                .chatModel(BASE_MODEL)
                .outputName("story")
                .build();

        StyleScorer styleScorer = AgentServices.agentBuilder(StyleScorer.class)
                .chatModel(BASE_MODEL)
                .outputName("score")
                .build();

        UntypedAgent styleReviewLoop = AgentServices.loopBuilder()
                .subAgents(styleScorer, styleEditor)
                .maxIterations(5)
                .exitCondition( cognisphere -> cognisphere.readState("score", 0.0) >= 0.8)
                .build();

        StyledWriter styledWriter = AgentServices.sequenceBuilder(StyledWriter.class)
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
        A2ACreativeWriter creativeWriter = AgentServices.a2aBuilder(A2A_SERVER_URL, A2ACreativeWriter.class)
                .outputName("story")
                .build();

        StyleEditor styleEditor = AgentServices.agentBuilder(StyleEditor.class)
                .chatModel(BASE_MODEL)
                .outputName("story")
                .build();

        StyleScorer styleScorer = AgentServices.agentBuilder(StyleScorer.class)
                .chatModel(BASE_MODEL)
                .outputName("score")
                .build();

        Agents.StyleReviewLoop styleReviewLoop = AgentServices.loopBuilder(StyleReviewLoop.class)
                .subAgents(styleScorer, styleEditor)
                .outputName("story")
                .maxIterations(5)
                .exitCondition(cognisphere -> cognisphere.readState("score", 0.0) >= 0.8)
                .build();

        SupervisorAgent styledWriter = AgentServices.supervisorBuilder()
                .chatModel(PLANNER_MODEL)
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

        assertThat(cognisphere.getAgentInvocations("Creative Writer")).hasSize(1);

        List<AgentCall> scoreAgentCalls = cognisphere.getAgentInvocations("scoreStyle");
        assertThat(scoreAgentCalls).hasSizeBetween(1, 5);
        System.out.println("Score agent invocations: " + scoreAgentCalls);
        assertThat((Double) scoreAgentCalls.get(scoreAgentCalls.size() - 1).response()).isGreaterThanOrEqualTo(0.8);
    }
}
