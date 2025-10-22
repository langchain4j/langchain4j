package dev.langchain4j.agentic.a2a;

import static dev.langchain4j.agentic.a2a.A2AAgentIT.A2A_SERVER_URL;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.A2AClientAgent;
import dev.langchain4j.agentic.declarative.ExitCondition;
import dev.langchain4j.agentic.declarative.LoopAgent;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.declarative.SubAgent;
import dev.langchain4j.agentic.scope.AgenticScopeAccess;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public class Agents {

    public interface StyleEditor {

        @UserMessage(
                """
                You are a professional editor.
                Analyze and rewrite the following story to better fit and be more coherent with the {{style}} style.
                Return only the story and nothing else.
                The story is "{{story}}".
                """)
        @Agent("Edit a story to better fit a given style")
        String editStory(@V("story") String story, @V("style") String style);
    }

    public interface StyleScorer {

        @UserMessage(
                """
                You are a critical reviewer.
                Give a review score between 0.0 and 1.0 for the following story based on how well it aligns with the style '{{style}}'.
                Return only the score and nothing else.

                The story is: "{{story}}"
                """)
        @Agent("Score a story based on how well it aligns with a given style")
        double scoreStyle(@V("story") String story, @V("style") String style);
    }

    public interface StyleReviewLoop {

        @Agent("Review the given story to ensure it aligns with the specified style")
        String scoreAndReview(@V("story") String story, @V("style") String style);
    }

    public interface StyledWriter extends AgenticScopeAccess {

        @Agent
        ResultWithAgenticScope<String> writeStoryWithStyle(@V("topic") String topic, @V("style") String style);
    }

    public interface DeclarativeA2ACreativeWriter {

        @A2AClientAgent(a2aServerUrl = A2A_SERVER_URL, outputKey = "story")
        String generateStory(@V("topic") String topic);
    }

    public interface StyleReviewLoopAgent {

        @LoopAgent(
                description = "Review and score the given story to ensure it aligns with the specified style",
                outputKey = "story",
                maxIterations = 5,
                subAgents = {
                    @SubAgent(type = StyleScorer.class, outputKey = "score"),
                    @SubAgent(type = StyleEditor.class, outputKey = "story")
                })
        String reviewAndScore(@V("story") String story);

        @ExitCondition
        static boolean exit(@V("score") double score) {
            return score >= 0.8;
        }
    }

    public interface StoryCreatorWithReview {

        @SequenceAgent(
                outputKey = "story",
                subAgents = {
                    @SubAgent(type = DeclarativeA2ACreativeWriter.class, outputKey = "story"),
                    @SubAgent(type = StyleReviewLoopAgent.class, outputKey = "story")
                })
        ResultWithAgenticScope<String> write(@V("topic") String topic, @V("style") String style);
    }
}
