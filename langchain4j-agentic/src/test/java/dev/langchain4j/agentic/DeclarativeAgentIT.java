package dev.langchain4j.agentic;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agentic.agent.AgentInvocationException;
import dev.langchain4j.agentic.agent.ErrorContext;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.agent.MissingArgumentException;
import dev.langchain4j.agentic.declarative.ChatMemoryProviderSupplier;
import dev.langchain4j.agentic.declarative.ErrorHandler;
import dev.langchain4j.agentic.declarative.ToolsSupplier;
import dev.langchain4j.agentic.internal.AgenticScopeOwner;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.AgenticScopeAccess;
import dev.langchain4j.agentic.scope.AgenticScopePersister;
import dev.langchain4j.agentic.scope.AgenticScopeRegistry;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.agentic.declarative.ActivationCondition;
import dev.langchain4j.agentic.declarative.ConditionalAgent;
import dev.langchain4j.agentic.declarative.ParallelExecutor;
import dev.langchain4j.agentic.declarative.ExitCondition;
import dev.langchain4j.agentic.declarative.LoopAgent;
import dev.langchain4j.agentic.declarative.Output;
import dev.langchain4j.agentic.declarative.ParallelAgent;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.declarative.SubAgent;
import dev.langchain4j.agentic.declarative.SupervisorAgent;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.declarative.SupervisorRequest;
import dev.langchain4j.agentic.internal.AgentInvocation;
import dev.langchain4j.agentic.supervisor.SupervisorResponseStrategy;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
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
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static dev.langchain4j.agentic.Models.baseModel;
import static dev.langchain4j.agentic.Models.plannerModel;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class DeclarativeAgentIT {

    @Test
    void declarative_single_agent_tests() {
        CreativeWriter creativeWriter = AgenticServices.createAgenticSystem(CreativeWriter.class, baseModel());

        String story = creativeWriter.generateStory("dragons and wizards");
        System.out.println(story);
    }

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
        StoryCreator storyCreator = AgenticServices.createAgenticSystem(StoryCreator.class, baseModel());

        String story = storyCreator.write("dragons and wizards", "fantasy", "young adults");
        System.out.println(story);
    }

    public interface StoryCreatorWithModel extends StoryCreator {

        @ChatModelSupplier
        static ChatModel chatModel() {
            return baseModel();
        }
    }

    @Test
    void declarative_sequence_with_model_tests() {
        StoryCreator storyCreator = AgenticServices.createAgenticSystem(StoryCreatorWithModel.class);

        String story = storyCreator.write("dragons and wizards", "fantasy", "young adults");
        System.out.println(story);
    }

    @Test
    void declarative_sequence_with_error_tests() {
        StoryCreator storyCreator = AgenticServices.createAgenticSystem(StoryCreator.class, baseModel());

        assertThat(
                assertThrows(AgentInvocationException.class,
                        () -> storyCreator.write(null, "fantasy", "young adults"))
        ).hasMessageContaining("topic");
    }

    public interface StoryCreatorWithErrorRecovery {

        @SequenceAgent(outputName = "story", subAgents = {
                @SubAgent(type = CreativeWriter.class, outputName = "story"),
                @SubAgent(type = AudienceEditor.class, outputName = "story"),
                @SubAgent(type = StyleEditor.class, outputName = "story")
        })
        String write(@V("topic") String topic, @V("style") String style, @V("audience") String audience);

        @ErrorHandler
        static ErrorRecoveryResult errorHandler(ErrorContext errorContext) {
            if (errorContext.agentName().equals("generateStory") &&
                    errorContext.exception() instanceof MissingArgumentException mEx && mEx.argumentName().equals("topic")) {
                errorContext.agenticScope().writeState("topic", "dragons and wizards");
                return ErrorRecoveryResult.retry();
            }
            return ErrorRecoveryResult.throwException();
        }
    }

    @Test
    void declarative_sequence_with_error_recover_tests() {
        StoryCreatorWithErrorRecovery storyCreator = AgenticServices.createAgenticSystem(StoryCreatorWithErrorRecovery.class, baseModel());

        String story = storyCreator.write(null, "fantasy", "young adults");
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
        ResultWithAgenticScope<String> write(@V("topic") String topic, @V("style") String style);
    }

    @Test
    void declarative_sequence_and_loop_tests() {
        StoryCreatorWithReview storyCreator = AgenticServices.createAgenticSystem(StoryCreatorWithReview.class, baseModel());

        ResultWithAgenticScope<String> result = storyCreator.write("dragons and wizards", "comedy");
        String story = result.result();
        System.out.println(story);

        AgenticScope agenticScope = result.agenticScope();
        assertThat(agenticScope.readState("topic")).isEqualTo("dragons and wizards");
        assertThat(agenticScope.readState("style")).isEqualTo("comedy");
        assertThat(story).isEqualTo(agenticScope.readState("story"));
        assertThat(agenticScope.readState("score", 0.0)).isGreaterThanOrEqualTo(0.8);
    }

    public interface ExpertsAgent {

        @ConditionalAgent(outputName = "response", subAgents = {
                @SubAgent(type = MedicalExpert.class, outputName = "response"),
                @SubAgent(type = TechnicalExpert.class, outputName = "response"),
                @SubAgent(type = LegalExpert.class, outputName = "response")
        })
        String askExpert(@V("request") String request);

        @ActivationCondition(MedicalExpert.class)
        static boolean activateMedical(@V("category") RequestCategory category) {
            return category == RequestCategory.MEDICAL;
        }

        @ActivationCondition(TechnicalExpert.class)
        static boolean activateTechnical(@V("category") RequestCategory category) {
            return category == RequestCategory.TECHNICAL;
        }

        @ActivationCondition(LegalExpert.class)
        static boolean activateLegal(@V("category") RequestCategory category) {
            return category == RequestCategory.LEGAL;
        }
    }

    public interface ExpertRouterAgent {

        @SequenceAgent(outputName = "response", subAgents = {
                @SubAgent(type = CategoryRouter.class, outputName = "category"),
                @SubAgent(type = ExpertsAgent.class, outputName = "response")
        })
        ResultWithAgenticScope<String> ask(@V("request") String request);
    }

    @Test
    void declarative_conditional_tests() {
        ExpertRouterAgent expertRouterAgent = AgenticServices.createAgenticSystem(ExpertRouterAgent.class, baseModel());

        ResultWithAgenticScope<String> result = expertRouterAgent.ask("I broke my leg what should I do");
        String response = result.result();
        System.out.println(response);

        AgenticScope agenticScope = result.agenticScope();
        assertThat(agenticScope.readState("category")).isEqualTo(RequestCategory.MEDICAL);
    }

    public interface EveningPlannerAgent {

        @ParallelAgent(outputName = "plans", subAgents = {
                @SubAgent(type = FoodExpert.class, outputName = "meals"),
                @SubAgent(type = MovieExpert.class, outputName = "movies")
        })
        List<EveningPlan> plan(@V("mood") String mood);

        @ParallelExecutor
        static Executor executor() {
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
        EveningPlannerAgent eveningPlannerAgent = AgenticServices.createAgenticSystem(EveningPlannerAgent.class, baseModel());
        List<Agents.EveningPlan> plans = eveningPlannerAgent.plan("romantic");
        System.out.println(plans);
        assertThat(plans).hasSize(3);
    }

    public interface SupervisorStoryCreator {

        @SupervisorAgent(outputName = "story", responseStrategy = SupervisorResponseStrategy.LAST, subAgents = {
                @SubAgent(type = CreativeWriter.class, outputName = "story"),
                @SubAgent(type = StyleReviewLoopAgent.class, outputName = "story")
        })
        ResultWithAgenticScope<String> write(@V("topic") String topic, @V("style") String style);

        @SupervisorRequest
        static String request(@V("topic") String topic, @V("style") String style) {
            return "Write a story about " + topic + " in " + style + " style";
        }

        @ChatModelSupplier
        static ChatModel chatModel() {
            return plannerModel();
        }
    }

    @Test
    void declarative_supervisor_tests() {
        SupervisorStoryCreator styledWriter = AgenticServices.createAgenticSystem(SupervisorStoryCreator.class, baseModel());
        ResultWithAgenticScope<String> result = styledWriter.write("dragons and wizards", "comedy");

        String story = result.result();
        System.out.println(story);

        DefaultAgenticScope agenticScope = (DefaultAgenticScope) result.agenticScope();
        assertThat(agenticScope.readState("topic", "")).contains("dragons and wizards");
        assertThat(agenticScope.readState("style", "")).contains("comedy");
        assertThat(story).isEqualTo(agenticScope.readState("story"));
        assertThat(agenticScope.readState("score", 0.0)).isGreaterThanOrEqualTo(0.8);

        assertThat(agenticScope.agentInvocations("generateStory")).hasSize(1);

        List<AgentInvocation> scoreAgentCalls = agenticScope.agentInvocations("scoreStyle");
        assertThat(scoreAgentCalls).hasSizeBetween(1, 5);
        System.out.println("Score agent invocations: " + scoreAgentCalls);
        assertThat((Double) scoreAgentCalls.get(scoreAgentCalls.size() - 1).output()).isGreaterThanOrEqualTo(0.8);
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

        @ChatMemoryProviderSupplier
        static ChatMemory chatMemory(Object memoryId) {
            return MessageWindowChatMemory.withMaxMessages(10);
        }

        @ChatModelSupplier
        static ChatModel chatModel() {
            return baseModel();
        }
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

        @ChatMemoryProviderSupplier
        static ChatMemory chatMemory(Object memoryId) {
            return MessageWindowChatMemory.withMaxMessages(10);
        }

        @ChatModelSupplier
        static ChatModel chatModel() {
            return baseModel();
        }
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

        @ChatMemoryProviderSupplier
        static ChatMemory chatMemory(Object memoryId) {
            return MessageWindowChatMemory.withMaxMessages(10);
        }

        @ChatModelSupplier
        static ChatModel chatModel() {
            return baseModel();
        }
    }

    public interface ExpertsAgentWithMemory {

        @ConditionalAgent(outputName = "response", subAgents = {
                @SubAgent(type = MedicalExpertWithMemory.class, outputName = "response"),
                @SubAgent(type = TechnicalExpertWithMemory.class, outputName = "response"),
                @SubAgent(type = LegalExpertWithMemory.class, outputName = "response", summarizedContext = {"medical", "technical"})
        })
        String askExpert(@V("request") String request);

        @ActivationCondition(MedicalExpertWithMemory.class)
        static boolean activateMedical(@V("category") RequestCategory category) {
            return category == RequestCategory.MEDICAL;
        }

        @ActivationCondition(TechnicalExpertWithMemory.class)
        static boolean activateTechnical(@V("category") RequestCategory category) {
            return category == RequestCategory.TECHNICAL;
        }

        @ActivationCondition(LegalExpertWithMemory.class)
        static boolean activateLegal(@V("category") RequestCategory category) {
            return category == RequestCategory.LEGAL;
        }
    }

    public interface CategoryRouterWithModel extends CategoryRouter {

        @ChatModelSupplier
        static ChatModel chatModel() {
            return baseModel();
        }
    }

    public interface ExpertRouterAgentWithMemory extends AgenticScopeAccess  {

        @SequenceAgent(outputName = "response", subAgents = {
                @SubAgent(type = CategoryRouterWithModel.class, outputName = "category"),
                @SubAgent(type = ExpertsAgentWithMemory.class, outputName = "response")
        })
        String ask(@MemoryId String memoryId, @V("request") String request);
    }

    @Test
    void declarative_memory_tests() {
        ExpertRouterAgentWithMemory expertRouterAgent = AgenticServices.createAgenticSystem(ExpertRouterAgentWithMemory.class);

        JsonInMemoryAgenticScopeStore store = new JsonInMemoryAgenticScopeStore();
        AgenticScopePersister.setStore(store);

        String response1 = expertRouterAgent.ask("1", "I broke my leg, what should I do?");
        System.out.println(response1);

        AgenticScope agenticScope1 = expertRouterAgent.getAgenticScope("1");
        assertThat(agenticScope1.readState("category", RequestCategory.UNKNOWN)).isEqualTo(RequestCategory.MEDICAL);

        assertThat(store.getLoadedIds()).isEmpty();

        String response2 = expertRouterAgent.ask("2", "My computer has liquid inside, what should I do?");
        System.out.println(response2);

        AgenticScope agenticScope2 = expertRouterAgent.getAgenticScope("2");
        assertThat(agenticScope2.readState("category", RequestCategory.UNKNOWN)).isEqualTo(RequestCategory.TECHNICAL);

        AgenticScopeRegistry registry = ((AgenticScopeOwner)expertRouterAgent).registry();
        assertThat(store.getAllKeys()).isEqualTo(registry.getAllAgenticScopeKeysInMemory());

        // Clear the in-memory registry to simulate a restart
        registry.clearInMemory();
        assertThat(registry.getAllAgenticScopeKeysInMemory()).isEmpty();

        String legalResponse1 = expertRouterAgent.ask("1", "Should I sue my neighbor who caused this damage?");
        System.out.println(legalResponse1);

        String legalResponse2 = expertRouterAgent.ask("2", "Should I sue my neighbor who caused this damage?");
        System.out.println(legalResponse2);

        assertThat(store.getLoadedIds()).isEqualTo(List.of("1", "2"));

        assertThat(legalResponse1).containsIgnoringCase("medical").doesNotContain("computer");
        assertThat(legalResponse2).containsIgnoringCase("computer").doesNotContain("medical");

        // It is necessary to read again the agenticScope instances since they were evicted from the in-memory registry
        // and reloaded from the persistence provider
        agenticScope1 = expertRouterAgent.getAgenticScope("1");
        assertThat(agenticScope1.readState("category", RequestCategory.UNKNOWN)).isEqualTo(RequestCategory.LEGAL);
        agenticScope2 = expertRouterAgent.getAgenticScope("2");
        assertThat(agenticScope2.readState("category", RequestCategory.UNKNOWN)).isEqualTo(RequestCategory.LEGAL);

        assertThat(expertRouterAgent.evictAgenticScope("1")).isTrue();
        assertThat(expertRouterAgent.evictAgenticScope("2")).isTrue();
        assertThat(expertRouterAgent.evictAgenticScope("1")).isFalse();
        assertThat(expertRouterAgent.evictAgenticScope("2")).isFalse();

        AgenticScopePersister.setStore(null);
    }

    static SupervisorAgentIT.BankTool bankTool = new SupervisorAgentIT.BankTool();

    public interface WithdrawAgent {
        @SystemMessage("""
            You are a banker that can only withdraw US dollars (USD) from a user account.
            """)
        @UserMessage("""
            Withdraw {{amountInUSD}} USD from {{user}}'s account and return the new balance.
            """)
        @Agent("A banker that withdraw USD from an account")
        String withdraw(@V("user") String user, @V("amountInUSD") Double amount);

        @ToolsSupplier
        static Object tools() {
            return bankTool;
        }
    }

    public interface CreditAgent {
        @SystemMessage("""
            You are a banker that can only credit US dollars (USD) to a user account.
            """)
        @UserMessage("""
            Credit {{amountInUSD}} USD to {{user}}'s account and return the new balance.
            """)
        @Agent("A banker that credit USD to an account")
        String credit(@V("user") String user, @V("amountInUSD") Double amount);

        @ToolsSupplier
        static Object[] tools() {
            return new Object[] { bankTool };
        }
    }

    public interface SupervisorBanker {

        @SupervisorAgent(responseStrategy = SupervisorResponseStrategy.SUMMARY, subAgents = {
                @SubAgent(type = WithdrawAgent.class),
                @SubAgent(type = CreditAgent.class)
        })
        String invoke(@V("request") String request);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return plannerModel();
        }
    }

    @Test
    void declarative_tools_tests() {
        bankTool.createAccount("Mario", 1000.0);
        bankTool.createAccount("Georgios", 1000.0);

        SupervisorBanker bankSupervisor = AgenticServices.createAgenticSystem(SupervisorBanker.class, baseModel());
        String result = bankSupervisor.invoke("Transfer 100 USD from Mario's account to Georgios' one");

        System.out.println(result);

        assertThat(bankTool.getBalance("Mario")).isEqualTo(900.0);
        assertThat(bankTool.getBalance("Georgios")).isEqualTo(1100.0);
    }
}
