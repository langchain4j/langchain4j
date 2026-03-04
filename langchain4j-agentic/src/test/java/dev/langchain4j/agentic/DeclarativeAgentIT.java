package dev.langchain4j.agentic;

import static dev.langchain4j.agentic.Models.baseModel;
import static dev.langchain4j.agentic.Models.plannerModel;
import static dev.langchain4j.agentic.observability.HtmlReportGenerator.generateReport;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.langchain4j.agentic.Agents.AudienceEditor;
import dev.langchain4j.agentic.Agents.CategoryRouter;
import dev.langchain4j.agentic.Agents.CreativeWriter;
import dev.langchain4j.agentic.Agents.EveningPlan;
import dev.langchain4j.agentic.Agents.FoodExpert;
import dev.langchain4j.agentic.Agents.LegalExpert;
import dev.langchain4j.agentic.Agents.MedicalExpert;
import dev.langchain4j.agentic.Agents.MovieExpert;
import dev.langchain4j.agentic.Agents.RequestCategory;
import dev.langchain4j.agentic.Agents.StyleEditor;
import dev.langchain4j.agentic.Agents.StyleScorer;
import dev.langchain4j.agentic.Agents.TechnicalExpert;
import dev.langchain4j.agentic.agent.AgentInvocationException;
import dev.langchain4j.agentic.agent.ErrorContext;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.agent.MissingArgumentException;
import dev.langchain4j.agentic.declarative.ActivationCondition;
import dev.langchain4j.agentic.declarative.AgentListenerSupplier;
import dev.langchain4j.agentic.declarative.ChatMemoryProviderSupplier;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.declarative.ConditionalAgent;
import dev.langchain4j.agentic.declarative.ErrorHandler;
import dev.langchain4j.agentic.declarative.ExitCondition;
import dev.langchain4j.agentic.declarative.HumanInTheLoop;
import dev.langchain4j.agentic.declarative.LoopAgent;
import dev.langchain4j.agentic.declarative.LoopCounter;
import dev.langchain4j.agentic.declarative.Output;
import dev.langchain4j.agentic.declarative.ParallelAgent;
import dev.langchain4j.agentic.declarative.ParallelExecutor;
import dev.langchain4j.agentic.declarative.ParallelMapperAgent;
import dev.langchain4j.agentic.declarative.PlannerAgent;
import dev.langchain4j.agentic.declarative.PlannerSupplier;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.declarative.SupervisorAgent;
import dev.langchain4j.agentic.declarative.SupervisorRequest;
import dev.langchain4j.agentic.declarative.ToolsSupplier;
import dev.langchain4j.agentic.internal.AgenticScopeOwner;
import dev.langchain4j.agentic.observability.AgentInvocation;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentMonitor;
import dev.langchain4j.agentic.observability.AgentRequest;
import dev.langchain4j.agentic.observability.AgentResponse;
import dev.langchain4j.agentic.observability.MonitoredAgent;
import dev.langchain4j.agentic.observability.MonitoredExecution;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemConfigurationException;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.AgenticScopeAccess;
import dev.langchain4j.agentic.scope.AgenticScopePersister;
import dev.langchain4j.agentic.scope.AgenticScopeRegistry;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.agentic.supervisor.SupervisorResponseStrategy;
import dev.langchain4j.agentic.workflow.ConditionalAgentInstance;
import dev.langchain4j.agentic.workflow.LoopAgentInstance;
import dev.langchain4j.agentic.workflow.impl.LoopPlanner;
import dev.langchain4j.agentic.workflow.impl.SequentialPlanner;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.memory.ChatMemoryAccess;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "GOOGLE_AI_GEMINI_API_KEY", matches = ".+")
public class DeclarativeAgentIT {

    @Test
    void declarative_single_agent_tests() {
        CreativeWriter creativeWriter = AgenticServices.createAgenticSystem(CreativeWriter.class, baseModel());

        String story = creativeWriter.generateStory("dragons and wizards");
        assertThat(story).isNotBlank();
    }

