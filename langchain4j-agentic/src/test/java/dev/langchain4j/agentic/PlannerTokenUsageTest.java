package dev.langchain4j.agentic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agentic.observability.AgentInvocation;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentMonitor;
import dev.langchain4j.agentic.observability.PlannerModelResponse;
import dev.langchain4j.agentic.planner.Action;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.InitPlanningContext;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.planner.PlanningContext;
import dev.langchain4j.agentic.supervisor.SupervisorAgent;
import dev.langchain4j.agentic.supervisor.SupervisorContextStrategy;
import dev.langchain4j.agentic.supervisor.SupervisorResponseStrategy;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.mock.DecisionModelMock;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.decision.response.YesNoAnswer;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.output.OutputParsingException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

class PlannerTokenUsageTest {

    public interface Assistant {

        @UserMessage("Answer {{question}}")
        @Agent(outputKey = "answer")
        String answer(@V("question") String question);
    }

    public interface Pattern {

        @Agent
        String run(@V("question") String question);
    }

    public interface Workflow {

        @Agent
        String process(@V("question") String question);
    }

    /**
     * A planner invoking its subagents in sequence, which calls its chat model before each step and once more before
     * ending, and reports every response.
     */
    static class ChatModelPlanner implements Planner {

        private final ChatModel chatModel;
        private List<AgentInstance> subagents;
        private int next;

        ChatModelPlanner(ChatModel chatModel) {
            this.chatModel = chatModel;
        }

        @Override
        public void init(InitPlanningContext initPlanningContext) {
            this.subagents = initPlanningContext.subagents();
        }

        @Override
        public Action nextAction(PlanningContext planningContext) {
            planningContext.reportChatModelResponse(
                    chatModel.chat(dev.langchain4j.data.message.UserMessage.from("What is the next step?")));
            return next < subagents.size() ? call(subagents.get(next++)) : done();
        }
    }

    /** A planner asking its decision model, once, whether to invoke its only subagent, and reporting the response. */
    static class DecisionModelPlanner implements Planner {

        private final DecisionModel decisionModel;
        private List<AgentInstance> subagents;

        DecisionModelPlanner(DecisionModel decisionModel) {
            this.decisionModel = decisionModel;
        }

        @Override
        public void init(InitPlanningContext initPlanningContext) {
            this.subagents = initPlanningContext.subagents();
        }

        @Override
        public Action firstAction(PlanningContext planningContext) {
            planningContext.reportDecisionModelResponse(decisionModel.decide(DecisionRequest.builder()
                    .input("question")
                    .question("invoke", YesNoQuestion.from("Should the agent be invoked?"))
                    .build()));
            return call(subagents.get(0));
        }

        @Override
        public Action nextAction(PlanningContext planningContext) {
            return done();
        }
    }

    private static ChatModelMock plannerModel() {
        return ChatModelMock.thatAlwaysResponds("go on");
    }

    private static Assistant assistant() {
        return AgenticServices.agentBuilder(Assistant.class)
                .chatModel(ChatModelMock.thatAlwaysResponds("It is noon").withTokenUsage(new TokenUsage(7, 1)))
                .build();
    }

    private static Pattern pattern(Planner planner, AgentListener listener) {
        return AgenticServices.plannerBuilder(Pattern.class)
                .name("pattern")
                .subAgents(assistant())
                .outputKey("answer")
                .planner(() -> planner)
                .listener(listener)
                .build();
    }

    private static AgentListener recording(List<PlannerModelResponse> responses) {
        return new AgentListener() {
            @Override
            public void onPlannerModelResponse(PlannerModelResponse plannerModelResponse) {
                responses.add(plannerModelResponse);
            }
        };
    }

    private static AgentInvocation topLevelInvocation(AgentMonitor monitor) {
        return monitor.successfulExecutions().get(0).topLevelInvocations();
    }

    @Test
    void planner_reports_each_response_of_its_chat_model_to_the_listeners_of_its_agent() {
        List<PlannerModelResponse> responses = new CopyOnWriteArrayList<>();
        ChatModelMock model = plannerModel()
                .withModelNames("planning-model", "planning-model")
                .withTokenUsages(new TokenUsage(100, 20), new TokenUsage(30, 5));

        pattern(new ChatModelPlanner(model), recording(responses)).run("what time is it?");

        assertThat(responses).hasSize(2);
        assertThat(responses).extracting(PlannerModelResponse::agentName).containsOnly("pattern");
        assertThat(responses)
                .extracting(PlannerModelResponse::tokenUsage)
                .containsExactly(new TokenUsage(100, 20), new TokenUsage(30, 5));
        assertThat(responses).extracting(PlannerModelResponse::modelName).containsOnly("planning-model");
        PlannerModelResponse first = responses.get(0);
        assertThat(first.agenticScope()).isNotNull();
        assertThat(first.agentId()).isEqualTo(first.agent().agentId());
    }

