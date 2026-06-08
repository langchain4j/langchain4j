package dev.langchain4j.agentic.patterns.voting.critic;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.observability.MonitoredAgent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public class CriticAgents {

    public record CritiqueResult(double score, String suggestions) {}

    public record ScoredStory(String story, double score, String suggestions) {}

    public interface CreativeWriter {

        @UserMessage("""
                You are a creative writer.
                Generate a short story of no more than 3 sentences around the given topic.
                Return only the story and nothing else.
                The topic is: {{topic}}
                """)
        @Agent(value = "Generate a short story based on the given topic", outputKey = "story")
        String generateStory(@V("topic") String topic);
    }

    public interface StyleCritic {

        @UserMessage("""
                You are a literary style critic.
                Evaluate the writing style of the following story.
                Consider prose quality, word choice, and narrative flow.
                Return a JSON object with two fields:
                - "score": a numeric value from 0.0 to 10.0
                - "suggestions": one or two very short suggestions to improve style
                The story is: "{{story}}"
                """)
        @Agent(value = "Evaluate the writing style of a story", outputKey = "styleCritique")
        CritiqueResult critique(@V("story") String story);
    }

    public interface OriginalityCritic {

        @UserMessage("""
                You are an originality critic.
                Evaluate how creative and original the following story is.
                Consider uniqueness of the concept, unexpected twists, and imaginative elements.
                Return a JSON object with two fields:
                - "score": a numeric value from 0.0 to 10.0
                - "suggestions": one or two very short suggestions to improve originality
                The story is: "{{story}}"
                """)
        @Agent(value = "Evaluate the originality of a story", outputKey = "originalityCritique")
        CritiqueResult critique(@V("story") String story);
    }

    public interface EngagementCritic {

        @UserMessage("""
                You are a reader engagement critic.
                Evaluate how engaging and captivating the following story is.
                Consider whether it hooks the reader, creates tension, and has a satisfying arc.
                Return a JSON object with two fields:
                - "score": a numeric value from 0.0 to 10.0
                - "suggestions": one or two very short suggestions to improve engagement
                The story is: "{{story}}"
                """)
        @Agent(value = "Evaluate how engaging a story is", outputKey = "engagementCritique")
        CritiqueResult critique(@V("story") String story);
    }

    public interface StoryEditor {

        @UserMessage("""
                You are a professional story editor.
                Rewrite and improve the following story based on the provided critique.
                Keep the story to no more than 3 sentences.
                Return only the improved story and nothing else.
                The story is: "{{story}}"
                The critique is: {{critique}}
                """)
        @Agent(value = "Improve a story based on critique suggestions", outputKey = "story")
        String edit(@V("story") String story, @V("critique") CritiqueResult critique);
    }

    public interface StoryEvaluator extends MonitoredAgent {

        @Agent
        ScoredStory evaluate(@V("topic") String topic);
    }
}