    public interface StoryCreator {

        @SequenceAgent(
                outputKey = "story",
                subAgents = {CreativeWriter.class, AudienceEditor.class, StyleEditor.class})
        String write(@V("topic") String topic, @V("style") String style, @V("audience") String audience);
    }

    @Test
    void declarative_sequence_tests() {
        StoryCreator storyCreator = AgenticServices.createAgenticSystem(StoryCreator.class, baseModel());

        String story = storyCreator.write("dragons and wizards", "fantasy", "young adults");
        assertThat(story).isNotBlank();
    }

    public interface PlannerBasedStoryCreator {

        @PlannerAgent(
                outputKey = "story",
                subAgents = {CreativeWriter.class, AudienceEditor.class, StyleEditor.class})
        String write(@V("topic") String topic, @V("style") String style, @V("audience") String audience);

        @PlannerSupplier
        static Planner planner() {
            return new SequentialPlanner();
        }
    }

    @Test
    void declarative_planner_tests() {
        PlannerBasedStoryCreator storyCreator =
                AgenticServices.createAgenticSystem(PlannerBasedStoryCreator.class, baseModel());

        String story = storyCreator.write("dragons and wizards", "fantasy", "young adults");
        assertThat(story).isNotBlank();
    }

    public interface StoryCreatorWithConfigurableStyleEditor {

        @SequenceAgent(
                outputKey = "styledStory",
                subAgents = {CreativeWriter.class, AudienceEditor.class, StyleEditor.class})
        String write(@V("topic") String topic, @V("style") String style, @V("audience") String audience);
    }

    @Test
    void declarative_sequence_without_agent_configuration_tests() {
        StoryCreatorWithConfigurableStyleEditor storyCreator =
                AgenticServices.createAgenticSystem(StoryCreatorWithConfigurableStyleEditor.class, baseModel());

        String story = storyCreator.write("dragons and wizards", "fantasy", "young adults");
        assertThat(story).isNull();
    }

    @Test
    void declarative_sequence_with_agent_configuration_tests() {
        StoryCreatorWithConfigurableStyleEditor storyCreator =
                AgenticServices.createAgenticSystem(StoryCreatorWithConfigurableStyleEditor.class, baseModel(), ctx -> {
                    if (ctx.agentServiceClass() == StyleEditor.class) {
                        ctx.agentBuilder().outputKey("styledStory");
                    }
                });

        String story = storyCreator.write("dragons and wizards", "fantasy", "young adults");
        assertThat(story).isNotBlank();
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
        assertThat(story).isNotBlank();
    }

    @Test
    void declarative_sequence_with_error_tests() {
        StoryCreator storyCreator = AgenticServices.createAgenticSystem(StoryCreator.class, baseModel());

        assertThat(assertThrows(
                        AgentInvocationException.class, () -> storyCreator.write(null, "fantasy", "young adults")))
                .hasMessageContaining("topic");
    }

    public interface StoryCreatorWithErrorRecovery {

        @SequenceAgent(
                outputKey = "story",
                subAgents = {CreativeWriter.class, AudienceEditor.class, StyleEditor.class})
        String write(@V("topic") String topic, @V("style") String style, @V("audience") String audience);

        @ErrorHandler
        static ErrorRecoveryResult errorHandler(ErrorContext errorContext) {
            if (errorContext.agentName().equals("generateStory")
                    && errorContext.exception() instanceof MissingArgumentException mEx
                    && mEx.argumentName().equals("topic")) {
                errorContext.agenticScope().writeState("topic", "dragons and wizards");
                return ErrorRecoveryResult.retry();
            }
            return ErrorRecoveryResult.throwException();
        }
    }

