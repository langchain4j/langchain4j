package dev.langchain4j.agentic;

import static dev.langchain4j.agentic.Models.baseModel;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agentic.Agents.AudienceEditor;
import dev.langchain4j.agentic.Agents.CategoryRouter;
import dev.langchain4j.agentic.Agents.CreativeWriter;
import dev.langchain4j.agentic.Agents.EveningPlan;
import dev.langchain4j.agentic.Agents.EveningPlannerAgent;
import dev.langchain4j.agentic.Agents.ExpertRouterAgent;
import dev.langchain4j.agentic.Agents.ExpertRouterAgentWithMemory;
import dev.langchain4j.agentic.Agents.FoodExpert;
import dev.langchain4j.agentic.Agents.LegalExpert;
import dev.langchain4j.agentic.Agents.LegalExpertWithMemory;
import dev.langchain4j.agentic.Agents.MedicalExpert;
import dev.langchain4j.agentic.Agents.MedicalExpertWithMemory;
import dev.langchain4j.agentic.Agents.MovieExpert;
import dev.langchain4j.agentic.Agents.RequestCategory;
import dev.langchain4j.agentic.Agents.StyleEditor;
import dev.langchain4j.agentic.Agents.StyleScorer;
import dev.langchain4j.agentic.Agents.StyledWriter;
import dev.langchain4j.agentic.Agents.TechnicalExpert;
import dev.langchain4j.agentic.Agents.TechnicalExpertWithMemory;
import dev.langchain4j.agentic.agent.AgentInvocationException;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.agent.MissingArgumentException;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.internal.AgentInvocation;
import dev.langchain4j.agentic.internal.AgenticScopeOwner;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.AgenticScopePersister;
import dev.langchain4j.agentic.scope.AgenticScopeRegistry;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.agentic.workflow.HumanInTheLoop;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

public class WorkflowAgentsIT {

    public interface CreativeWriterWithModel extends CreativeWriter {
        @ChatModelSupplier
        static ChatModel chatModel() {
            return baseModel();
        }
    }

    @Test
    void sequential_agents_tests() {
        check_sequential_agents(false);
    }

    @Test
    void sequential_agents_using_declarative_api_tests() {
        check_sequential_agents(true);
    }

    void check_sequential_agents(boolean useDeclarativeAPI) {
        CreativeWriter creativeWriter = useDeclarativeAPI
                ? spy(AgenticServices.agentBuilder(CreativeWriterWithModel.class)
                        .outputKey("story")
                        .build())
                : spy(AgenticServices.agentBuilder(CreativeWriter.class)
                        .chatModel(baseModel())
                        .outputKey("story")
                        .build());

        AudienceEditor audienceEditor = spy(AgenticServices.agentBuilder(AudienceEditor.class)
                .chatModel(baseModel())
                .outputKey("story")
                .build());

        StyleEditor styleEditor = spy(AgenticServices.agentBuilder(StyleEditor.class)
                .chatModel(baseModel())
                .outputKey("story")
                .build());

        UntypedAgent novelCreator = AgenticServices.sequenceBuilder()
                .subAgents(creativeWriter, audienceEditor, styleEditor)
                .outputKey("story")
                .build();

        Map<String, Object> input = Map.of(
                "topic", "dragons and wizards",
                "style", "fantasy",
                "audience", "young adults");

        String story = (String) novelCreator.invoke(input);
        System.out.println(story);

        verify(creativeWriter).generateStory("dragons and wizards");
        verify(audienceEditor).editStory(any(), eq("young adults"));
        verify(styleEditor).editStory(any(), eq("fantasy"));
    }

    @Test
    void sequential_agents_with_error_tests() {
        CreativeWriter creativeWriter = spy(AgenticServices.agentBuilder(CreativeWriter.class)
                .chatModel(baseModel())
                .outputKey("story")
                .build());

        AudienceEditor audienceEditor = spy(AgenticServices.agentBuilder(AudienceEditor.class)
                .chatModel(baseModel())
                .outputKey("story")
                .build());

        StyleEditor styleEditor = spy(AgenticServices.agentBuilder(StyleEditor.class)
                .chatModel(baseModel())
                .outputKey("story")
                .build());

        UntypedAgent novelCreator = AgenticServices.sequenceBuilder()
                .subAgents(creativeWriter, audienceEditor, styleEditor)
                .outputKey("story")
                .build();

        Map<String, Object> input = Map.of(
                // missing "topic" entry to trigger an error
                // "topic", "dragons and wizards",
                "style", "fantasy",
                "audience", "young adults");

        assertThat(assertThrows(AgentInvocationException.class, () -> novelCreator.invoke(input)))
                .hasMessageContaining("topic");
    }

