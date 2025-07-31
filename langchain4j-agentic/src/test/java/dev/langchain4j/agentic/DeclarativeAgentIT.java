package dev.langchain4j.agentic;

import dev.langchain4j.agentic.cognisphere.Cognisphere;
import dev.langchain4j.agentic.cognisphere.ResultWithCognisphere;
import dev.langchain4j.agentic.declarative.ActivationCondition;
import dev.langchain4j.agentic.declarative.ConditionalAgent;
import dev.langchain4j.agentic.declarative.Executor;
import dev.langchain4j.agentic.declarative.ExitCondition;
import dev.langchain4j.agentic.declarative.LoopAgent;
import dev.langchain4j.agentic.declarative.Output;
import dev.langchain4j.agentic.declarative.ParallelAgent;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.declarative.SubAgent;
import dev.langchain4j.agentic.declarative.SupervisorAgent;
import dev.langchain4j.agentic.declarative.SupervisorChatModel;
import dev.langchain4j.agentic.declarative.SupervisorRequest;
import dev.langchain4j.agentic.internal.AgentCall;
import dev.langchain4j.agentic.supervisor.SupervisorResponseStrategy;
import dev.langchain4j.model.chat.ChatModel;
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
import dev.langchain4j.agentic.Agents.FoodExpert;
import dev.langchain4j.agentic.Agents.MovieExpert;
import dev.langchain4j.agentic.Agents.EveningPlan;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static dev.langchain4j.agentic.Models.BASE_MODEL;
import static dev.langchain4j.agentic.Models.PLANNER_MODEL;
import static org.assertj.core.api.Assertions.assertThat;

public class DeclarativeAgentIT {

    public interface StoryCreator {

        @SequenceAgent(outputName = "story", subAgents = {
                @SubAgent(type = CreativeWriter.class, outputName = "story"),
                @SubAgent(type = AudienceEditor.class, outputName = "story"),
                @SubAgent(type = StyleEditor.class, outputName = "story")
        })
        String write(@V("topic") String topic, @V("style") String style, @V("audience") String audience);
    }

    @Test
    void declarative_sequence_tests() {
        StoryCreator storyCreator = AgentServices.createAgenticSystem(StoryCreator.class, BASE_MODEL);

        String story = storyCreator.write("dragons and wizards", "fantasy", "young adults");
        System.out.println(story);
    }

    public interface StyleReviewLoopAgent {

        @LoopAgent(
                description = "Review the given story to ensure it aligns with the specified style",
                outputName = "story", maxIterations = 5,
                subAgents = {
                    @SubAgent(type = StyleScorer.class, outputName = "score"),
                    @SubAgent(type = StyleEditor.class, outputName = "story")
            }
        )
        String write(@V("story") String story);

        @ExitCondition
        static boolean exit(@V("score") double score) {
            return score >= 0.8;
        }
    }

    public interface StoryCreatorWithReview {

        @SequenceAgent(outputName = "story", subAgents = {
                @SubAgent(type = CreativeWriter.class, outputName = "story"),
                @SubAgent(type = StyleReviewLoopAgent.class, outputName = "story")
        })
        ResultWithCognisphere<String> write(@V("topic") String topic, @V("style") String style);
    }

    @Test
    void declarative_sequence_and_loop_tests() {
        StoryCreatorWithReview storyCreator = AgentServices.createAgenticSystem(StoryCreatorWithReview.class, BASE_MODEL);

        ResultWithCognisphere<String> result = storyCreator.write("dragons and wizards", "comedy");
        String story = result.result();
        System.out.println(story);

        Cognisphere cognisphere = result.cognisphere();
        assertThat(cognisphere.readState("topic")).isEqualTo("dragons and wizards");
        assertThat(cognisphere.readState("style")).isEqualTo("comedy");
        assertThat(story).isEqualTo(cognisphere.readState("story"));
        assertThat(cognisphere.readState("score", 0.0)).isGreaterThanOrEqualTo(0.8);
    }

    public interface ExpertsAgent {

        @ConditionalAgent(outputName = "response", subAgents = {
                @SubAgent(name = "medical", type = MedicalExpert.class, outputName = "response"),
                @SubAgent(name = "technical", type = TechnicalExpert.class, outputName = "response"),
                @SubAgent(name = "legal", type = LegalExpert.class, outputName = "response")
        })
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