    @Test
    void declarative_sequence_with_error_recover_tests() {
        StoryCreatorWithErrorRecovery storyCreator =
                AgenticServices.createAgenticSystem(StoryCreatorWithErrorRecovery.class, baseModel());

        String story = storyCreator.write(null, "fantasy", "young adults");
        assertThat(story).isNotBlank();
    }

    public interface StyleReviewLoopAgent {

        @LoopAgent(
                description = "Review and score the given story to ensure it aligns with the specified style",
                outputKey = "story",
                maxIterations = 5,
                subAgents = {StyleScorer.class, StyleEditor.class})
        String reviewAndScore(@V("story") String story);

        @ExitCondition
        static boolean exit(@V("score") double score) {
            return score >= 0.8;
        }
    }

    public interface StoryCreatorWithReview {

        @SequenceAgent(
                outputKey = "story",
                subAgents = {CreativeWriter.class, StyleReviewLoopAgent.class})
        ResultWithAgenticScope<String> write(@V("topic") String topic, @V("style") String style);
    }

    @Test
    void declarative_sequence_and_loop_tests() {
        StoryCreatorWithReview storyCreator =
                AgenticServices.createAgenticSystem(StoryCreatorWithReview.class, baseModel());

        ResultWithAgenticScope<String> result = storyCreator.write("dragons and wizards", "comedy");
        String story = result.result();
        assertThat(story).isNotBlank();

        AgenticScope agenticScope = result.agenticScope();
        assertThat(agenticScope.readState("topic")).isEqualTo("dragons and wizards");
        assertThat(agenticScope.readState("style")).isEqualTo("comedy");
        assertThat(story).isEqualTo(agenticScope.readState("story"));
        assertThat(agenticScope.readState("score", 0.0)).isGreaterThanOrEqualTo(0.8);
    }

    static AtomicInteger loopCount;

    public interface StyleReviewLoopAgentWithCounter {

        @LoopAgent(
                description = "Review the given story to ensure it aligns with the specified style",
                outputKey = "story",
                maxIterations = 5,
                subAgents = {StyleScorer.class, StyleEditor.class})
        String write(@V("story") String story);

        @ExitCondition(testExitAtLoopEnd = true, description = "score greater than 0.8")
        static boolean exit(@V("score") double score, @LoopCounter int loopCounter) {
            loopCount.set(loopCounter);
            return score >= 0.8;
        }
    }

    public interface StoryCreatorWithReviewWithCounter extends AgentInstance {

        @SequenceAgent(
                outputKey = "story",
                subAgents = {CreativeWriter.class, StyleReviewLoopAgentWithCounter.class})
        ResultWithAgenticScope<String> write(@V("topic") String topic, @V("style") String style);
    }

    @Test
    void declarative_loop_with_counter_tests() {
        loopCount = new AtomicInteger();
        StoryCreatorWithReviewWithCounter storyCreator =
                AgenticServices.createAgenticSystem(StoryCreatorWithReviewWithCounter.class, baseModel());

        assertThat(storyCreator.name()).isEqualTo("write");
        assertThat(storyCreator.subagents()).hasSize(2);

        AgentInstance creativeWriterInstance = storyCreator.subagents().get(0);
        assertThat(creativeWriterInstance.name()).isEqualTo("generateStory");

        AgentInstance loopAgent = storyCreator.subagents().get(1);
        assertThat(loopAgent.topology()).isEqualTo(AgenticSystemTopology.LOOP);
        assertThat(loopAgent.type()).isSameAs(StyleReviewLoopAgentWithCounter.class);
        assertThat(loopAgent.plannerType()).isSameAs(LoopPlanner.class);
        LoopAgentInstance loopInstance = loopAgent.as(LoopAgentInstance.class);
        assertThat(loopInstance.subagents()).hasSize(2);
        assertThat(loopInstance.maxIterations()).isEqualTo(5);
        assertThat(loopInstance.testExitAtLoopEnd()).isTrue();
        assertThat(loopInstance.exitCondition()).isEqualTo("score greater than 0.8");

        ResultWithAgenticScope<String> result = storyCreator.write("dragons and wizards", "comedy");
        String story = result.result();
        assertThat(story).isNotBlank();

        AgenticScope agenticScope = result.agenticScope();
        assertThat(agenticScope.readState("topic")).isEqualTo("dragons and wizards");
        assertThat(agenticScope.readState("style")).isEqualTo("comedy");
        assertThat(story).isEqualTo(agenticScope.readState("story"));
        assertThat(agenticScope.readState("score", 0.0)).isGreaterThanOrEqualTo(0.8);

        List<dev.langchain4j.agentic.scope.AgentInvocation> scoreAgentInvocations =
                agenticScope.agentInvocations("scoreStyle");
        assertThat(scoreAgentInvocations).hasSizeBetween(1, 5).hasSize(loopCount.get());

        List<dev.langchain4j.agentic.scope.AgentInvocation> styleEditorAgentInvocations =
                agenticScope.agentInvocations("editStory");
        assertThat(styleEditorAgentInvocations).hasSizeBetween(1, 5).hasSize(loopCount.get());

        loopCount = null;
    }