    @Test
    void planner_reports_the_response_of_its_decision_model_to_the_listeners_of_its_agent() {
        List<PlannerModelResponse> responses = new CopyOnWriteArrayList<>();
        DecisionModelMock model = DecisionModelMock.thatAlwaysAnswers(
                        Map.of("invoke", YesNoAnswer.builder().probability(0.9).build()))
                .withModelName("decision-model")
                .withTokenUsage(new TokenUsage(40, 2));

        pattern(new DecisionModelPlanner(model), recording(responses)).run("what time is it?");

        assertThat(responses).hasSize(1);
        PlannerModelResponse response = responses.get(0);
        assertThat(response.agentName()).isEqualTo("pattern");
        assertThat(response.tokenUsage()).isEqualTo(new TokenUsage(40, 2));
        assertThat(response.modelName()).isEqualTo("decision-model");
    }

    @Test
    void planner_reporting_no_response_notifies_nothing() {
        List<PlannerModelResponse> responses = new CopyOnWriteArrayList<>();
        Planner planner = new Planner() {
            @Override
            public Action nextAction(PlanningContext planningContext) {
                planningContext.reportChatModelResponse(null);
                planningContext.reportDecisionModelResponse(null);
                return done();
            }
        };

        pattern(planner, recording(responses)).run("what time is it?");

        assertThat(responses).isEmpty();
    }

    @Test
    void monitor_counts_the_model_calls_of_a_planner_on_its_own_invocation_and_not_those_of_its_subagents() {
        AgentMonitor monitor = new AgentMonitor();
        ChatModelMock model = plannerModel().withTokenUsages(new TokenUsage(100, 20), new TokenUsage(30, 5));

        pattern(new ChatModelPlanner(model), monitor).run("what time is it?");

        AgentInvocation invocation = topLevelInvocation(monitor);
        assertThat(invocation.tokenUsage()).contains(new TokenUsage(130, 25));
        assertThat(invocation.totalTokenCount()).isEqualTo(155);
        assertThat(invocation.nestedInvocations())
                .extracting(AgentInvocation::totalTokenCount)
                .containsExactly(8);
    }

    @Test
    void monitor_counts_the_decision_calls_of_a_planner_without_reporting_a_decision_response() {
        AgentMonitor monitor = new AgentMonitor();
        DecisionModelMock model = DecisionModelMock.thatAlwaysAnswers(
                        Map.of("invoke", YesNoAnswer.builder().probability(0.9).build()))
                .withModelName("decision-model")
                .withTokenUsage(new TokenUsage(40, 2));

        pattern(new DecisionModelPlanner(model), monitor).run("what time is it?");

        AgentInvocation invocation = topLevelInvocation(monitor);
        assertThat(invocation.tokenUsage()).contains(new TokenUsage(40, 2));
        assertThat(invocation.modelName()).contains("decision-model");
        // the decision response is the answer of a decision agent, not a decision taken by a planner
        assertThat(invocation.decisionResponse()).isEmpty();
    }

    @Test
    void planner_whose_model_calls_used_the_same_model_reports_that_model() {
        AgentMonitor monitor = new AgentMonitor();
        ChatModelMock model = plannerModel().withModelNames("planning-model", "planning-model");

        pattern(new ChatModelPlanner(model), monitor).run("what time is it?");

        assertThat(topLevelInvocation(monitor).modelName()).contains("planning-model");
    }

    @Test
    void planner_whose_model_calls_used_different_models_reports_its_tokens_without_a_model() {
        AgentMonitor monitor = new AgentMonitor();
        ChatModelMock model = plannerModel()
                .withModelNames("first-model", "second-model")
                .withTokenUsages(new TokenUsage(100, 20), new TokenUsage(30, 5));

        pattern(new ChatModelPlanner(model), monitor).run("what time is it?");

        AgentInvocation invocation = topLevelInvocation(monitor);
        assertThat(invocation.tokenUsage()).contains(new TokenUsage(130, 25));
        assertThat(invocation.modelName()).isEmpty();
    }

