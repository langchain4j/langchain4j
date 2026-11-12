package dev.langchain4j.agentic.decision;

import static dev.langchain4j.agentic.decision.DecisionMocks.answeringYes;
import static dev.langchain4j.agentic.decision.DecisionMocks.choosing;
import static dev.langchain4j.agentic.decision.DecisionMocks.triaging;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.decision.DecisionAgents.BannerCheck;
import dev.langchain4j.agentic.decision.DecisionAgents.CategoryRouter;
import dev.langchain4j.agentic.decision.DecisionAgents.ConfidentCategoryRouter;
import dev.langchain4j.agentic.decision.DecisionAgents.CreditEligibility;
import dev.langchain4j.agentic.decision.DecisionAgents.Quality;
import dev.langchain4j.agentic.decision.DecisionAgents.RequestCategory;
import dev.langchain4j.agentic.decision.DecisionAgents.StyleScorer;
import dev.langchain4j.agentic.decision.DecisionAgents.TicketTriage;
import dev.langchain4j.agentic.decision.DecisionAgents.TransactionGate;
import dev.langchain4j.agentic.decision.DecisionAgents.Triage;
import dev.langchain4j.agentic.observability.AgentInvocation;
import dev.langchain4j.agentic.observability.AgentMonitor;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.agentic.supervisor.SupervisorAgent;
import dev.langchain4j.agentic.supervisor.SupervisorResponseStrategy;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.decision.mock.DecisionModelMock;
import dev.langchain4j.model.decision.request.FieldsInput;
import dev.langchain4j.model.decision.request.ScaleQuestion;
import dev.langchain4j.model.decision.response.YesNoAnswer;
import dev.langchain4j.service.decision.Choice;
import dev.langchain4j.service.decision.Scale;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * The scenarios of the design record of decision agents, run with mocked models: one test per scenario, asserting
 * on the scope and on the monitor.
 */
class DecisionAgentScenariosTest {

    private static Object expert(String name, String answer) {
        return AgenticServices.nonAiAgentBuilder(scope -> answer + " " + scope.readState("request", ""))
                .name(name)
                .description("A " + name + " expert")
                .inputKey(String.class, "request")
                .outputKey("response")
                .build();
    }

    private static List<AgentInvocation> invocations(AgentMonitor monitor) {
        return monitor.successfulExecutions().get(0).topLevelInvocations().nestedInvocations();
    }

    // Scenario 1: classifier for a conditional workflow

    @Test
    void classifier_routes_the_request_to_one_expert() {
        CategoryRouter router = AgenticServices.decisionAgentBuilder(CategoryRouter.class)
                .decisionModel(choosing("LEGAL", 0.9))
                .build();
        UntypedAgent experts = AgenticServices.conditionalBuilder()
                .subAgents(
                        scope -> scope.readState("category") == RequestCategory.MEDICAL,
                        expert("medical", "Medically:"))
                .subAgents(scope -> scope.readState("category") == RequestCategory.LEGAL, expert("legal", "Legally:"))
                .subAgents(
                        scope -> scope.readState("category") == RequestCategory.UNKNOWN,
                        expert("human", "A human will answer:"))
                .build();
        AgentMonitor monitor = new AgentMonitor();
        UntypedAgent system = AgenticServices.sequenceBuilder()
                .subAgents(router, experts)
                .outputKey("response")
                .listener(monitor)
                .build();

        Object response = system.invoke(Map.of("request", "Can my landlord keep my deposit?"));

        assertThat(response).isEqualTo("Legally: Can my landlord keep my deposit?");
        AgentInvocation conditional = invocations(monitor).get(1);
        assertThat(conditional.nestedInvocations())
                .filteredOn(invocation -> !invocation.skipped())
                .extracting(invocation -> invocation.agent().name())
                .containsExactly("legal");
    }

    @Test
    void classifier_with_probabilities_sends_uncertain_requests_to_a_human() {
        ConfidentCategoryRouter router = AgenticServices.decisionAgentBuilder(ConfidentCategoryRouter.class)
                .decisionModel(choosing("LEGAL", 0.4))
                .build();
        UntypedAgent experts = AgenticServices.conditionalBuilder()
                .subAgents(scope -> confident(scope, RequestCategory.LEGAL), expert("legal", "Legally:"))
                .subAgents(scope -> !confident(scope, RequestCategory.LEGAL), expert("human", "A human will answer:"))
                .build();
        UntypedAgent system = AgenticServices.sequenceBuilder()
                .subAgents(router, experts)
                .outputKey("response")
                .build();

        Object response = system.invoke(Map.of("request", "Can my landlord keep my deposit?"));

        assertThat(response).isEqualTo("A human will answer: Can my landlord keep my deposit?");
    }