    public interface CreativeWriterWithListener extends CreativeWriter {
        @AgentListenerSupplier
        static AgentListener listener() {
            return new AgentListener() {
                @Override
                public void beforeAgentInvocation(AgentRequest request) {
                    requestedTopic = (String) request.inputs().get("topic");
                }
            };
        }
    }

    public interface StyleReviewLoopAgentWithListener extends StyleReviewLoopAgent {
        @AgentListenerSupplier
        static AgentListener listener() {
            return new AgentListener() {
                @Override
                public void afterAgentInvocation(AgentResponse response) {
                    finalScore = response.agenticScope().readState("score", 0.0);
                }
            };
        }
    }

    public interface StoryCreatorWithReviewWithListener {

        @SequenceAgent(
                outputKey = "story",
                subAgents = {CreativeWriterWithListener.class, StyleReviewLoopAgentWithListener.class})
        ResultWithAgenticScope<String> write(@V("topic") String topic, @V("style") String style);
    }

    static String requestedTopic;
    static Double finalScore;

    @Test
    void declarative_listeners_tests() {
        assertThat(requestedTopic).isNull();
        assertThat(finalScore).isNull();

        StoryCreatorWithReviewWithListener storyCreator =
                AgenticServices.createAgenticSystem(StoryCreatorWithReviewWithListener.class, baseModel());

        ResultWithAgenticScope<String> result = storyCreator.write("dragons and wizards", "comedy");
        String story = result.result();
        assertThat(story).isNotBlank();

        AgenticScope agenticScope = result.agenticScope();
        assertThat(agenticScope.readState("topic")).isEqualTo("dragons and wizards");
        assertThat(agenticScope.readState("style")).isEqualTo("comedy");
        assertThat(story).isEqualTo(agenticScope.readState("story"));
        assertThat(agenticScope.readState("score", 0.0)).isGreaterThanOrEqualTo(0.8);

        assertThat(requestedTopic).isEqualTo("dragons and wizards");
        assertThat(finalScore).isGreaterThanOrEqualTo(0.8);

        requestedTopic = null;
        finalScore = null;
    }

    public interface ExpertsAgent {

        @ConditionalAgent(
                outputKey = "response",
                subAgents = {MedicalExpert.class, TechnicalExpert.class, LegalExpert.class})
        String askExpert(@V("request") String request);

        @ActivationCondition(value = MedicalExpert.class, description = "category is medical")
        static boolean activateMedical(@V("category") RequestCategory category) {
            return category == RequestCategory.MEDICAL;
        }

        @ActivationCondition(TechnicalExpert.class)
        static boolean activateTechnical(@V("category") RequestCategory category) {
            return category == RequestCategory.TECHNICAL;
        }