    @Test
    void planner_whose_model_reports_no_token_usage_has_no_tokens() {
        AgentMonitor monitor = new AgentMonitor();

        pattern(new ChatModelPlanner(plannerModel()), monitor).run("what time is it?");

        AgentInvocation invocation = topLevelInvocation(monitor);
        assertThat(invocation.tokenUsage()).isEmpty();
        assertThat(invocation.totalTokenCount()).isZero();
    }

    @Test
    void planner_nested_in_a_workflow_keeps_its_tokens_on_its_own_invocation() {
        AgentMonitor monitor = new AgentMonitor();
        ChatModelMock model = plannerModel().withTokenUsages(new TokenUsage(100, 20), new TokenUsage(30, 5));
        Pattern pattern = pattern(new ChatModelPlanner(model), null);

        AgenticServices.sequenceBuilder(Workflow.class)
                .subAgents(pattern)
                .outputKey("answer")
                .listener(monitor)
                .build()
                .process("what time is it?");

        AgentInvocation sequence = topLevelInvocation(monitor);
        assertThat(sequence.tokenUsage()).isEmpty();
        assertThat(sequence.nestedInvocations())
                .extracting(AgentInvocation::totalTokenCount)
                .containsExactly(155);
    }

    @Test
    void listener_failing_on_a_planner_model_response_does_not_stop_the_planner() {
        AgentListener failing = new AgentListener() {
            @Override
            public void onPlannerModelResponse(PlannerModelResponse plannerModelResponse) {
                throw new IllegalStateException("listener failed");
            }
        };

        String answer = pattern(new ChatModelPlanner(plannerModel()), failing).run("what time is it?");

        assertThat(answer).isEqualTo("It is noon");
    }