    public interface ExpertRouterAgent {

        @SequenceAgent(outputName = "response", subAgents = {
                @SubAgent(type = CategoryRouter.class, outputName = "category"),
                @SubAgent(type = ExpertsAgent.class, outputName = "response")
        })
        ResultWithCognisphere<String> ask(@V("request") String request);
    }

    @Test
    void declarative_conditional_tests() {
        ExpertRouterAgent expertRouterAgent = AgentServices.createAgenticSystem(ExpertRouterAgent.class, BASE_MODEL);

        ResultWithCognisphere<String> result = expertRouterAgent.ask("I broke my leg what should I do");
        String response = result.result();
        System.out.println(response);

        Cognisphere cognisphere = result.cognisphere();
        assertThat(cognisphere.readState("category")).isEqualTo(RequestCategory.MEDICAL);
    }

    public interface EveningPlannerAgent {

        @ParallelAgent(outputName = "plans", subAgents = {
                @SubAgent(type = FoodExpert.class, outputName = "meals"),
                @SubAgent(type = MovieExpert.class, outputName = "movies")
        })
        List<EveningPlan> plan(@V("mood") String mood);

        @Executor
        static ExecutorService executor() {
            return Executors.newFixedThreadPool(2);
        }

        @Output
        static List<EveningPlan> createPlans(@V("movies") List<String> movies, @V("meals") List<String> meals) {
            List<EveningPlan> moviesAndMeals = new ArrayList<>();
            for (int i = 0; i < movies.size(); i++) {
                if (i >= meals.size()) {
                    break;
                }
                moviesAndMeals.add(new EveningPlan(movies.get(i), meals.get(i)));
            }
            return moviesAndMeals;
        }
    }

    @Test
    void declarative_parallel_tests() {
        EveningPlannerAgent eveningPlannerAgent = AgentServices.createAgenticSystem(EveningPlannerAgent.class, BASE_MODEL);
        List<Agents.EveningPlan> plans = eveningPlannerAgent.plan("romantic");
        System.out.println(plans);
        assertThat(plans).hasSize(3);
    }

    public interface SupervisorStoryCreator {

        @SupervisorAgent(outputName = "story", responseStrategy = SupervisorResponseStrategy.LAST, subAgents = {
                @SubAgent(type = CreativeWriter.class, outputName = "story"),
                @SubAgent(type = StyleReviewLoopAgent.class, outputName = "story")
        })
        ResultWithCognisphere<String> write(@V("topic") String topic, @V("style") String style);

        @SupervisorRequest
        static String request(@V("topic") String topic, @V("style") String style) {
            return "Write a story about " + topic + " in " + style + " style";
        }

        @SupervisorChatModel
        static ChatModel chatModel() {
            return PLANNER_MODEL;
        }
    }

    @Test
    void declarative_supervisor_tests() {
        SupervisorStoryCreator styledWriter = AgentServices.createAgenticSystem(SupervisorStoryCreator.class, BASE_MODEL);
        ResultWithCognisphere<String> result = styledWriter.write("dragons and wizards", "comedy");

        String story = result.result();
        System.out.println(story);

        Cognisphere cognisphere = result.cognisphere();
        assertThat(cognisphere.readState("topic")).isEqualTo("dragons and wizards");
        assertThat(cognisphere.readState("style")).isEqualTo("comedy");
        assertThat(story).isEqualTo(cognisphere.readState("story"));
        assertThat(cognisphere.readState("score", 0.0)).isGreaterThanOrEqualTo(0.8);

        assertThat(cognisphere.getAgentInvocations("generateStory")).hasSize(1);

        List<AgentCall> scoreAgentCalls = cognisphere.getAgentInvocations("scoreStyle");
        assertThat(scoreAgentCalls).hasSizeBetween(1, 5);
        System.out.println("Score agent invocations: " + scoreAgentCalls);
        assertThat((Double) scoreAgentCalls.get(scoreAgentCalls.size() - 1).response()).isGreaterThanOrEqualTo(0.8);
    }
}