        @ActivationCondition(value = LegalExpert.class, description = "category is legal")
        static boolean activateLegal(AgenticScope agenticScope) {
            return agenticScope.readState("category", RequestCategory.UNKNOWN) == RequestCategory.LEGAL;
        }
    }

    public interface ExpertRouterAgent extends AgentInstance {

        @SequenceAgent(
                outputKey = "response",
                subAgents = {CategoryRouter.class, ExpertsAgent.class})
        ResultWithAgenticScope<String> ask(@V("request") String request);
    }

    @Test
    void declarative_conditional_tests() {
        ExpertRouterAgent expertRouterAgent = AgenticServices.createAgenticSystem(ExpertRouterAgent.class, baseModel());

        assertThat(expertRouterAgent.subagents()).hasSize(2);

        AgentInstance routerAgentInstance = expertRouterAgent.subagents().get(0);
        assertThat(routerAgentInstance.name()).isEqualTo("classify");

        AgentInstance conditionalAgentInstance = expertRouterAgent.subagents().get(1);
        assertThat(conditionalAgentInstance.name()).isEqualTo("askExpert");
        assertThat(conditionalAgentInstance.outputType()).isEqualTo(String.class);
        assertThat(conditionalAgentInstance.outputKey()).isEqualTo("response");
        assertThat(conditionalAgentInstance.topology()).isEqualTo(AgenticSystemTopology.ROUTER);
        assertThat(conditionalAgentInstance.subagents()).hasSize(3);

        ConditionalAgentInstance conditionalInstance = conditionalAgentInstance.as(ConditionalAgentInstance.class);
        assertThat(conditionalInstance.conditionalSubagents()).hasSize(3);
        assertThat(conditionalInstance.conditionalSubagents().get(0).condition())
                .isEqualTo("category is medical");
        assertThat(conditionalInstance.conditionalSubagents().get(1).condition())
                .isEqualTo("<unknown>");
        assertThat(conditionalInstance.conditionalSubagents().get(2).condition())
                .isEqualTo("category is legal");

        ResultWithAgenticScope<String> result = expertRouterAgent.ask("I broke my leg what should I do");
        String response = result.result();
        assertThat(response).isNotBlank();

        AgenticScope agenticScope = result.agenticScope();
        assertThat(agenticScope.readState("category")).isEqualTo(RequestCategory.MEDICAL);
    }

    public interface EveningPlannerAgent extends MonitoredAgent {

        @ParallelAgent(
                outputKey = "plans",
                subAgents = {FoodExpert.class, MovieExpert.class})
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
        EveningPlannerAgent eveningPlannerAgent =
                AgenticServices.createAgenticSystem(EveningPlannerAgent.class, baseModel());
        List<Agents.EveningPlan> plans = eveningPlannerAgent.plan("romantic");
        assertThat(plans).hasSize(3);

        AgentMonitor agentMonitor = eveningPlannerAgent.agentMonitor();
        MonitoredExecution execution = agentMonitor.successfulExecutions().get(0);
        System.out.println(execution);

        assertThat(execution.done()).isTrue();
        assertThat(execution.ongoingInvocations()).isEmpty();
        AgentInvocation topLevelInvocation = execution.topLevelInvocations();
        assertThat(topLevelInvocation.agent().name()).isEqualTo("plan");
        assertThat(topLevelInvocation.inputs()).containsKey("mood").containsValue("romantic");
        assertThat(topLevelInvocation.nestedInvocations()).hasSize(2);

        // generateReport(agentMonitor, Path.of("src", "test", "resources", "parallel.html"));
    }

    public interface SupervisorStoryCreator {

        @SupervisorAgent(
                outputKey = "story",
                responseStrategy = SupervisorResponseStrategy.LAST,
                subAgents = {CreativeWriter.class, StyleReviewLoopAgent.class})
        ResultWithAgenticScope<String> write(@V("topic") String topic, @V("style") String style);

