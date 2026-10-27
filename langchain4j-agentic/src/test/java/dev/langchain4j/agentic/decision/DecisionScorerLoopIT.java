package dev.langchain4j.agentic.decision;

import static dev.langchain4j.agentic.Models.baseModel;
import static dev.langchain4j.agentic.decision.DecisionModels.typeSafe;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.Agents.StyleEditor;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.decision.DecisionAgents.Quality;
import dev.langchain4j.agentic.decision.DecisionAgents.StyleScorer;
import dev.langchain4j.agentic.observability.AgentInvocation;
import dev.langchain4j.agentic.observability.AgentMonitor;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.service.decision.Scale;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Scenario 2: a decision agent grades the story on a scale and its mean drives the exit condition of the loop.
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TYPESAFE_API_KEY", matches = ".+")
public class DecisionScorerLoopIT {

    private static final String DULL_STORY =
            "The quarterly report shows that the logistics department reduced its delivery times by four percent. "
                    + "The manager thanked the team and announced that the next review will take place in March.";
    // the mean of the level indexes: at least GOOD, the third of the four levels
    private static final double BAR = 2.0;
    private static final int MAX_ITERATIONS = 4;

    @Test
    void scores_the_story_at_every_iteration_until_it_is_good_enough() {
        StyleEditor editor = AgenticServices.agentBuilder(StyleEditor.class)
                .chatModel(baseModel())
                .outputKey("story")
                .build();
        StyleScorer scorer = AgenticServices.decisionAgentBuilder(StyleScorer.class)
                .decisionModel(typeSafe())
                .build();
        AgentMonitor monitor = new AgentMonitor();
        UntypedAgent loop = AgenticServices.loopBuilder()
                .subAgents(scorer, editor)
                .maxIterations(MAX_ITERATIONS)
                .exitCondition(
                        "the style is at least good",
                        scope -> ((Scale<Quality>) scope.readState("styleScore")).mean() >= BAR)
                .outputKey("story")
                .listener(monitor)
                .build();

        ResultWithAgenticScope<String> result =
                loop.invokeWithAgenticScope(Map.of("story", DULL_STORY, "style", "fairy tale"));

        Scale<Quality> finalScore = (Scale<Quality>) result.agenticScope().readState("styleScore");
        List<AgentInvocation> scores =
                monitor.successfulExecutions().get(0).topLevelInvocations().nestedInvocations().stream()
                        .filter(invocation -> invocation.agent().type() == StyleScorer.class)
                        .toList();
        scores.forEach(score -> System.out.println("iteration " + score.iterationIndex() + ": " + score.output()));

        assertThat(scores).isNotEmpty().hasSizeLessThanOrEqualTo(MAX_ITERATIONS);
        assertThat(finalScore.mean() >= BAR || scores.size() == MAX_ITERATIONS)
                .as("the loop stops when the story is good enough, or after the last iteration")
                .isTrue();
        scores.forEach(score -> {
            assertThat(score.decisionResponse()).isPresent();
            assertThat(score.totalTokenCount()).isPositive();
            assertThat(score.modelName()).isPresent();
        });
        assertThat(result.result()).isNotBlank();
    }
}
