package dev.langchain4j.agentic.patterns.voting.critic;

import static dev.langchain4j.agentic.patterns.Models.baseModel;
import static dev.langchain4j.agentic.patterns.Models.enhancedModel;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.patterns.voting.VotingPlanner;
import dev.langchain4j.agentic.patterns.voting.VotingStrategy;
import dev.langchain4j.agentic.patterns.voting.critic.CriticAgents.CreativeWriter;
import dev.langchain4j.agentic.patterns.voting.critic.CriticAgents.CritiqueResult;
import dev.langchain4j.agentic.patterns.voting.critic.CriticAgents.EngagementCritic;
import dev.langchain4j.agentic.patterns.voting.critic.CriticAgents.OriginalityCritic;
import dev.langchain4j.agentic.patterns.voting.critic.CriticAgents.ScoredStory;
import dev.langchain4j.agentic.patterns.voting.critic.CriticAgents.StoryEditor;
import dev.langchain4j.agentic.patterns.voting.critic.CriticAgents.StoryEvaluator;
import dev.langchain4j.agentic.patterns.voting.critic.CriticAgents.StyleCritic;
import dev.langchain4j.agentic.observability.HtmlReportGenerator;
import java.nio.file.Path;
import java.util.Collection;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class VotingCriticIT {

    static VotingStrategy critiquesAggregator() {
        return votes -> {
            Collection<CritiqueResult> critiques = votes.stream()
                    .map(v -> (CritiqueResult) v)
                    .toList();

            double averageScore = critiques.stream()
                    .mapToDouble(CritiqueResult::score)
                    .average()
                    .orElse(0.0);

            String allSuggestions = critiques.stream()
                    .map(CritiqueResult::suggestions)
                    .collect(Collectors.joining("; "));

            return new CritiqueResult(averageScore, allSuggestions);
        };
    }

    @Test
    void sequence_of_writer_and_voting_critics_loop() {
        CreativeWriter writer = AgenticServices.agentBuilder(CreativeWriter.class)
                .chatModel(baseModel())
                .outputKey("story")
                .build();

        StyleCritic styleCritic = AgenticServices.agentBuilder(StyleCritic.class)
                .chatModel(baseModel())
                .outputKey("styleCritique")
                .build();

        OriginalityCritic originalityCritic = AgenticServices.agentBuilder(OriginalityCritic.class)
                .chatModel(baseModel())
                .outputKey("originalityCritique")
                .build();

        EngagementCritic engagementCritic = AgenticServices.agentBuilder(EngagementCritic.class)
                .chatModel(baseModel())
                .outputKey("engagementCritique")
                .build();

        Object votingCritics = AgenticServices.plannerBuilder()
                .subAgents(styleCritic, originalityCritic, engagementCritic)
                .outputKey("critique")
                .planner(() -> new VotingPlanner(critiquesAggregator()))
                .name("votingCritics")
                .build();

        StoryEditor storyEditor = AgenticServices.agentBuilder(StoryEditor.class)
                .chatModel(scope -> {
                    CritiqueResult critique = (CritiqueResult) scope.readState("critique");
                    return critique != null && critique.score() > 7.8 ? enhancedModel() : baseModel();
                })
                .outputKey("story")
                .build();

        Object reviewLoop = AgenticServices.loopBuilder()
                .subAgents(votingCritics, storyEditor)
                .outputKey("story")
                .maxIterations(5)
                .exitCondition(scope -> {
                    CritiqueResult critique = (CritiqueResult) scope.readState("critique");
                    System.out.println("critique score = " + critique.score());
                    return critique.score() >= 8.5;
                })
                .name("reviewLoop")
                .build();

        StoryEvaluator evaluator = AgenticServices.sequenceBuilder(StoryEvaluator.class)
                .subAgents(writer, reviewLoop)
                .output(scope -> {
                    CritiqueResult critique = (CritiqueResult) scope.readState("critique");
                    return new ScoredStory(
                            scope.readState("story", ""),
                            critique != null ? critique.score() : 0.0,
                            critique != null ? critique.suggestions() : "");
                })
                .build();

        ScoredStory result = evaluator.evaluate("a lonely robot finding friendship");

        assertThat(result.story()).isNotBlank();
        assertThat(result.score()).isBetween(0.0, 10.0);

        System.out.println("Generated story: " + result.story());
        System.out.println("Average score: " + result.score());
        System.out.println("Last suggestions: " + result.suggestions());

//        HtmlReportGenerator.generateReport(evaluator.agentMonitor(),
//                Path.of("src", "test", "resources", "voting-critic-report.html"));
    }
}