        @SupervisorRequest
        static String request(@V("topic") String topic, @V("style") String style) {
            return "Write a story about " + topic + " in " + style + " style";
        }

        @ChatModelSupplier
        static ChatModel chatModel() {
            return plannerModel();
        }

        @ChatMemoryProviderSupplier
        static ChatMemory chatMemory(Object memoryId) {
            return MessageWindowChatMemory.withMaxMessages(10);
        }
    }

    @Test
    void declarative_supervisor_tests() {
        SupervisorStoryCreator styledWriter =
                AgenticServices.createAgenticSystem(SupervisorStoryCreator.class, baseModel());
        ResultWithAgenticScope<String> result = styledWriter.write("dragons and wizards", "comedy");

        String story = result.result();
        assertThat(story).isNotBlank();

        DefaultAgenticScope agenticScope = (DefaultAgenticScope) result.agenticScope();
        assertThat(agenticScope.readState("topic", "")).contains("dragons and wizards");
        assertThat(agenticScope.readState("style", "")).contains("comedy");
        assertThat(story).isEqualTo(agenticScope.readState("story"));
        assertThat(agenticScope.readState("score", 0.0)).isGreaterThanOrEqualTo(0.8);

        assertThat(agenticScope.agentInvocations("generateStory")).hasSize(1);

        List<dev.langchain4j.agentic.scope.AgentInvocation> scoreAgentInvocations =
                agenticScope.agentInvocations("scoreStyle");
        assertThat(scoreAgentInvocations).hasSizeBetween(1, 5);
        assertThat((Double) scoreAgentInvocations
                        .get(scoreAgentInvocations.size() - 1)
                        .output())
                .isGreaterThanOrEqualTo(0.8);
    }

    public interface MedicalExpertWithMemory {

        @UserMessage(
                """
            You are a medical expert.
            Analyze the following user request under a medical point of view and provide the best possible answer.
            The user request is {{request}}.
            """)
        @Agent(description = "A medical expert", outputKey = "response")
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

        @UserMessage(
                """
            You are a legal expert.
            Analyze the following user request under a legal point of view and provide the best possible answer.
            The user request is {{request}}.
            """)
        @Agent(
                description = "A legal expert",
                outputKey = "response",
                summarizedContext = {"medical", "technical"})
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

        @UserMessage(
                """
            You are a technical expert.
            Analyze the following user request under a technical point of view and provide the best possible answer.
            The user request is {{request}}.
            """)
        @Agent(description = "A technical expert", outputKey = "response")
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

