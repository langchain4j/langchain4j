package dev.langchain4j.agentic.patterns.voting.critic;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.observability.HtmlReportGenerator;
import dev.langchain4j.agentic.patterns.voting.critic.CriticAgents.ScoredStory;
import dev.langchain4j.agentic.patterns.voting.critic.DeclarativeCriticAgents.DeclarativeStoryEvaluator;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class DeclarativeVotingCriticIT {

    @Test
    void declarative_sequence_of_writer_and_voting_critics_loop() {
        DeclarativeStoryEvaluator evaluator =
                AgenticServices.createAgenticSystem(DeclarativeStoryEvaluator.class);

        ScoredStory result = evaluator.evaluate("a lonely robot finding friendship");

        assertThat(result.story()).isNotBlank();
        assertThat(result.score()).isBetween(0.0, 10.0);

        System.out.println("Generated story: " + result.story());
        System.out.println("Average score: " + result.score());
        System.out.println("Last suggestions: " + result.suggestions());

//        HtmlReportGenerator.generateReport(evaluator.agentMonitor(),
//                Path.of("src", "test", "resources", "voting-critic-declarative-report.html"));
    }
}