    @Test
    void planner_failing_after_a_model_call_keeps_the_tokens_it_spent_in_the_monitor() {
        List<PlannerModelResponse> responses = new CopyOnWriteArrayList<>();
        AgentMonitor monitor = new AgentMonitor();
        ChatModelMock model = ChatModelMock.thatResponds(request -> {
                    if (!responses.isEmpty()) {
                        throw new IllegalStateException("model failed");
                    }
                    return AiMessage.from("go on");
                })
                .withModelNames("planning-model")
                .withTokenUsage(new TokenUsage(100, 20));
        Pattern pattern = AgenticServices.plannerBuilder(Pattern.class)
                .name("pattern")
                .subAgents(assistant())
                .outputKey("answer")
                .planner(() -> new ChatModelPlanner(model))
                .listener(recording(responses))
                .listener(monitor)
                .build();

        assertThatThrownBy(() -> pattern.run("what time is it?"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("model failed");

        assertThat(responses).extracting(PlannerModelResponse::tokenUsage).containsExactly(new TokenUsage(100, 20));
        // a failed invocation is never done, but what its planner spent before failing is still reported
        AgentInvocation failed = monitor.failedExecutions().get(0).topLevelInvocations();
        assertThat(failed.done()).isFalse();
        assertThat(failed.tokenUsage()).contains(new TokenUsage(100, 20));
        assertThat(failed.totalTokenCount()).isEqualTo(120);
        assertThat(failed.modelName()).contains("planning-model");
        assertThat(failed.toString()).contains("tokens=120");
    }

    @Test
    void planner_whose_subagent_fails_keeps_the_tokens_it_spent_in_the_monitor() {
        AgentMonitor monitor = new AgentMonitor();
        ChatModelMock model = plannerModel().withTokenUsage(new TokenUsage(100, 20));
        Assistant failingAssistant = AgenticServices.agentBuilder(Assistant.class)
                .chatModel(ChatModelMock.thatAlwaysThrowsException())
                .build();
        Pattern pattern = AgenticServices.plannerBuilder(Pattern.class)
                .name("pattern")
                .subAgents(failingAssistant)
                .outputKey("answer")
                .planner(() -> new ChatModelPlanner(model))
                .listener(monitor)
                .build();

        assertThatThrownBy(() -> pattern.run("what time is it?")).isInstanceOf(RuntimeException.class);

        AgentInvocation failed = monitor.failedExecutions().get(0).topLevelInvocations();
        assertThat(failed.done()).isFalse();
        assertThat(failed.tokenUsage()).contains(new TokenUsage(100, 20));
        // the response of the model of a failed AI agent is lost with its failure, so it has no tokens to report
        AgentInvocation failedAssistant = failed.nestedInvocations().get(0);
        assertThat(failedAssistant.done()).isFalse();
        assertThat(failedAssistant.tokenUsage()).isEmpty();
        assertThat(failedAssistant.totalTokenCount()).isZero();
        assertThat(failedAssistant.modelName()).isEmpty();
    }

    // ── Supervisor ──

    private static final AiMessage INVOKE_ASSISTANT = AiMessage.from("""
            {"agentName": "answer", "arguments": {"question": "what time is it?"}}
            """);
    private static final AiMessage DONE = AiMessage.from("""
            {"agentName": "done", "arguments": {"response": "The assistant said it is noon"}}
            """);

    private static SupervisorAgent supervisor(
            ChatModel model,
            SupervisorResponseStrategy responseStrategy,
            SupervisorContextStrategy contextStrategy,
            AgentListener... listeners) {
        var builder = AgenticServices.supervisorBuilder()
                .chatModel(model)
                .responseStrategy(responseStrategy)
                .contextGenerationStrategy(contextStrategy)
                .name("supervisor")
                .subAgents(assistant());
        for (AgentListener listener : listeners) {
            builder.listener(listener);
        }
        return builder.build();
    }

    @Test
    void supervisor_reports_each_planning_call_to_its_listeners() {
        List<PlannerModelResponse> responses = new CopyOnWriteArrayList<>();
        ChatModelMock model = ChatModelMock.thatAlwaysResponds(INVOKE_ASSISTANT, DONE)
                .withModelNames("planning-model", "planning-model")
                .withTokenUsages(new TokenUsage(100, 20), new TokenUsage(30, 5));

        supervisor(model, SupervisorResponseStrategy.SUMMARY, SupervisorContextStrategy.CHAT_MEMORY, recording(responses))
                .invoke("What time is it?");

        // the call choosing the assistant, then the one ending the invocation
        assertThat(responses).extracting(PlannerModelResponse::agentName).containsExactly("supervisor", "supervisor");
        assertThat(responses)
                .extracting(PlannerModelResponse::tokenUsage)
                .containsExactly(new TokenUsage(100, 20), new TokenUsage(30, 5));
        assertThat(responses).extracting(PlannerModelResponse::modelName).containsOnly("planning-model");
    }

    @Test
    void monitor_counts_the_planning_calls_of_a_supervisor_and_not_the_calls_of_its_subagents() {
        AgentMonitor monitor = new AgentMonitor();
        ChatModelMock model = ChatModelMock.thatAlwaysResponds(INVOKE_ASSISTANT, DONE)
                .withModelNames("planning-model", "planning-model")
                .withTokenUsages(new TokenUsage(100, 20), new TokenUsage(30, 5));

        String result = supervisor(
                        model, SupervisorResponseStrategy.SUMMARY, SupervisorContextStrategy.CHAT_MEMORY, monitor)
                .invoke("What time is it?");

        assertThat(result).isEqualTo("The assistant said it is noon");
        AgentInvocation invocation = topLevelInvocation(monitor);
        assertThat(invocation.tokenUsage()).contains(new TokenUsage(130, 25));
        assertThat(invocation.modelName()).contains("planning-model");
        assertThat(invocation.nestedInvocations())
                .extracting(AgentInvocation::totalTokenCount)
                .containsExactly(8);
    }

    @Test
    void supervisor_scoring_its_responses_counts_the_scoring_call() {
        AgentMonitor monitor = new AgentMonitor();
        ChatModelMock model = ChatModelMock.thatAlwaysResponds(INVOKE_ASSISTANT, DONE, AiMessage.from("""
                        {"score1": 0.2, "score2": 0.9}
                        """))
                .withTokenUsages(new TokenUsage(100, 20), new TokenUsage(30, 5), new TokenUsage(10, 2));

        String result = supervisor(
                        model, SupervisorResponseStrategy.SCORED, SupervisorContextStrategy.CHAT_MEMORY, monitor)
                .invoke("What time is it?");

        // the recap of the supervisor got the highest score
        assertThat(result).isEqualTo("The assistant said it is noon");
        assertThat(model.requests()).hasSize(3);
        assertThat(topLevelInvocation(monitor).tokenUsage()).contains(new TokenUsage(140, 27));
    }

    @Test
    void supervisor_whose_planning_reply_cannot_be_parsed_still_reports_that_call() {
        List<PlannerModelResponse> responses = new CopyOnWriteArrayList<>();
        AgentMonitor monitor = new AgentMonitor();
        ChatModelMock model = ChatModelMock.thatAlwaysResponds("this is not an agent invocation")
                .withModelNames("planning-model")
                .withTokenUsage(new TokenUsage(100, 20));
        SupervisorAgent supervisor = supervisor(
                model,
                SupervisorResponseStrategy.SUMMARY,
                SupervisorContextStrategy.CHAT_MEMORY,
                recording(responses),
                monitor);

        assertThatThrownBy(() -> supervisor.invoke("What time is it?")).isInstanceOf(OutputParsingException.class);

        // the model answered and was billed, even if the supervisor could not read its reply
        assertThat(responses).extracting(PlannerModelResponse::tokenUsage).containsExactly(new TokenUsage(100, 20));
        AgentInvocation failed = monitor.failedExecutions().get(0).topLevelInvocations();
        assertThat(failed.tokenUsage()).contains(new TokenUsage(100, 20));
        assertThat(failed.modelName()).contains("planning-model");
    }

    @Test
    void supervisor_whose_scoring_reply_cannot_be_parsed_still_reports_that_call() {
        AgentMonitor monitor = new AgentMonitor();
        ChatModelMock model = ChatModelMock.thatAlwaysResponds(
                        INVOKE_ASSISTANT, DONE, AiMessage.from("these are not scores"))
                .withTokenUsages(new TokenUsage(100, 20), new TokenUsage(30, 5), new TokenUsage(10, 2));
        SupervisorAgent supervisor =
                supervisor(model, SupervisorResponseStrategy.SCORED, SupervisorContextStrategy.CHAT_MEMORY, monitor);

        assertThatThrownBy(() -> supervisor.invoke("What time is it?")).isInstanceOf(OutputParsingException.class);

        // the two planning calls and the scoring call
        assertThat(model.requests()).hasSize(3);
        assertThat(monitor.failedExecutions().get(0).topLevelInvocations().tokenUsage())
                .contains(new TokenUsage(140, 27));
    }

    @Test
    void supervisor_summarizing_its_context_counts_the_summarization_call() {
        List<PlannerModelResponse> responses = new CopyOnWriteArrayList<>();
        AgentMonitor monitor = new AgentMonitor();
        AiMessage summary = AiMessage.from("The assistant told the time");
        // the context is empty at the first step, so it is summarized only before the second planning call
        ChatModelMock model = ChatModelMock.thatAlwaysResponds(INVOKE_ASSISTANT, summary, DONE)
                .withTokenUsages(new TokenUsage(100, 20), new TokenUsage(10, 2), new TokenUsage(30, 5));

        supervisor(
                        model,
                        SupervisorResponseStrategy.SUMMARY,
                        SupervisorContextStrategy.SUMMARIZATION,
                        recording(responses),
                        monitor)
                .invoke("What time is it?");

        // the first planning call, the summarization call and the second planning call, in this order
        assertThat(responses)
                .extracting(PlannerModelResponse::tokenUsage)
                .containsExactly(new TokenUsage(100, 20), new TokenUsage(10, 2), new TokenUsage(30, 5));
        assertThat(responses).extracting(PlannerModelResponse::agentName).containsOnly("supervisor");
        assertThat(topLevelInvocation(monitor).tokenUsage()).contains(new TokenUsage(140, 27));
    }

    // ── Out of scope: the context summarized for an AI agent ──

    public interface Recapper {

        @UserMessage("Recap what was said")
        @Agent(outputKey = "recap")
        String recap();
    }

    @Test
    void summarization_call_of_an_ai_agent_is_not_a_planner_model_response_and_is_not_counted() {
        List<PlannerModelResponse> responses = new CopyOnWriteArrayList<>();
        AgentMonitor monitor = new AgentMonitor();
        // the first call of this model summarizes the context, the second one is the call of the agent itself
        ChatModelMock recapperModel = ChatModelMock.thatAlwaysResponds("recap")
                .withTokenUsages(new TokenUsage(10, 2), new TokenUsage(30, 5));
        Recapper recapper = AgenticServices.agentBuilder(Recapper.class)
                .chatModel(recapperModel)
                .summarizedContext("answer")
                .build();

        AgenticServices.sequenceBuilder(Workflow.class)
                .subAgents(assistant(), recapper)
                .outputKey("recap")
                .listener(recording(responses))
                .listener(monitor)
                .build()
                .process("what time is it?");

        assertThat(recapperModel.requests()).hasSize(2);
        assertThat(responses).isEmpty();
        assertThat(topLevelInvocation(monitor).nestedInvocations())
                .extracting(AgentInvocation::tokenUsage)
                .containsExactly(Optional.of(new TokenUsage(7, 1)), Optional.of(new TokenUsage(30, 5)));
    }
}
