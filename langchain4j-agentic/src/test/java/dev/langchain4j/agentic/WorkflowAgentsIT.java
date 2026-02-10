package dev.langchain4j.agentic;

import static dev.langchain4j.agentic.Models.baseModel;
import static dev.langchain4j.agentic.Models.throwingModel;
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
import dev.langchain4j.agentic.Agents.CreativeWriterWithArgMessage;
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
import dev.langchain4j.agentic.Agents.ReviewedWriter;
import dev.langchain4j.agentic.Agents.StyleEditor;
import dev.langchain4j.agentic.Agents.StyleScorer;
import dev.langchain4j.agentic.Agents.StyledWriter;
import dev.langchain4j.agentic.Agents.TechnicalExpert;
import dev.langchain4j.agentic.Agents.TechnicalExpertWithMemory;
import dev.langchain4j.agentic.agent.AgentInvocationException;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.agent.MissingArgumentException;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.observability.AgentInvocation;
import dev.langchain4j.agentic.observability.AgentMonitor;
import dev.langchain4j.agentic.observability.AgentRequest;
import dev.langchain4j.agentic.observability.AgentResponse;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.MonitoredExecution;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemConfigurationException;
import dev.langchain4j.agentic.internal.AgenticScopeOwner;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.AgenticScopePersister;
import dev.langchain4j.agentic.scope.AgenticScopeRegistry;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.agentic.workflow.ConditionalAgentInstance;
import dev.langchain4j.agentic.workflow.HumanInTheLoop;
import dev.langchain4j.agentic.workflow.LoopAgentInstance;
import dev.langchain4j.agentic.workflow.impl.LoopPlanner;
import dev.langchain4j.agentic.workflow.impl.SequentialPlanner;
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
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "GOOGLE_AI_GEMINI_API_KEY", matches = ".+")
public class WorkflowAgentsIT {

    public interface CreativeWriterWithModel extends CreativeWriter {
        @ChatModelSupplier
        static ChatModel chatModel() {
            return baseModel();
        }
    }

    @Test
    void sequential_agents_tests() {
        check_sequential_agents(false, false);
    }

    @Test
    void sequential_agents_using_declarative_api_tests() {
        check_sequential_agents(true, false);
    }

    @Test
    void sequential_agents_as_planner() {
        check_sequential_agents(false, true);
    }

    void check_sequential_agents(boolean useDeclarativeAPI, boolean asPlanner) {
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

        UntypedAgent novelCreator = asPlanner ?
                AgenticServices.plannerBuilder()
                        .subAgents(creativeWriter, audienceEditor, styleEditor)
                        .outputKey("story")
                        .planner(SequentialPlanner::new)
                        .build() :
                AgenticServices.sequenceBuilder()
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
    void agent_with_programmatic_user_message_test() {
        // the UserMessage is passed as an argument when invoking the agent

        CreativeWriterWithArgMessage creativeWriter = AgenticServices.agentBuilder(CreativeWriterWithArgMessage.class)
                .chatModel(baseModel())
                .outputKey("story")
                .build();

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
                "userMessage", """
                               You are a creative writer.
                               Generate a draft of a story long no more than 3 sentence around the given topic.
                               Return only the story and nothing else.
                               The topic is {{topic}}.
                               """,
                "topic", "dragons and wizards",
                "style", "fantasy",
                "audience", "young adults");

        String story = (String) novelCreator.invoke(input);
        assertThat(story).containsIgnoringCase("dragon");

        verify(audienceEditor).editStory(any(), eq("young adults"));
        verify(styleEditor).editStory(any(), eq("fantasy"));
    }

