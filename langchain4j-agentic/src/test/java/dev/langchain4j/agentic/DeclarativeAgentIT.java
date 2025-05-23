package dev.langchain4j.agentic;

import dev.langchain4j.agentic.declarative.ActivationCondition;
import dev.langchain4j.agentic.declarative.ConditionalAgent;
import dev.langchain4j.agentic.declarative.ExitCondition;
import dev.langchain4j.agentic.declarative.LoopAgent;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.declarative.Subagent;
import dev.langchain4j.service.V;

import dev.langchain4j.agentic.Agents.CreativeWriter;
import dev.langchain4j.agentic.Agents.AudienceEditor;
import dev.langchain4j.agentic.Agents.StyleEditor;
import dev.langchain4j.agentic.Agents.StyleScorer;
import dev.langchain4j.agentic.Agents.CategoryRouter;
import dev.langchain4j.agentic.Agents.MedicalExpert;
import dev.langchain4j.agentic.Agents.TechnicalExpert;
import dev.langchain4j.agentic.Agents.LegalExpert;
import dev.langchain4j.agentic.Agents.RequestCategory;

import org.junit.jupiter.api.Test;

import static dev.langchain4j.agentic.Models.BASE_MODEL;
import static org.assertj.core.api.Assertions.assertThat;

public class DeclarativeAgentIT {

    @SequenceAgent(outputName = "story", subagents = {
            @Subagent(agentClass = CreativeWriter.class, outputName = "story"),
            @Subagent(agentClass = AudienceEditor.class, outputName = "story"),
            @Subagent(agentClass = StyleEditor.class, outputName = "story")
    })
    public interface StoryCreator {

        @Agent
        String write(@V("topic") String topic, @V("style") String style, @V("audience") String audience);
    }

    @Test
    void declarative_sequence_tests() {
        StoryCreator storyCreator = AgentServices.createAgent(StoryCreator.class, BASE_MODEL);

        String story = storyCreator.write("dragons and wizards", "fantasy", "young adults");
        System.out.println(story);
    }

    @LoopAgent(outputName = "story", maxIterations = 5, subagents = {
            @Subagent(agentClass = StyleScorer.class, outputName = "score"),
            @Subagent(agentClass = StyleEditor.class, outputName = "story")
    })
    public interface StyleReviewLoopAgent {

        @Agent
        String write(@V("story") String story);

        @ExitCondition
        static boolean exit(@V("score") double score) {
            return score >= 0.8;
        }
    }

    @SequenceAgent(outputName = "story", subagents = {
            @Subagent(agentClass = CreativeWriter.class, outputName = "story"),
            @Subagent(agentClass = StyleReviewLoopAgent.class, outputName = "story")
    })
    public interface StoryCreatorWithReview {

        @Agent
        String write(@V("topic") String topic, @V("style") String style);
    }

    @Test
    void declarative_sequence_and_loop_tests() {
        StoryCreatorWithReview storyCreator = AgentServices.createAgent(StoryCreatorWithReview.class, BASE_MODEL);

        String story = storyCreator.write("dragons and wizards", "comedy");
        System.out.println(story);

        Cognisphere cognisphere = ((CognisphereOwner) storyCreator).cognisphere();
        assertThat(cognisphere.readState("topic")).isEqualTo("dragons and wizards");
        assertThat(cognisphere.readState("style")).isEqualTo("comedy");
        assertThat(story).isEqualTo(cognisphere.readState("story"));
        assertThat(cognisphere.readState("score", 0.0)).isGreaterThanOrEqualTo(0.8);
    }

    @ConditionalAgent(outputName = "response", subagents = {
            @Subagent(agentName = "medical", agentClass = MedicalExpert.class, outputName = "response"),
            @Subagent(agentName = "technical", agentClass = TechnicalExpert.class, outputName = "response"),
            @Subagent(agentName = "legal", agentClass = LegalExpert.class, outputName = "response")
    })
    public interface ExpertsAgent {

        @Agent
        String askExpert(@V("request") String request);

        @ActivationCondition("medical")
        static boolean activateMedical(@V("category") RequestCategory category) {
            return category == RequestCategory.MEDICAL;
        }

        @ActivationCondition("technical")
        static boolean activateTechnical(@V("category") RequestCategory category) {
            return category == RequestCategory.TECHNICAL;
        }

        @ActivationCondition("legal")
        static boolean activateLegal(@V("category") RequestCategory category) {
            return category == RequestCategory.LEGAL;
        }
    }

    @SequenceAgent(outputName = "response", subagents = {
            @Subagent(agentClass = CategoryRouter.class, outputName = "category"),
            @Subagent(agentClass = ExpertsAgent.class, outputName = "response")
    })
    public interface ExpertRouterAgent {

        @Agent
        String ask(@V("request") String request);
    }

    @Test
    void declarative_conditional_tests() {
        ExpertRouterAgent expertRouterAgent = AgentServices.createAgent(ExpertRouterAgent.class, BASE_MODEL);

        String response = expertRouterAgent.ask("I broke my leg what should I do");
        System.out.println(response);

        Cognisphere cognisphere = ((CognisphereOwner) expertRouterAgent).cognisphere();
        assertThat(cognisphere.readState("category")).isEqualTo(RequestCategory.MEDICAL);
    }

}
