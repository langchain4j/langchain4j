package dev.langchain4j.agentic.a2a;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.agentic.internal.AgentInvocation;
import dev.langchain4j.agentic.supervisor.SupervisorAgent;
import dev.langchain4j.service.V;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import java.util.List;

import dev.langchain4j.agentic.a2a.Agents.StyleEditor;
import dev.langchain4j.agentic.a2a.Agents.StyleScorer;
import dev.langchain4j.agentic.a2a.Agents.StyleReviewLoop;
import dev.langchain4j.agentic.a2a.Agents.StyledWriter;

import static dev.langchain4j.agentic.a2a.Models.baseModel;
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
                .exitCondition( agenticScope -> agenticScope.readState("score", 0.0) >= 0.8)
                .build();

        StyledWriter styledWriter = AgenticServices.sequenceBuilder(StyledWriter.class)
                .subAgents(creativeWriter, styleReviewLoop)
                .outputName("story")
                .build();

        ResultWithAgenticScope<String> result = styledWriter.writeStoryWithStyle("dragons and wizards", "comedy");
        String story = result.result();
        System.out.println(story);

        AgenticScope agenticScope = result.agenticScope();
        assertThat(story).isEqualTo(agenticScope.readState("story"));
        assertThat(agenticScope.readState("score", 0.0)).isGreaterThanOrEqualTo(0.8);
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
                .chatModel(Models.baseModel())
                .outputName("story")
                .build();

        StyleScorer styleScorer = AgenticServices.agentBuilder(StyleScorer.class)
                .chatModel(Models.baseModel())
                .outputName("score")
                .build();

        Agents.StyleReviewLoop styleReviewLoop = AgenticServices.loopBuilder(StyleReviewLoop.class)
                .subAgents(styleScorer, styleEditor)
                .outputName("story")
                .maxIterations(5)
                .exitCondition(agenticScope -> agenticScope.readState("score", 0.0) >= 0.8)
                .build();

        SupervisorAgent styledWriter = AgenticServices.supervisorBuilder()
                .chatModel(Models.plannerModel())
                .subAgents(creativeWriter, styleReviewLoop)
                .maxAgentsInvocations(5)
                .outputName("story")
                .build();

        ResultWithAgenticScope<String> result = styledWriter.invokeWithAgenticScope("Write a story about dragons and wizards in the style of a comedy");
        String story = result.result();
        System.out.println(story);

        DefaultAgenticScope agenticScope = (DefaultAgenticScope) result.agenticScope();
        assertThat(agenticScope.readState("topic", "")).contains("dragons and wizards");
        assertThat(agenticScope.readState("style", "")).contains("comedy");
        assertThat(story).isEqualTo(agenticScope.readState("story"));
        assertThat(agenticScope.readState("score", 0.0)).isGreaterThanOrEqualTo(0.8);

        assertThat(agenticScope.agentInvocations("Creative Writer")).hasSize(1);

        List<AgentInvocation> scoreAgentCalls = agenticScope.agentInvocations("scoreStyle");
        assertThat(scoreAgentCalls).hasSizeBetween(1, 5);
        System.out.println("Score agent invocations: " + scoreAgentCalls);
        assertThat((Double) scoreAgentCalls.get(scoreAgentCalls.size() - 1).output()).isGreaterThanOrEqualTo(0.8);
    }
}