    @Test
    void sequential_agents_with_error_recovery_tests() {
        AtomicBoolean errorRecoveryCalled = new AtomicBoolean(false);

        CreativeWriter creativeWriter = spy(AgenticServices.agentBuilder(CreativeWriter.class)
                .chatModel(baseModel())
                .outputKey("story")
                .build());

        AudienceEditor audienceEditor = spy(AgenticServices.agentBuilder(AudienceEditor.class)
                .chatModel(baseModel())
                .outputKey("story")
                .build());

        StyleEditor styleEditor = spy(AgenticServices.agentBuilder(StyleEditor.class)
                .chatModel(baseModel())
                .outputKey("story")
                .build());

        UntypedAgent novelCreator = AgenticServices.sequenceBuilder()
                .subAgents(creativeWriter, audienceEditor, styleEditor)
                .errorHandler(errorContext -> {
                    if (errorContext.agentName().equals("generateStory")
                            && errorContext.exception() instanceof MissingArgumentException mEx
                            && mEx.argumentName().equals("topic")) {
                        errorContext.agenticScope().writeState("topic", "dragons and wizards");
                        errorRecoveryCalled.set(true);
                        return ErrorRecoveryResult.retry();
                    }
                    return ErrorRecoveryResult.throwException();
                })
                .outputKey("story")
                .build();

        Map<String, Object> input = Map.of(
                // missing "topic" entry to trigger an error
                // "topic", "dragons and wizards",
                "style", "fantasy",
                "audience", "young adults");

        String story = (String) novelCreator.invoke(input);
        System.out.println(story);

        assertThat(errorRecoveryCalled.get()).isTrue();

        verify(creativeWriter).generateStory("dragons and wizards");
        verify(audienceEditor).editStory(any(), eq("young adults"));
        verify(styleEditor).editStory(any(), eq("fantasy"));
    }

    public static class FailingAsyncAgent {
        private final AtomicInteger callsCounter = new AtomicInteger(0);

        @Agent(async = true, outputKey = "topic")
        public String getTopic() {
            if (callsCounter.getAndIncrement() < 2) {
                try {
                    Thread.sleep(10L); // simulate some delay in the processing
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                throw new RuntimeException("Intentional failure");
            }
            return "dragons and wizards";
        }
    }

    @Test
    void error_recovery_with_async_agent_tests() {
        AtomicBoolean errorRecoveryCalled = new AtomicBoolean(false);

        CreativeWriter creativeWriter = spy(AgenticServices.agentBuilder(CreativeWriter.class)
                .chatModel(baseModel())
                .outputKey("story")
                .build());

        UntypedAgent novelCreator = AgenticServices.sequenceBuilder()
                .subAgents(new FailingAsyncAgent(), creativeWriter)
                .errorHandler(errorContext -> {
                    errorRecoveryCalled.set(true);
                    if (errorContext.agentName().equals("getTopic")) {
                        return ErrorRecoveryResult.retry();
                    }
                    return ErrorRecoveryResult.throwException();
                })
                .outputKey("story")
                .build();

        String story = (String) novelCreator.invoke(Map.of());
        System.out.println(story);

        verify(creativeWriter).generateStory("dragons and wizards");
    }

