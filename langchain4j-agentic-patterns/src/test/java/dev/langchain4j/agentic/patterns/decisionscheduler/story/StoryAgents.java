package dev.langchain4j.agentic.patterns.decisionscheduler.story;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * A story writer whose editors rewrite the same story: each rewrite makes every editor able to run again, so the
 * editors alternate until the story reaches the goal.
 */
public class StoryAgents {

    public static final String GOAL =
            "The story is about the topic, is written in the requested style and suits the requested audience";

    public interface StoryGenerator {

        @UserMessage("""
                You are a creative writer.
                Write a draft of a story of no more than 3 sentences about the given topic.
                Return only the story and nothing else.
                The topic is {{topic}}.
                """)
        @Agent(
                name = "storyGenerator",
                description = "Writes the first draft of a story about the topic",
                outputKey = "story")
        String generate(@V("topic") String topic);
    }

    public interface StyleEditor {

        @UserMessage("""
                You are a professional editor.
                Rewrite the following story in the {{style}} style, in no more than 3 sentences and without
                changing what happens in it.
                Return only the story and nothing else.
                The story is "{{story}}".
                """)
        @Agent(
                name = "styleEditor",
                description = "Rewrites the story to fit the requested style",
                outputKey = "story")
        String editStyle(@V("story") String story, @V("style") String style);
    }

    public interface AudienceEditor {

        @UserMessage("""
                You are a professional editor.
                Rewrite the following story for an audience of {{audience}}, in no more than 3 sentences and
                without changing its style or what happens in it.
                Return only the story and nothing else.
                The story is "{{story}}".
                """)
        @Agent(
                name = "audienceEditor",
                description = "Rewrites the story to suit the requested audience",
                outputKey = "story")
        String editAudience(@V("story") String story, @V("audience") String audience);
    }

    public interface StoryWriter {

        @Agent
        ResultWithAgenticScope<String> write(
                @V("topic") String topic, @V("style") String style, @V("audience") String audience);
    }
}
