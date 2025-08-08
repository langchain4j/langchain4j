package dev.langchain4j.agentic;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agentic.scope.AgenticScopeAccess;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import java.util.List;

public class Agents {

    public interface ExpertRouterAgent {

        @Agent
        String ask(@V("request") String request);
    }

    public interface ExpertRouterAgentWithMemory extends AgenticScopeAccess {

        @Agent
        String ask(@MemoryId String memoryId, @V("request") String request);
    }

    public interface CategoryRouter {

        @UserMessage("""
            Analyze the following user request and categorize it as 'legal', 'medical' or 'technical'.
            In case the request doesn't belong to any of those categories categorize it as 'unknown'.
            Reply with only one of those words and nothing else.
            The user request is: '{{request}}'.
            """)
        @Agent("Categorize a user request")
        RequestCategory classify(@V("request") String request);
    }

    public enum RequestCategory {
        LEGAL, MEDICAL, TECHNICAL, UNKNOWN
    }

    public interface RouterAgent {

        @UserMessage("""
            Analyze the following user request and categorize it as 'legal', 'medical' or 'technical',
            then forward the request as it is to the corresponding expert provided as a tool.
            Finally return the answer that you received from the expert without any modification.

            The user request is: '{{it}}'.
            """)
        @Agent
        String askToExpert(String request);
    }

    public interface MedicalExpert {

        @UserMessage("""
            You are a medical expert.
            Analyze the following user request under a medical point of view and provide the best possible answer.
            The user request is {{request}}.
            """)
        @Tool("A medical expert")
        @Agent("A medical expert")
        String medical(@V("request") String request);
    }

    public interface MedicalExpertWithMemory {

        @UserMessage("""
            You are a medical expert.
            Analyze the following user request under a medical point of view and provide the best possible answer.
            The user request is {{request}}.
            """)
        @Tool("A medical expert")
        @Agent("A medical expert")
        String medical(@MemoryId String memoryId, @V("request") String request);
    }

    public interface LegalExpert {

        @UserMessage("""
            You are a legal expert.
            Analyze the following user request under a legal point of view and provide the best possible answer.
            The user request is {{request}}.
            """)
        @Tool("A legal expert")
        @Agent("A legal expert")
        String legal(@V("request") String request);
    }

    public interface LegalExpertWithMemory {

        @UserMessage("""
            You are a legal expert.
            Analyze the following user request under a legal point of view and provide the best possible answer.
            The user request is {{request}}.
            """)
        @Tool("A legal expert")
        @Agent("A legal expert")
        String legal(@MemoryId String memoryId, @V("request") String request);
    }

    public interface TechnicalExpert {

        @UserMessage("""
            You are a technical expert.
            Analyze the following user request under a technical point of view and provide the best possible answer.
            The user request is {{request}}.
            """)
        @Tool("A technical expert")
        @Agent("A technical expert")
        String technical(@V("request") String request);
    }

    public interface TechnicalExpertWithMemory {

        @UserMessage("""
            You are a technical expert.
            Analyze the following user request under a technical point of view and provide the best possible answer.
            The user request is {{request}}.
            """)
        @Tool("A technical expert")
        @Agent("A technical expert")
        String technical(@MemoryId String memoryId, @V("request") String request);
    }

    public interface CreativeWriter {

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
            The story is "{{story}}".
            """)
        @Agent("Edit a story to better fit a given audience")
        String editStory(@V("story") String story, @V("audience") String audience);
    }

    public interface StyleEditor {

        @UserMessage("""
                You are a professional editor.
                Analyze and rewrite the following story to better fit and be more coherent with the {{style}} style.
                Return only the story and nothing else.
                The story is "{{story}}".
                """)
        @Agent("Edit a story to better fit a given style")
        String editStory(@V("story") String story, @V("style") String style);
    }

    public interface StyleScorer {

        @UserMessage("""
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

    public interface FoodExpert {

        @UserMessage("""
            You are a great evening planner.
            Propose a list of 3 meals matching the given mood.
            The mood is {{mood}}.
            For each meal, just give the name of the meal.
            Provide a list with the 3 items and nothing else.
            """)
        @Agent
        List<String> findMeal(@V("mood") String mood);
    }

    public interface MovieExpert {

        @UserMessage("""
            You are a great evening planner.
            Propose a list of 3 movies matching the given mood.
            The mood is {{mood}}.
            Provide a list with the 3 items and nothing else.
            """)
        @Agent
        List<String> findMovie(@V("mood") String mood);
    }

    public record EveningPlan(String movie, String meal) { }

    public interface EveningPlannerAgent {

        @Agent
        List<EveningPlan> plan(@V("mood") String mood);
    }
}