    @Test
    void sequential_agents_with_human_in_the_loop_tests() {
        CreativeWriter creativeWriter = spy(AgenticServices.agentBuilder(CreativeWriter.class)
                .chatModel(baseModel())
                .outputKey("story")
                .build());

        CyclicBarrier barrier = new CyclicBarrier(2);
        AtomicReference<String> request = new AtomicReference<>();
        AtomicReference<String> audience = new AtomicReference<>();

        HumanInTheLoop humanInTheLoop = AgenticServices.humanInTheLoopBuilder()
                .description("An agent that asks the audience for the story")
                .inputKey("topic")
                .outputKey("audience")
                .async(true)
                .requestWriter(q -> request.set("Which audience for topic " + q + "?"))
                .responseReader(() -> {
                    try {
                        barrier.await();
                    } catch (InterruptedException | BrokenBarrierException e) {
                        throw new RuntimeException(e);
                    }

                    // check that the creativeWriter was already invoked
                    verify(creativeWriter).generateStory("dragons and wizards");

                    return "young adults";
                })
                .build();

        AudienceEditor audienceEditor = spy(AgenticServices.agentBuilder(AudienceEditor.class)
                .chatModel(baseModel())
                .outputKey("story")
                .build());

        UntypedAgent novelCreator = AgenticServices.sequenceBuilder()
                .subAgents(
                        humanInTheLoop, // asks user for the audience in a non-blocking way
                        creativeWriter, // doesn't need the audience so it can generate the story without waiting for
                        // the human-in-the-loop response
                        AgenticServices.agentAction(
                                barrier::await), // unblock the human-in-the-loop making its response available
                        audienceEditor, // use the audience provided by the human-in-the-loop
                        AgenticServices.agentAction(agenticScope -> audience.set(agenticScope.readState(
                                "audience", "")))) // read the audience state, just for test purposes
                .outputKey("story")
                .build();

        Map<String, Object> input = Map.of("topic", "dragons and wizards");

        String story = (String) novelCreator.invoke(input);
        System.out.println(story);

        assertThat(request.get()).isEqualTo("Which audience for topic dragons and wizards?");
        assertThat(audience.get()).isEqualTo("young adults");
        verify(audienceEditor).editStory(any(), eq("young adults"));
    }

    @Test
    void loop_agents_tests() {
        AtomicReference<Object> requestedTopic = new AtomicReference<>();
        AtomicReference<Double> finalScore = new AtomicReference<>();

        CreativeWriter creativeWriter = AgenticServices.agentBuilder(CreativeWriter.class)
                .chatModel(baseModel())
                .outputKey("story")
                .beforeAgentInvocation(
                        request -> requestedTopic.set(request.inputs().get("topic")))
                .build();

        StyleEditor styleEditor = AgenticServices.agentBuilder(StyleEditor.class)
                .chatModel(baseModel())
                .outputKey("story")
                .build();

        StyleScorer styleScorer = AgenticServices.agentBuilder(StyleScorer.class)
                .chatModel(baseModel())
                .outputKey("score")
                .build();

        UntypedAgent styleReviewLoop = AgenticServices.loopBuilder()
                .subAgents(styleScorer, styleEditor)
                .maxIterations(5)
                .exitCondition(agenticScope -> agenticScope.readState("score", 0.0) >= 0.8)
                .afterAgentInvocation(
                        response -> finalScore.set(response.agenticScope().readState("score", 0.0)))
                .build();

        UntypedAgent styledWriter = AgenticServices.sequenceBuilder()
                .subAgents(creativeWriter, styleReviewLoop)
                .outputKey("story")
                .build();

        Map<String, Object> input = Map.of(
                "topic", "dragons and wizards",
                "style", "comedy");

        ResultWithAgenticScope<String> result = styledWriter.invokeWithAgenticScope(input);
        String story = result.result();
        System.out.println(story);

        AgenticScope agenticScope = result.agenticScope();
        assertThat(story).isEqualTo(agenticScope.readState("story"));
        assertThat(agenticScope.readState("score", 0.0)).isGreaterThanOrEqualTo(0.8);

        assertThat(agenticScope.contextAsConversation("editStory"))
                .isNotBlank()
                .isEqualTo(agenticScope.contextAsConversation(styleEditor));
        assertThat(agenticScope.contextAsConversation("notExistingAgent")).isBlank();

        assertThat(requestedTopic.get()).isEqualTo("dragons and wizards");
        assertThat(finalScore.get()).isGreaterThanOrEqualTo(0.8);
    }

    @Test
    void typed_loop_agents_tests() {
        check_typed_loop_agents(false);
    }

    @Test
    void typed_loop_agents_completing_loop_tests() {
        check_typed_loop_agents(true);
    }