    @Test
    void agent_with_default_value_test() {
        // the UserMessage is set as a default value in the agent builder

        CreativeWriterWithArgMessage creativeWriter = AgenticServices.agentBuilder(CreativeWriterWithArgMessage.class)
                .chatModel(baseModel())
                .defaultKeyValue("userMessage", """
                               You are a creative writer.
                               Generate a draft of a story long no more than 3 sentence around the given topic.
                               Return only the story and nothing else.
                               The topic is {{topic}}.
                               """)
                .outputKey("story")
                .build();

        AudienceEditor audienceEditor = spy(AgenticServices.agentBuilder(AudienceEditor.class)
                .chatModel(baseModel())
                .outputKey("story")
                .build());

        StyleEditor styleEditor = spy(AgenticServices.agentBuilder(StyleEditor.class)
                .chatModel(baseModel())
                .outputKey("story")
                .build());

        ReviewedWriter novelCreator = AgenticServices.sequenceBuilder(ReviewedWriter.class)
                        .subAgents(creativeWriter, audienceEditor, styleEditor)
                        .outputKey("story")
                        .build();

        String story = novelCreator.writeStory("dragons and wizards", "young adults", "fantasy");
        assertThat(story).containsIgnoringCase("dragon");

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
        AtomicReference<String> hitlResult = new AtomicReference<>();

        HumanInTheLoop humanInTheLoop = AgenticServices.humanInTheLoopBuilder()
                .description("An agent that asks the audience for the story")
                .outputKey("audience")
                .async(true)
                .responseProvider(scope -> {
                    request.set("Which audience for topic " + scope.readState("topic") + "?");
                    try {
                        barrier.await();
                    } catch (InterruptedException | BrokenBarrierException e) {
                        throw new RuntimeException(e);
                    }

                    // check that the creativeWriter was already invoked
                    verify(creativeWriter).generateStory("dragons and wizards");

                    return "young adults";
                })
                .listener(new AgentListener() {
                    @Override
                    public void afterAgentInvocation(final AgentResponse agentResponse) {
                        hitlResult.set(agentResponse.output().toString());
                    }
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

        AgentInstance sequenceInstance = ((AgentInstance) novelCreator);
        assertThat(sequenceInstance.type()).isSameAs(UntypedAgent.class);
        assertThat(sequenceInstance.plannerType()).isSameAs(SequentialPlanner.class);
        assertThat(sequenceInstance.outputKey()).isEqualTo("story");
        assertThat(sequenceInstance.subagents()).hasSize(5);

        assertThat(sequenceInstance.subagents().get(0).topology()).isEqualTo(AgenticSystemTopology.HUMAN_IN_THE_LOOP);
        assertThat(sequenceInstance.subagents().get(1).topology()).isEqualTo(AgenticSystemTopology.AI_AGENT);
        assertThat(sequenceInstance.subagents().get(2).topology()).isEqualTo(AgenticSystemTopology.NON_AI_AGENT);
        assertThat(sequenceInstance.subagents().get(3).topology()).isEqualTo(AgenticSystemTopology.AI_AGENT);
        assertThat(sequenceInstance.subagents().get(4).topology()).isEqualTo(AgenticSystemTopology.NON_AI_AGENT);

        Map<String, Object> input = Map.of("topic", "dragons and wizards");

        String story = (String) novelCreator.invoke(input);
        System.out.println(story);

        assertThat(request.get()).isEqualTo("Which audience for topic dragons and wizards?");
        assertThat(audience.get()).isEqualTo("young adults");
        assertThat(hitlResult.get()).isEqualTo("young adults");
        verify(audienceEditor).editStory(any(), eq("young adults"));
    }

    @Test
    void loop_agents_tests() {
        class AllLevelsListener implements AgentListener {
            int invocationsCounter = 0;
            int loopCounter = 0;
            double finalScore = 0.0;
            Object createdScopeId;
            Object destroyedScopeId;

            @Override
            public void afterAgentInvocation(AgentResponse response) {
                invocationsCounter++;
                if (StyleScorer.class.isAssignableFrom(response.agent().type())) {
                    loopCounter++;
                    finalScore = (double) response.output();
                }
            }

            @Override
            public void afterAgenticScopeCreated(AgenticScope agenticScope) {
                createdScopeId = agenticScope.memoryId();
            }

            @Override
            public void beforeAgenticScopeDestroyed(AgenticScope agenticScope) {
                destroyedScopeId = agenticScope.memoryId();
            }

            @Override
            public boolean inheritedBySubagents() {
                return true;
            }
        }

        class TopLevelListener implements AgentListener {
            int invocationsCounter = 0;

            @Override
            public void afterAgentInvocation(AgentResponse response) {
                invocationsCounter++;
                if (StyleScorer.class.isAssignableFrom(response.agent().type())) {
                    throw new RuntimeException("It should not be called for a subagent");
                }
            }
        }

        class WriterListener implements AgentListener {
            Object requestedTopic;

            @Override
            public void beforeAgentInvocation(AgentRequest request) {
                requestedTopic = request.inputs().get("topic");
            }
        }

        TopLevelListener topLevelListener = new TopLevelListener();
        AllLevelsListener allLevelsListener = new AllLevelsListener();
        WriterListener writerListener = new WriterListener();
        AgentMonitor monitor = new AgentMonitor();

        CreativeWriter creativeWriter = AgenticServices.agentBuilder(CreativeWriter.class)
                .listener(writerListener)
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
                .name("reviewLoop")
                .subAgents(styleScorer, styleEditor)
                .listener(new AgentListener() {}) // empty listener to test inheritance
                .maxIterations(5)
                .exitCondition(agenticScope -> agenticScope.readState("score", 0.0) >= 0.8)
                .build();

        UntypedAgent styledWriter = AgenticServices.sequenceBuilder()
                .subAgents(creativeWriter, styleReviewLoop)
                .listener(topLevelListener)
                .listener(allLevelsListener)
                .listener(monitor)
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

        assertThat(topLevelListener.invocationsCounter).isEqualTo(1);
        assertThat(writerListener.requestedTopic).isEqualTo("dragons and wizards");
        assertThat(allLevelsListener.finalScore).isGreaterThanOrEqualTo(0.8);
        assertThat(allLevelsListener.createdScopeId).isNotNull().isEqualTo(allLevelsListener.destroyedScopeId);
        assertThat(allLevelsListener.invocationsCounter).isEqualTo((allLevelsListener.loopCounter*2)-1 + 3);

        assertThat(monitor.successfulExecutionsFor(agenticScope)).hasSize(1);
        MonitoredExecution execution = monitor.successfulExecutionsFor(agenticScope).get(0);
        assertThat(execution.done()).isTrue();
        assertThat(execution.ongoingInvocations()).isEmpty();
        AgentInvocation topLevelInvocation = execution.topLevelInvocations();
        assertThat(topLevelInvocation.inputs()).containsKey("topic").containsKey("style");

        assertThat(topLevelInvocation.nestedInvocations()).hasSize(2);
        assertThat(topLevelInvocation.nestedInvocations().get(0).agent().name()).isEqualTo("generateStory");
        assertThat(topLevelInvocation.nestedInvocations().get(1).agent().name()).isEqualTo("reviewLoop");

        System.out.println(execution);
    }

    @Test
    void loop_agents_with_error_tests() {
        AgentMonitor monitor = new AgentMonitor();

        CreativeWriter creativeWriter = AgenticServices.agentBuilder(CreativeWriter.class)
                .chatModel(baseModel())
                .outputKey("story")
                .build();

        StyleEditor styleEditor = AgenticServices.agentBuilder(StyleEditor.class)
                .chatModel(baseModel())
                .outputKey("story")
                .build();

        StyleScorer styleScorer = AgenticServices.agentBuilder(StyleScorer.class)
                .chatModel(throwingModel())
                .outputKey("score")
                .build();

        UntypedAgent styleReviewLoop = AgenticServices.loopBuilder()
                .name("reviewLoop")
                .subAgents(styleScorer, styleEditor)
                .maxIterations(5)
                .exitCondition(agenticScope -> agenticScope.readState("score", 0.0) >= 0.8)
                .build();

        UntypedAgent styledWriter = AgenticServices.sequenceBuilder()
                .subAgents(creativeWriter, styleReviewLoop)
                .listener(monitor)
                .outputKey("story")
                .build();

        Map<String, Object> input = Map.of(
                "topic", "dragons and wizards",
                "style", "comedy");

        assertThrows(AgentInvocationException.class, () -> styledWriter.invokeWithAgenticScope(input));

        assertThat(monitor.successfulExecutions()).isEmpty();
        MonitoredExecution failed = monitor.failedExecutions().get(0);
        assertThat(failed.done()).isFalse();
        assertThat(failed.hasError()).isTrue();
        assertThat(failed.error().error()).isInstanceOf(AgentInvocationException.class);
        assertThat(failed.error().agent().name()).isEqualTo("scoreStyle");
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
                .exitCondition("score greater than 0.8", (agenticScope, loop) -> {
                    loopCount.set(loop);
                    return agenticScope.readState("score", 0.0) >= 0.8;
                })
                .build();

        StyledWriter styledWriter = AgenticServices.sequenceBuilder(StyledWriter.class)
                .subAgents(creativeWriter, styleReviewLoop)
                .outputKey("story")
                .build();

        assertThat(styledWriter.name()).isEqualTo("writeStoryWithStyle");
        assertThat(styledWriter.subagents()).hasSize(2);
        assertThat(styledWriter.type()).isSameAs(StyledWriter.class);
        assertThat(styledWriter.plannerType()).isSameAs(SequentialPlanner.class);

        AgentInstance creativeWriterInstance = styledWriter.subagents().get(0);
        assertThat(creativeWriterInstance.name()).isEqualTo("generateStory");
        assertThat(creativeWriterInstance.type()).isSameAs(CreativeWriter.class);
        assertThat(creativeWriterInstance.plannerType()).isNull();

        AgentInstance loopAgent = styledWriter.subagents().get(1);
        assertThat(loopAgent.topology()).isEqualTo(AgenticSystemTopology.LOOP);
        assertThat(loopAgent.type()).isSameAs(UntypedAgent.class);
        assertThat(loopAgent.plannerType()).isSameAs(LoopPlanner.class);
        LoopAgentInstance loopInstance = loopAgent.as(LoopAgentInstance.class);
        assertThat(loopInstance.subagents()).hasSize(2);
        assertThat(loopInstance.maxIterations()).isEqualTo(5);
        assertThat(loopInstance.testExitAtLoopEnd()).isEqualTo(testExitAtLoopEnd);
        assertThat(loopInstance.exitCondition()).isEqualTo("score greater than 0.8");

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

        List<dev.langchain4j.agentic.scope.AgentInvocation> scoreAgentInvocations = agenticScope.agentInvocations(StyleScorer.class);
        assertThat(scoreAgentInvocations).hasSizeBetween(1, 5).hasSize(loopCount.get());
        System.out.println("Score agent invocations: " + scoreAgentInvocations);
        assertThat((Double) scoreAgentInvocations.get(scoreAgentInvocations.size() - 1).output())
                .isGreaterThanOrEqualTo(0.8);

        List<dev.langchain4j.agentic.scope.AgentInvocation> styleEditorAgentInvocations = agenticScope.agentInvocations(StyleEditor.class);
        assertThat(styleEditorAgentInvocations).hasSize(testExitAtLoopEnd ? loopCount.get() : loopCount.get() - 1);
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

    interface ExpertRouterAgentInstance extends ExpertRouterAgent, AgentInstance { }

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
                .subAgents("category is medical",
                        agenticScope ->
                                agenticScope.readState("category", RequestCategory.UNKNOWN) == RequestCategory.MEDICAL,
                        medicalExpert)
                .subAgents("category is legal",
                        agenticScope ->
                                agenticScope.readState("category", RequestCategory.UNKNOWN) == RequestCategory.LEGAL,
                        legalExpert)
                .subAgents(
                        agenticScope -> agenticScope.readState("category", RequestCategory.UNKNOWN)
                                == RequestCategory.TECHNICAL,
                        technicalExpert)
                .build();

        var agentInstance = AgenticServices.sequenceBuilder(ExpertRouterAgentInstance.class)
                .subAgents(routerAgent, expertsAgent)
                .outputKey("response")
                .build();

        assertThat(agentInstance.name()).isEqualTo("ask");
        assertThat(agentInstance.outputType()).isEqualTo(String.class);
        assertThat(agentInstance.outputKey()).isEqualTo("response");
        assertThat(agentInstance.topology()).isEqualTo(AgenticSystemTopology.SEQUENCE);
        assertThat(agentInstance.arguments()).hasSize(1);
        assertThat(agentInstance.arguments().get(0).name()).isEqualTo("request");
        assertThat(agentInstance.arguments().get(0).type()).isEqualTo(String.class);
        assertThat(agentInstance.subagents()).hasSize(2);

        AgentInstance routerAgentInstance = agentInstance.subagents().get(0);
        assertThat(routerAgentInstance.name()).isEqualTo("classify");
        assertThat(routerAgentInstance.agentId()).isEqualTo("classify$0");
        assertThat(routerAgentInstance.outputType()).isEqualTo(RequestCategory.class);
        assertThat(routerAgentInstance.outputKey()).isEqualTo("category");
        assertThat(routerAgentInstance.topology()).isEqualTo(AgenticSystemTopology.AI_AGENT);
        assertThat(routerAgentInstance.arguments()).hasSize(1);
        assertThat(routerAgentInstance.arguments().get(0).name()).isEqualTo("request");
        assertThat(routerAgentInstance.arguments().get(0).type()).isEqualTo(String.class);
        assertThat(routerAgentInstance.subagents()).isEmpty();
        assertThat(routerAgentInstance.parent().agentId()).isEqualTo(agentInstance.agentId());

        AgentInstance conditionalAgentInstance = agentInstance.subagents().get(1);
        assertThat(conditionalAgentInstance.outputType()).isEqualTo(Object.class);
        assertThat(conditionalAgentInstance.outputKey()).isNull();
        assertThat(conditionalAgentInstance.topology()).isEqualTo(AgenticSystemTopology.ROUTER);
        assertThat(conditionalAgentInstance.arguments()).isEmpty(); // untyped agent does not know its arguments
        assertThat(conditionalAgentInstance.subagents()).hasSize(3);
        assertThat(conditionalAgentInstance.parent().agentId()).isEqualTo(agentInstance.agentId());

        ConditionalAgentInstance conditionalInstance = conditionalAgentInstance.as(ConditionalAgentInstance.class);
        assertThat(conditionalInstance.conditionalSubagents()).hasSize(3);
        assertThat(conditionalInstance.conditionalSubagents().get(0).condition()).isEqualTo("category is medical");
        assertThat(conditionalInstance.conditionalSubagents().get(1).condition()).isEqualTo("category is legal");
        assertThat(conditionalInstance.conditionalSubagents().get(2).condition()).isEqualTo("<unknown>");

        checkExpertAgent(conditionalAgentInstance, "medical", 0);
        checkExpertAgent(conditionalAgentInstance, "legal", 1);
        checkExpertAgent(conditionalAgentInstance, "technical", 2);

        System.out.println(agentInstance.ask("I broke my leg what should I do"));

        verify(medicalExpert).medical("I broke my leg what should I do");
    }

    private static void checkExpertAgent(AgentInstance parent, String name, int index) {
        AgentInstance expertAgentInstance = parent.subagents().get(index);
        assertThat(expertAgentInstance.name()).isEqualTo(name);
        assertThat(expertAgentInstance.agentId()).isEqualTo(name + "$" + index + "$1");
        assertThat(expertAgentInstance.outputType()).isEqualTo(String.class);
        assertThat(expertAgentInstance.outputKey()).isEqualTo("response");
        assertThat(expertAgentInstance.topology()).isEqualTo(AgenticSystemTopology.AI_AGENT);
        assertThat(expertAgentInstance.arguments()).hasSize(1);
        assertThat(expertAgentInstance.arguments().get(0).name()).isEqualTo("request");
        assertThat(expertAgentInstance.arguments().get(0).type()).isEqualTo(String.class);
        assertThat(expertAgentInstance.subagents()).isEmpty();
        assertThat(expertAgentInstance.parent().agentId()).isEqualTo(parent.agentId());
    }

    @Test
    void no_matching_condition_tests() {
        CategoryRouter routerAgent = AgenticServices.agentBuilder(CategoryRouter.class)
                .chatModel(baseModel())
                .outputKey("category")
                .build();

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
                                agenticScope.readState("category", RequestCategory.UNKNOWN) == RequestCategory.LEGAL,
                        legalExpert)
                .subAgents(
                        agenticScope -> agenticScope.readState("category", RequestCategory.UNKNOWN)
                                == RequestCategory.TECHNICAL,
                        technicalExpert)
                .build();

        var agentInstance = AgenticServices.sequenceBuilder(ExpertRouterAgentInstance.class)
                .subAgents(routerAgent, expertsAgent)
                .outputKey("response")
                .build();

        assertThat(agentInstance.ask("I broke my leg what should I do")).isNull();
    }

    @Test
    void agents_validation_tests() {
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
                .outputKey("category")
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

        assertThat(assertThrows(AgenticSystemConfigurationException.class, () ->
                AgenticServices.sequenceBuilder(ExpertRouterAgent.class)
                    .subAgents(routerAgent, expertsAgent)
                    .outputKey("response")
                    .build()))
            .hasMessageContaining("category");
    }

    @Test
    void memory_agents_tests() {
        AgentMonitor monitor = new AgentMonitor();

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
                .name("router")
                .subAgents(agenticScope -> agenticScope.readState("category", RequestCategory.UNKNOWN) == RequestCategory.MEDICAL, medicalExpert)
                .subAgents(agenticScope -> agenticScope.readState("category", RequestCategory.UNKNOWN) == RequestCategory.TECHNICAL, technicalExpert)
                .subAgents(agenticScope -> agenticScope.readState("category", RequestCategory.UNKNOWN) == RequestCategory.LEGAL, legalExpert)
                .build();

        ExpertRouterAgentWithMemory expertRouterAgent = AgenticServices.sequenceBuilder(
                        ExpertRouterAgentWithMemory.class)
                .subAgents(routerAgent, expertsAgent)
                .listener(monitor)
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

        checkMonitoredExecution(monitor, "1", "medical");
        checkMonitoredExecution(monitor, "2", "technical");

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

    private static void checkMonitoredExecution(AgentMonitor monitor, Object memoryId, String expertName) {
        List<MonitoredExecution> executions1 = monitor.successfulExecutionsFor(memoryId);
        assertThat(executions1).hasSize(1);
        List<AgentInvocation> sequenceInvocations = executions1.get(0).topLevelInvocations().nestedInvocations();
        assertThat(sequenceInvocations).hasSize(2);
        assertThat(sequenceInvocations.get(0).agent().name()).isEqualTo("classify");
        assertThat(sequenceInvocations.get(1).agent().name()).isEqualTo("router");
        List<AgentInvocation> routerInvocations = sequenceInvocations.get(1).nestedInvocations();
        assertThat(routerInvocations).hasSize(1);
        assertThat(routerInvocations.get(0).agent().name()).isEqualTo(expertName);
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
