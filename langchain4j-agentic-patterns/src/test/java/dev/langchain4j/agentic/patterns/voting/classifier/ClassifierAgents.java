package dev.langchain4j.agentic.patterns.voting.classifier;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public class ClassifierAgents {

    public interface SentimentClassifier1 {

        @UserMessage("""
                Classify the sentiment of the following text.
                Reply with exactly one word: POSITIVE, NEGATIVE, or NEUTRAL.
                The text is: "{{text}}"
                """)
        @Agent("Classify the sentiment of a given text")
        String classify(@V("text") String text);
    }

    public interface SentimentClassifier2 {

        @UserMessage("""
                You are a sentiment analysis expert.
                Analyze the emotional tone of the following text and classify it.
                Reply with exactly one word: POSITIVE, NEGATIVE, or NEUTRAL.
                The text is: "{{text}}"
                """)
        @Agent("Analyze the emotional tone of a given text")
        String classify(@V("text") String text);
    }

    public interface SentimentClassifier3 {

        @UserMessage("""
                You are a customer feedback analyst.
                Determine whether the following feedback is positive, negative, or neutral.
                Reply with exactly one word: POSITIVE, NEGATIVE, or NEUTRAL.
                The text is: "{{text}}"
                """)
        @Agent("Determine the sentiment of customer feedback")
        String classify(@V("text") String text);
    }

    public interface SentimentVoter {

        @Agent
        String classify(@V("text") String text);
    }
}