    private static boolean confident(AgenticScope scope, RequestCategory category) {
        Choice<RequestCategory> choice = (Choice<RequestCategory>) scope.readState("category");
        return choice.value() == category && choice.probabilityOf(category) >= 0.6;
    }

    // Scenario 2: scorer inside a loop

    @Test
    void scorer_stops_the_loop_when_the_story_is_good_enough() {
        Iterator<String> levels = List.of("POOR", "FAIR", "EXCELLENT").iterator();
        DecisionModelMock scoringModel = DecisionModelMock.thatAnswersQuestions(
                question -> DecisionMocks.scaleAnswer((ScaleQuestion) question, levels.next(), 1.0));
        StyleScorer scorer = AgenticServices.decisionAgentBuilder(StyleScorer.class)
                .decisionModel(scoringModel)
                .build();
        Object editor = AgenticServices.nonAiAgentBuilder(scope -> scope.readState("story", "") + "!")
                .name("editor")
                .inputKey(String.class, "story")
                .outputKey("story")
                .build();
        UntypedAgent loop = AgenticServices.loopBuilder()
                .subAgents(scorer, editor)
                .maxIterations(5)
                .exitCondition(scope -> ((Scale<Quality>) scope.readState("styleScore")).mean() >= 2)
                .outputKey("story")
                .build();

        Object story = loop.invoke(Map.of("story", "Once upon a time", "style", "fairy tale"));

        assertThat(story).isEqualTo("Once upon a time!!");
        assertThat(scoringModel.requests()).hasSize(3);
    }

    // Scenario 3: gate before a side effect

    @Test
    void gate_sends_suspicious_transfers_to_a_human_and_moves_the_others() {
        AtomicInteger moved = new AtomicInteger();
        double[] threshold = {0.5};
        TransactionGate gate = AgenticServices.decisionAgentBuilder(TransactionGate.class)
                .decisionModel(answeringYes(0.7))
                .thresholdProvider(context -> threshold[0])
                .build();
        Object mover = AgenticServices.nonAiAgentBuilder(scope -> "moved " + moved.incrementAndGet())
                .name("mover")
                .outputKey("receipt")
                .build();
        Object approver = AgenticServices.nonAiAgentBuilder(scope -> "waiting for approval")
                .name("approver")
                .outputKey("receipt")
                .build();
        UntypedAgent system = AgenticServices.sequenceBuilder()
                .subAgents(
                        gate,
                        AgenticServices.conditionalBuilder()
                                .subAgents(scope -> scope.readState("suspicious", false), approver)
                                .subAgents(scope -> !scope.readState("suspicious", false), mover)
                                .build())
                .outputKey("receipt")
                .build();

        assertThat(system.invoke(Map.of("transfer", "50000 USD to an unknown account")))
                .isEqualTo("waiting for approval");
        assertThat(moved).hasValue(0);

        // raising the threshold from configuration lets the same transfer through
        threshold[0] = 0.9;
        assertThat(system.invoke(Map.of("transfer", "50000 USD to an unknown account")))
                .isEqualTo("moved 1");
    }

    // Scenario 4: oracle subagent of a supervisor

    @Test
    void supervisor_consults_the_oracle_before_crediting() {
        DecisionModelMock eligibilityModel = answeringYes(0.95);
        CreditEligibility eligibility = AgenticServices.decisionAgentBuilder(CreditEligibility.class)
                .decisionModel(eligibilityModel)
                .build();
        AtomicInteger credited = new AtomicInteger();
        Object credit = AgenticServices.nonAiAgentBuilder(scope -> {
                    credited.incrementAndGet();
                    return "credited " + scope.readState("amount", 0.0) + " USD";
                })
                .name("credit")
                .description("Credits the given amount of USD to the customer")
                .inputKey(Double.class, "amount")
                .outputKey("receipt")
                .build();
        ChatModelMock plannerModel = ChatModelMock.thatAlwaysResponds(AiMessage.from("""
                {"agentName": "isEligible", "arguments": {"customerProfile": "Mario, income 3000 USD, no debts", "amount": 1000}}
                """), AiMessage.from("""
                {"agentName": "credit", "arguments": {"amount": 1000}}
                """), AiMessage.from("""
                {"agentName": "done", "arguments": {"response": "Mario has been credited 1000 USD"}}
                """));
        SupervisorAgent supervisor = AgenticServices.supervisorBuilder()
                .chatModel(plannerModel)
                .responseStrategy(SupervisorResponseStrategy.LAST)
                .subAgents(eligibility, credit)
                .build();

        ResultWithAgenticScope<String> result =
                supervisor.invokeWithAgenticScope("Give Mario a credit of 1000 USD if he is eligible");

        assertThat(result.agenticScope().readState("eligible")).isEqualTo(true);
        assertThat(result.agenticScope().readState("receipt")).isEqualTo("credited 1000.0 USD");
        // the supervisor generated the amount as an integer, converted to the declared Double before the decision
        assertThat(((FieldsInput) eligibilityModel.request().input()).fields()).containsEntry("amount", 1000.0);
        assertThat(credited).hasValue(1);
    }

