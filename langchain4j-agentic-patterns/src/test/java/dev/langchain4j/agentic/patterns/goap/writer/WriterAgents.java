package dev.langchain4j.agentic.patterns.goap.writer;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public class WriterAgents {

    public interface StoryGenerator {

        @UserMessage("""
                You are a creative writer.
                Generate a draft of a story long no more than 3 sentence around the given topic.
                Return only the story and nothing else.
                The topic is {{topic}}.
                """)
        @Agent("Generate a story based on the given topic")
        String generateStory(@V("topic") String topic);
    }

    public interface AudienceEditor {

        @UserMessage("""
            You are a professional editor.
            Analyze and rewrite the following story to better align with the target audience of {{audience}}.
            Return only the story and nothing else.
            The story is "{{styledStory}}".
            """)
        @Agent("Edit a story to better fit a given audience")
        String editStoryForAudience(@V("styledStory") String styledStory, @V("audience") String audience);
    }

    public interface StyleEditor {

        @UserMessage("""
                You are a professional editor.
                Analyze and rewrite the following story to better fit and be more coherent with the {{style}} style.
                Return only the story and nothing else.
                The story is "{{story}}".
                """)
        @Agent("Edit a story to better fit a given style")
        String editStoryForStyle(@V("story") String story, @V("style") String style);
    }

    public interface StyleScorer {

        @UserMessage("""
                You are a critical reviewer.
                Give a review score between 0.0 and 1.0 for the following story based on how well it aligns with the style '{{style}}'.
                Return only the score and nothing else.
                
                The story is: "{{styledStory}}"
                """)
        @Agent("Score a story based on how well it aligns with a given style")
        double scoreStyle(@V("styledStory") String styledStory, @V("style") String style);
    }

    public interface StyleReviewLoop {

        @Agent("Review the given story to ensure it aligns with the specified style")
        String reviewStyleAndScore(@V("story") String story, @V("style") String style);
    }

    public interface Writer {

        @Agent
        ResultWithAgenticScope<String> write(@V("topic") String topic, @V("style") String style, @V("audience") String audience);
    }
}