        @ConditionalAgent(
                outputKey = "response",
                subAgents = {MedicalExpertWithMemory.class, TechnicalExpertWithMemory.class, LegalExpertWithMemory.class
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

    public interface ExpertRouterAgentWithMemory extends AgenticScopeAccess {

        @SequenceAgent(
                outputKey = "response",
                subAgents = {CategoryRouterWithModel.class, ExpertsAgentWithMemory.class})
        String ask(@MemoryId String memoryId, @V("request") String request);
    }

    @Test
    void declarative_memory_tests() {
        ExpertRouterAgentWithMemory expertRouterAgent =
                AgenticServices.createAgenticSystem(ExpertRouterAgentWithMemory.class);

        JsonInMemoryAgenticScopeStore store = new JsonInMemoryAgenticScopeStore();
        AgenticScopePersister.setStore(store);

        String response1 = expertRouterAgent.ask("1", "I broke my leg, what should I do?");

        AgenticScope agenticScope1 = expertRouterAgent.getAgenticScope("1");
        assertThat(agenticScope1.readState("category", RequestCategory.UNKNOWN)).isEqualTo(RequestCategory.MEDICAL);

        assertThat(store.getLoadedIds()).isEmpty();

        String response2 = expertRouterAgent.ask("2", "My computer has liquid inside, what should I do?");

        AgenticScope agenticScope2 = expertRouterAgent.getAgenticScope("2");
        assertThat(agenticScope2.readState("category", RequestCategory.UNKNOWN)).isEqualTo(RequestCategory.TECHNICAL);

        AgenticScopeRegistry registry = ((AgenticScopeOwner) expertRouterAgent).registry();
        assertThat(store.getAllKeys()).isEqualTo(registry.getAllAgenticScopeKeysInMemory());

        // Clear the in-memory registry to simulate a restart
        registry.clearInMemory();
        assertThat(registry.getAllAgenticScopeKeysInMemory()).isEmpty();

        String legalResponse1 = expertRouterAgent.ask("1", "Should I sue my neighbor who caused this damage?");

        String legalResponse2 = expertRouterAgent.ask("2", "Should I sue my neighbor who caused this damage?");

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
        @SystemMessage(
                """
            You are a banker that can only withdraw US dollars (USD) from a user account.
            """)
        @UserMessage(
                """
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
        @SystemMessage(
                """
            You are a banker that can only credit US dollars (USD) to a user account.
            """)
        @UserMessage(
                """
            Credit {{amountInUSD}} USD to {{user}}'s account and return the new balance.
            """)
        @Agent("A banker that credit USD to an account")
        String credit(@V("user") String user, @V("amountInUSD") Double amount);

        @ToolsSupplier
        static Object[] tools() {
            return new Object[] {bankTool};
        }
    }

    public interface SupervisorBanker {

        @SupervisorAgent(
                responseStrategy = SupervisorResponseStrategy.SUMMARY,
                subAgents = {WithdrawAgent.class, CreditAgent.class})
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
        assertThat(result).isNotBlank().contains("Mario").contains("Georgios");

        assertThat(bankTool.getBalance("Mario")).isEqualTo(900.0);
        assertThat(bankTool.getBalance("Georgios")).isEqualTo(1100.0);
    }

    private static final AtomicReference<String> requestRef = new AtomicReference<>();
    private static final AtomicReference<String> audienceRef = new AtomicReference<>();

    public interface AudienceRetriever {

        @HumanInTheLoop(description = "Generate a story based on the given topic", outputKey = "audience", async = true)
        static String humanResponse(AgenticScope scope, @V("topic") String topic) {
            requestRef.set("Which audience for topic " + topic + "?");
            CompletableFuture<String> futureResult = new CompletableFuture<>();
            HumanResponseSupplier.pendingResponses.put(scope.memoryId(), futureResult);
            try {
                String result = futureResult.get();
                HumanResponseSupplier.pendingResponses.remove(scope.memoryId());
                return result;
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static class HumanResponseSupplier {

        static final Map<Object, CompletableFuture<String>> pendingResponses = new ConcurrentHashMap<>();

        @Agent
        public static void await(AgenticScope scope) {
            pendingResponses.get(scope.memoryId()).complete("young adults");
        }
    }

    public static class AudienceReader {

        @Agent
        public static void readAudience(@V("audience") String audience) {
            audienceRef.set(audience);
        }
    }

    public interface StoryCreatorWithHumanInTheLoop {

        @SequenceAgent(
                outputKey = "story",
                subAgents = {
                    AudienceRetriever.class,
                    CreativeWriter.class,
                    HumanResponseSupplier.class,
                    AudienceEditor.class,
                    AudienceReader.class
                })
        String write(@V("topic") String topic);
    }

    @Test
    void declarative_human_in_the_loop_tests() {
        StoryCreatorWithHumanInTheLoop storyCreator =
                AgenticServices.createAgenticSystem(StoryCreatorWithHumanInTheLoop.class, baseModel());

        String story = storyCreator.write("dragons and wizards");
        System.out.println(story);

        assertThat(requestRef.get()).isEqualTo("Which audience for topic dragons and wizards?");
        assertThat(audienceRef.get()).isEqualTo("young adults");
    }

    public interface AstrologyAgent {
        @SystemMessage(
                """
            You are an astrologist that generates horoscopes based on the user's name and zodiac sign.
            """)
        @UserMessage("""
            Generate the horoscope for {{name}} who is a {{sign}}.
            """)
        @Agent("An astrologist that generates horoscopes based on the user's name and zodiac sign.")
        String horoscope(@V("name") String name, @V("sign") String sign);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return baseModel();
        }
    }

    private static final AtomicReference<String> signRequest = new AtomicReference<>();

    public interface SignRetriever {

        @HumanInTheLoop(description = "An agent that asks the zodiac sign of the user", outputKey = "sign")
        static String humanResponse(@V("name") String name) {
            signRequest.set("hi " + name + ", what is your zodiac sign?");
            return "pisces";
        }
    }

    @Test
    void supervisor_human_in_the_loop_tests() {
        var horoscopeGenerator = AgenticServices.supervisorBuilder()
                .chatModel(plannerModel())
                .responseStrategy(SupervisorResponseStrategy.LAST)
                .subAgents(AstrologyAgent.class, SignRetriever.class)
                .build();

        String horoscope = horoscopeGenerator.invoke("My name is Mario");
        assertThat(horoscope).containsIgnoringCase("pisces");

        assertThat(signRequest.get()).isEqualTo("hi Mario, what is your zodiac sign?");
    }

    // --- Parallel Multi-Instance Agent tests ---

    public record Person(String name, String sign) {}

    public interface PersonAstrologyAgent {
        @SystemMessage(
                """
            You are an astrologist that generates horoscopes based on the user's name and zodiac sign.
            """)
        @UserMessage(
                """
            Generate the horoscope for {{person}}.
            The person has a name and a zodiac sign. Use both to create a personalized horoscope.
            """)
        @Agent(description = "An astrologist that generates horoscopes for a person", outputKey = "horoscope")
        String horoscope(@V("person") Person person);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return baseModel();
        }
    }

    public interface BatchHoroscopeAgent extends AgentInstance {

        @ParallelMapperAgent(subAgent = PersonAstrologyAgent.class)
        List<String> generateHoroscopes(@V("persons") List<Person> persons);

        @ParallelExecutor
        static Executor executor() {
            return Executors.newFixedThreadPool(3);
        }
    }

    @Test
    void declarative_parallel_mapper_tests() {
        BatchHoroscopeAgent agent = AgenticServices.createAgenticSystem(BatchHoroscopeAgent.class, baseModel());

        assertThat(agent.name()).isEqualTo("generateHoroscopes");
        assertThat(agent.topology()).isEqualTo(AgenticSystemTopology.PARALLEL);
        assertThat(agent.subagents()).hasSize(1);

        AgentInstance subagent = agent.subagents().get(0);
        assertThat(subagent.name()).isEqualTo("horoscope");
        assertThat(subagent.outputKey()).isEqualTo("horoscope");

        List<Person> persons =
                List.of(new Person("Mario", "aries"), new Person("Luigi", "pisces"), new Person("Peach", "leo"));

        List<String> horoscopes = agent.generateHoroscopes(persons);
        assertThat(horoscopes).hasSize(3).allSatisfy(horoscope -> assertThat(horoscope).isNotBlank());
    }

    public interface BatchHoroscopeAgentWith2Lists extends AgentInstance {

        @ParallelMapperAgent(subAgent = PersonAstrologyAgent.class)
        List<String> generateHoroscopes(@V("persons") List<Person> persons, @V("moods") List<String> moods);
    }

    @Test
    void parallel_mapper_with_ambigous_items_provider_throws_tests() {
        assertThat(assertThrows(AgenticSystemConfigurationException.class, () ->
                AgenticServices.createAgenticSystem(BatchHoroscopeAgentWith2Lists.class, baseModel())));
    }
}
