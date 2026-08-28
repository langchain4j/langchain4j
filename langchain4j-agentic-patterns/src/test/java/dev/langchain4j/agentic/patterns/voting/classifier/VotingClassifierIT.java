package dev.langchain4j.agentic.patterns.voting.classifier;

import static dev.langchain4j.agentic.patterns.Models.baseModel;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.patterns.voting.VotingPlanner;
import dev.langchain4j.agentic.patterns.voting.classifier.ClassifierAgents.SentimentClassifier1;
import dev.langchain4j.agentic.patterns.voting.classifier.ClassifierAgents.SentimentClassifier2;
import dev.langchain4j.agentic.patterns.voting.classifier.ClassifierAgents.SentimentClassifier3;
import dev.langchain4j.agentic.patterns.voting.classifier.ClassifierAgents.SentimentVoter;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class VotingClassifierIT {

    @Test
    void voting_classifier_positive_sentiment() {
        SentimentClassifier1 c1 = AgenticServices.agentBuilder(SentimentClassifier1.class)
                .chatModel(baseModel())
                .outputKey("vote1")
                .build();

        SentimentClassifier2 c2 = AgenticServices.agentBuilder(SentimentClassifier2.class)
                .chatModel(baseModel())
                .outputKey("vote2")
                .build();

        SentimentClassifier3 c3 = AgenticServices.agentBuilder(SentimentClassifier3.class)
                .chatModel(baseModel())
                .outputKey("vote3")
                .build();

        SentimentVoter voter = AgenticServices.plannerBuilder(SentimentVoter.class)
                .subAgents(c1, c2, c3)
                .outputKey("classification")
                .planner(VotingPlanner::new)
                .build();

        String result = voter.classify(
                "I absolutely love this product! It exceeded all my expectations and I would recommend it to everyone.");

        assertThat(result).isEqualToIgnoringCase("POSITIVE");
    }
}