    // Scenario 5: multi-label triage feeding several agents

    @Test
    void triage_answers_several_questions_at_once_and_feeds_several_agents() {
        TicketTriage triage = AgenticServices.decisionAgentBuilder(TicketTriage.class)
                .decisionModel(triaging("BILLING", 0.8, "ANGRY"))
                .build();
        Object replier = AgenticServices.nonAiAgentBuilder(scope ->
                        "Reply from " + scope.readState("team") + " to a " + scope.readState("sentiment") + " customer")
                .name("replier")
                .inputKeys(String.class, "team", String.class, "sentiment")
                .outputKey("reply")
                .build();
        Object escalator = AgenticServices.nonAiAgentBuilder(scope -> "escalated")
                .name("escalator")
                .outputKey("escalation")
                .build();
        UntypedAgent handlers = AgenticServices.conditionalBuilder()
                .beforeCall(scope -> {
                    Triage result = (Triage) scope.readState("triage");
                    scope.writeState("team", result.team());
                    scope.writeState("urgent", result.urgent());
                    scope.writeState("sentiment", result.sentiment());
                })
                .subAgents(scope -> true, replier)
                .subAgents(scope -> scope.readState("urgent", false), escalator)
                .build();
        UntypedAgent system = AgenticServices.sequenceBuilder()
                .subAgents(triage, handlers)
                .outputKey("reply")
                .build();

        ResultWithAgenticScope<String> result = system.invokeWithAgenticScope(Map.of("ticket", "I was charged twice!"));

        assertThat(result.result()).isEqualTo("Reply from BILLING to a ANGRY customer");
        assertThat(result.agenticScope().readState("escalation")).isEqualTo("escalated");
    }

    // Scenario 6: sensing agent in a state-driven flow (the blackboard version lives in the patterns module)

    @Test
    void sensing_agent_fires_when_its_inputs_appear_and_gates_the_specialist() {
        DecisionModelMock reviewModel = answeringYes(0.9);
        Object specialistReview = AgenticServices.decisionAgentBuilder(DecisionAgents.SpamCheck.class)
                .decisionModel(reviewModel)
                .name("needsReview")
                .outputKey("needsReview")
                .optional(true)
                .build();
        Object labs = AgenticServices.nonAiAgentBuilder(scope -> "elevated creatinine")
                .name("labs")
                .outputKey("message")
                .build();
        Object specialist = AgenticServices.nonAiAgentBuilder(scope -> "nephrologist consulted")
                .name("specialist")
                .outputKey("consultation")
                .activationCondition(scope ->
                        scope.readState("needsReview", YesNoAnswer.from(0)).probability() >= 0.5)
                .build();
        // the sensing agent runs before its input exists (skipped) and again after the labs wrote it
        UntypedAgent system = AgenticServices.sequenceBuilder()
                .subAgents(specialistReview, labs, specialistReview, specialist)
                .outputKey("consultation")
                .build();

        Object consultation = system.invoke(Map.of());

        assertThat(consultation).isEqualTo("nephrologist consulted");
        assertThat(reviewModel.requests()).hasSize(1);
    }

    // Scenario 7: a decision on an image

    @Test
    void image_check_sends_the_image_as_a_content() {
        DecisionModelMock imageModel = answeringYes(0.9);
        BannerCheck check = AgenticServices.decisionAgentBuilder(BannerCheck.class)
                .decisionModel(imageModel)
                .build();
        ImageContent banner = ImageContent.from("https://example.com/banner.png");

        Object containsText = AgenticServices.sequenceBuilder()
                .subAgents(check)
                .outputKey("containsText")
                .build()
                .invoke(Map.of("banner", banner));

        assertThat(containsText).isEqualTo(true);
        assertThat(((FieldsInput) imageModel.request().input()).fields()).containsEntry("banner", banner);
    }
}