    void check_typed_loop_agents(boolean testExitAtLoopEnd) {
        AtomicInteger loopCount = new AtomicInteger();

        CreativeWriter creativeWriter = AgenticServices.agentBuilder(CreativeWriter.class)
                .chatModel(baseModel())
                .outputKey("story")
                .build();

        StyleEditor styleEditor = AgenticServices.agentBuilder(StyleEditor.class)
                .chatModel(baseModel())
                .outputKey("story")
                .build();

        StyleScorer styleScorer = AgenticServices.agentBuilder(StyleScorer.class)
                .chatModel(baseModel())
                .outputKey("score")
                .build();

        UntypedAgent styleReviewLoop = AgenticServices.loopBuilder()
                .subAgents(styleScorer, styleEditor)
                .maxIterations(5)
                .testExitAtLoopEnd(testExitAtLoopEnd)
                .exitCondition((agenticScope, loop) -> {
                    loopCount.set(loop);
                    return agenticScope.readState("score", 0.0) >= 0.8;
                })
                .build();

        StyledWriter styledWriter = AgenticServices.sequenceBuilder(StyledWriter.class)
                .subAgents(creativeWriter, styleReviewLoop)
                .outputKey("story")
                .build();

        ResultWithAgenticScope<String> result = styledWriter.writeStoryWithStyle("dragons and wizards", "comedy");
        String story = result.result();
        System.out.println(story);

        AgenticScope agenticScope = result.agenticScope();
        // Verify that an ephemeral agenticScope is correctly evicted from the registry after the call
        assertThat(styledWriter.getAgenticScope(agenticScope.memoryId())).isNull();

        assertThat(agenticScope.readState("topic")).isEqualTo("dragons and wizards");
        assertThat(agenticScope.readState("style")).isEqualTo("comedy");
        assertThat(story).isEqualTo(agenticScope.readState("story"));
        assertThat(agenticScope.readState("score", 0.0)).isGreaterThanOrEqualTo(0.8);

        assertThat(agenticScope.agentInvocations("generateStory")).hasSize(1);

        List<AgentInvocation> scoreAgentCalls = agenticScope.agentInvocations("scoreStyle");
        assertThat(scoreAgentCalls).hasSizeBetween(1, 5).hasSize(loopCount.get());
        System.out.println("Score agent invocations: " + scoreAgentCalls);
        assertThat((Double) scoreAgentCalls.get(scoreAgentCalls.size() - 1).output())
                .isGreaterThanOrEqualTo(0.8);

        List<AgentInvocation> styleEditorAgentCalls = agenticScope.agentInvocations("editStory");
        assertThat(styleEditorAgentCalls).hasSize(testExitAtLoopEnd ? loopCount.get() : loopCount.get() - 1);
    }

    @Test
    void untyped_loop_with_output_tests() {
        StyleEditor styleEditor = AgenticServices.agentBuilder(StyleEditor.class)
                .chatModel(baseModel())
                .outputKey("story")
                .build();

        StyleScorer styleScorer = AgenticServices.agentBuilder(StyleScorer.class)
                .chatModel(baseModel())
                .outputKey("score")
                .build();

        UntypedAgent styleReviewLoop = AgenticServices.loopBuilder()
                .subAgents(styleScorer, styleEditor)
                .maxIterations(5)
                .exitCondition(agenticScope -> agenticScope.readState("score", 0.0) >= 0.8)
                .output(agenticScope -> agenticScope.readState("score", 0.0))
                .build();

        double score = (double) styleReviewLoop.invoke(Map.of(
                "story", "Once upon a time there were a wizard and a dragon",
                "style", "fantasy"));

        assertThat(score).isGreaterThanOrEqualTo(0.8);
    }

    @Test
    void conditional_agents_tests() {
        CategoryRouter routerAgent = AgenticServices.agentBuilder(CategoryRouter.class)
                .chatModel(baseModel())
                .outputKey("category")
                .build();

        MedicalExpert medicalExpert = spy(AgenticServices.agentBuilder(MedicalExpert.class)
                .chatModel(baseModel())
                .outputKey("response")
                .build());
        LegalExpert legalExpert = spy(AgenticServices.agentBuilder(LegalExpert.class)
                .chatModel(baseModel())
                .outputKey("response")
                .build());
        TechnicalExpert technicalExpert = spy(AgenticServices.agentBuilder(TechnicalExpert.class)
                .chatModel(baseModel())
                .outputKey("response")
                .build());

        UntypedAgent expertsAgent = AgenticServices.conditionalBuilder()
                .subAgents(
                        agenticScope ->
                                agenticScope.readState("category", RequestCategory.UNKNOWN) == RequestCategory.MEDICAL,
                        medicalExpert)
                .subAgents(
                        agenticScope ->
                                agenticScope.readState("category", RequestCategory.UNKNOWN) == RequestCategory.LEGAL,
                        legalExpert)
                .subAgents(
                        agenticScope -> agenticScope.readState("category", RequestCategory.UNKNOWN)
                                == RequestCategory.TECHNICAL,
                        technicalExpert)
                .build();

        ExpertRouterAgent expertRouterAgent = AgenticServices.sequenceBuilder(ExpertRouterAgent.class)
                .subAgents(routerAgent, expertsAgent)
                .outputKey("response")
                .build();

        System.out.println(expertRouterAgent.ask("I broke my leg what should I do"));

        verify(medicalExpert).medical("I broke my leg what should I do");
    }

