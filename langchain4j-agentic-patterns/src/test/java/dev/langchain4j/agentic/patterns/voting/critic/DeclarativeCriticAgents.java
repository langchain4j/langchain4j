package dev.langchain4j.agentic.patterns.voting.critic;

import static dev.langchain4j.agentic.patterns.Models.baseModel;
import static dev.langchain4j.agentic.patterns.Models.enhancedModel;
import static dev.langchain4j.agentic.patterns.voting.critic.VotingCriticIT.critiquesAggregator;

import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.declarative.ExitCondition;
import dev.langchain4j.agentic.declarative.LoopAgent;
import dev.langchain4j.agentic.declarative.Output;
import dev.langchain4j.agentic.declarative.PlannerAgent;
import dev.langchain4j.agentic.declarative.PlannerSupplier;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.observability.MonitoredAgent;
import dev.langchain4j.agentic.patterns.voting.VotingPlanner;
import dev.langchain4j.agentic.patterns.voting.critic.CriticAgents.CritiqueResult;
import dev.langchain4j.agentic.patterns.voting.critic.CriticAgents.ScoredStory;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.V;

public class DeclarativeCriticAgents {

    public interface DeclarativeCreativeWriter extends CriticAgents.CreativeWriter {

        @ChatModelSupplier
        static ChatModel chatModel() {
            return baseModel();
        }
    }

    public interface DeclarativeStyleCritic extends CriticAgents.StyleCritic {

        @ChatModelSupplier
        static ChatModel chatModel() {
            return baseModel();
        }
    }

    public interface DeclarativeOriginalityCritic extends CriticAgents.OriginalityCritic {

        @ChatModelSupplier
        static ChatModel chatModel() {
            return baseModel();
        }
    }

    public interface DeclarativeEngagementCritic extends CriticAgents.EngagementCritic {

        @ChatModelSupplier
        static ChatModel chatModel() {
            return baseModel();
        }
    }

    public interface DeclarativeStoryEditor extends CriticAgents.StoryEditor {

        @ChatModelSupplier
        static ChatModel chatModel(@V("critique") CritiqueResult critique) {
            return critique != null && critique.score() > 7.8 ? enhancedModel() : baseModel();
        }
    }

    public interface DeclarativeVotingCritics {

        @PlannerAgent(
                name = "votingCritics",
                outputKey = "critique",
                subAgents = {DeclarativeStyleCritic.class, DeclarativeOriginalityCritic.class, DeclarativeEngagementCritic.class})
        CritiqueResult critique(@V("story") String story);

        @PlannerSupplier
        static Planner planner() {
            return new VotingPlanner(critiquesAggregator());
        }
    }

    public interface DeclarativeReviewLoop {

        @LoopAgent(
                name = "reviewLoop",
                outputKey = "story",
                maxIterations = 5,
                subAgents = {DeclarativeVotingCritics.class, DeclarativeStoryEditor.class})
        String review(@V("story") String story);

        @ExitCondition
        static boolean shouldExit(@V("critique") CritiqueResult critique) {
            System.out.println("critique score = " + critique.score());
            return critique.score() >= 8.5;
        }
    }

    public interface DeclarativeStoryEvaluator extends MonitoredAgent {

        @SequenceAgent(
                subAgents = {DeclarativeCreativeWriter.class, DeclarativeReviewLoop.class})
        ScoredStory evaluate(@V("topic") String topic);

        @Output
        static ScoredStory output(@V("story") String story, @V("critique") CritiqueResult critique) {
            return new ScoredStory(
                    story != null ? story : "",
                    critique != null ? critique.score() : 0.0,
                    critique != null ? critique.suggestions() : "");
        }
    }
}