    @Test
    void memory_agents_tests() {
        CategoryRouter routerAgent = AgenticServices.agentBuilder(CategoryRouter.class)
                .chatModel(baseModel())
                .outputKey("category")
                .build();

        MedicalExpertWithMemory medicalExpert = AgenticServices.agentBuilder(MedicalExpertWithMemory.class)
                .chatModel(baseModel())
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10))
                .outputKey("response")
                .build();
        TechnicalExpertWithMemory technicalExpert = AgenticServices.agentBuilder(TechnicalExpertWithMemory.class)
                .chatModel(baseModel())
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10))
                .outputKey("response")
                .build();
        LegalExpertWithMemory legalExpert = AgenticServices.agentBuilder(LegalExpertWithMemory.class)
                .chatModel(baseModel())
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10))
                .summarizedContext("medical", "technical")
                .outputKey("response")
                .build();

        UntypedAgent expertsAgent = AgenticServices.conditionalBuilder()
                .subAgents(
                        agenticScope ->
                                agenticScope.readState("category", RequestCategory.UNKNOWN) == RequestCategory.MEDICAL,
                        medicalExpert)
                .subAgents(
                        agenticScope -> agenticScope.readState("category", RequestCategory.UNKNOWN)
                                == RequestCategory.TECHNICAL,
                        technicalExpert)
                .subAgents(
                        agenticScope ->
                                agenticScope.readState("category", RequestCategory.UNKNOWN) == RequestCategory.LEGAL,
                        legalExpert)
                .build();

        ExpertRouterAgentWithMemory expertRouterAgent = AgenticServices.sequenceBuilder(
                        ExpertRouterAgentWithMemory.class)
                .subAgents(routerAgent, expertsAgent)
                .outputKey("response")
                .build();

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

        AgenticScopeRegistry registry = ((AgenticScopeOwner) expertRouterAgent).registry();
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

    @Test
    void parallel_agents_tests() {
        test_parallel_agents(false);
    }

    @Test
    void parallel_agents_with_default_executor_tests() {
        test_parallel_agents(true);
    }

    private void test_parallel_agents(boolean useDefaultExecutor) {
        FoodExpert foodExpert = AgenticServices.agentBuilder(FoodExpert.class)
                .chatModel(baseModel())
                .outputKey("meals")
                .build();

        MovieExpert movieExpert = AgenticServices.agentBuilder(MovieExpert.class)
                .chatModel(baseModel())
                .outputKey("movies")
                .build();

        var builder = AgenticServices.parallelBuilder(EveningPlannerAgent.class)
                .subAgents(foodExpert, movieExpert)
                .outputKey("plans")
                .output(agenticScope -> {
                    List<String> movies = agenticScope.readState("movies", List.of());
                    List<String> meals = agenticScope.readState("meals", List.of());

                    List<EveningPlan> moviesAndMeals = new ArrayList<>();
                    for (int i = 0; i < movies.size(); i++) {
                        if (i >= meals.size()) {
                            break;
                        }
                        moviesAndMeals.add(new EveningPlan(movies.get(i), meals.get(i)));
                    }
                    return moviesAndMeals;
                });

        if (!useDefaultExecutor) {
            builder.executor(Executors.newFixedThreadPool(2));
        }

        EveningPlannerAgent eveningPlannerAgent = builder.build();

        List<EveningPlan> plans = eveningPlannerAgent.plan("romantic");
        System.out.println(plans);
        assertThat(plans).hasSize(3);
    }

    @Test
    void async_agents_tests() {
        FoodExpert foodExpert = AgenticServices.agentBuilder(FoodExpert.class)
                .chatModel(baseModel())
                .async(true)
                .outputKey("meals")
                .build();

        MovieExpert movieExpert = AgenticServices.agentBuilder(MovieExpert.class)
                .chatModel(baseModel())
                .async(true)
                .outputKey("movies")
                .build();

        EveningPlannerAgent eveningPlannerAgent = AgenticServices.sequenceBuilder(EveningPlannerAgent.class)
                .subAgents(foodExpert, movieExpert)
                .outputKey("plans")
                .output(agenticScope -> {
                    List<String> movies = agenticScope.readState("movies", List.of());
                    List<String> meals = agenticScope.readState("meals", List.of());

                    List<EveningPlan> moviesAndMeals = new ArrayList<>();
                    for (int i = 0; i < movies.size(); i++) {
                        if (i >= meals.size()) {
                            break;
                        }
                        moviesAndMeals.add(new EveningPlan(movies.get(i), meals.get(i)));
                    }
                    return moviesAndMeals;
                })
                .build();

        List<EveningPlan> plans = eveningPlannerAgent.plan("romantic");
        System.out.println(plans);
        assertThat(plans).hasSize(3);
    }

    static class NotificationTool {

        private final String agentName;

        NotificationTool(String agentName) {
            this.agentName = agentName;
        }

        @Tool("notify that you are done")
        void done(AgenticScope agenticScope) {
            agenticScope.writeState(agentName, "done");
        }
    }

    public interface FoodExpertWithNotification {

        @UserMessage(
                """
            You are a great evening planner.
            Propose a list of 3 meals matching the given mood.
            The mood is {{mood}}.
            For each meal, just give the name of the meal.
            Provide a list with the 3 items and nothing else.

            When you are done and immediately before returning also call, once and only once,
            the provided tool, to notify that you have completed your task.
            """)
        @Agent
        List<String> findMeal(@V("mood") String mood);
    }

    public interface MovieExpertWithNotification {

        @UserMessage(
                """
            You are a great evening planner.
            Propose a list of 3 movies matching the given mood.
            The mood is {{mood}}.
            Provide a list with the 3 items and nothing else.

            When you are done and immediately before returning also call the provided tool,
            to notify that you have completed your task.
            """)
        @Agent
        List<String> findMovie(@V("mood") String mood);
    }

    @Test
    void async_untyped_agents_tests() {
        FoodExpertWithNotification foodExpert = AgenticServices.agentBuilder(FoodExpertWithNotification.class)
                .chatModel(baseModel())
                .tools(new NotificationTool("foodAgent"))
                .async(true)
                .outputKey("meals")
                .build();

        MovieExpertWithNotification movieExpert = AgenticServices.agentBuilder(MovieExpertWithNotification.class)
                .chatModel(baseModel())
                .tools(new NotificationTool("moviesAgent"))
                .async(true)
                .outputKey("movies")
                .build();

        UntypedAgent eveningPlannerAgent = AgenticServices.sequenceBuilder()
                .subAgents(foodExpert, movieExpert)
                .build();

        ResultWithAgenticScope<String> result = eveningPlannerAgent.invokeWithAgenticScope(Map.of("mood", "romantic"));
        assertThat(result.agenticScope().readState("foodAgent", "")).isEqualTo("done");
        assertThat(result.agenticScope().readState("moviesAgent", "")).isEqualTo("done");
    }

    public interface CreativeWriterDeclarative {

        @UserMessage(
                """
                You are a creative writer.
                Generate a draft of a story long no more than 3 sentence around the given topic.
                Return only the story and nothing else.
                The topic is {{topic}}.
                """)
        @Agent(description = "Generate a story based on the given topic", outputKey = "story")
        String generateStory(@V("topic") String topic);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return baseModel();
        }
    }

    public interface AudienceEditorDeclarative {

        @UserMessage(
                """
            You are a professional editor.
            Analyze and rewrite the following story to better align with the target audience of {{audience}}.
            Return only the story and nothing else.
            The story is "{{story}}".
            """)
        @Agent(description = "Edit a story to better fit a given audience", outputKey = "story")
        String editStory(@V("story") String story, @V("audience") String audience);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return baseModel();
        }
    }

    @Test
    void sequential_declarative_agents_as_classes_tests() {
        UntypedAgent novelCreator = AgenticServices.sequenceBuilder()
                .subAgents(CreativeWriterDeclarative.class, AudienceEditorDeclarative.class)
                .outputKey("story")
                .build();

        Map<String, Object> input = Map.of(
                "topic", "dragons and wizards",
                "audience", "young adults");

        String story = (String) novelCreator.invoke(input);
        System.out.println(story);
        assertThat(story).containsIgnoringCase("dragon").containsIgnoringCase("wizard");
    }
}
