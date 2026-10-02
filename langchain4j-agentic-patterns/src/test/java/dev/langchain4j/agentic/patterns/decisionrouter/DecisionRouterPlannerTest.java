package dev.langchain4j.agentic.patterns.decisionrouter;

import static dev.langchain4j.agentic.patterns.decisionrouter.DecisionRouterPlanner.QUESTION_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.observability.HtmlReportGenerator;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.model.decision.mock.DecisionModelMock;
import dev.langchain4j.model.decision.request.ChoiceQuestion;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.decision.response.ChoiceAnswer;
import dev.langchain4j.model.decision.response.YesNoAnswer;
import dev.langchain4j.service.V;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DecisionRouterPlannerTest {

    private static final String REQUEST = "I broke my leg, what should I do?";

    // ── Non-AI expert agents counting their invocations ──

    public static class MedicalExpert {
        final AtomicInteger calls = new AtomicInteger();

        @Agent(description = "Answers medical questions", outputKey = "medicalResponse")
        public String medical(@V("request") String request) {
            calls.incrementAndGet();
            return "medical answer";
        }
    }

    public static class LegalExpert {
        final AtomicInteger calls = new AtomicInteger();

        @Agent(description = "Answers legal questions", outputKey = "legalResponse")
        public String legal(@V("request") String request) {
            calls.incrementAndGet();
            return "legal answer";
        }
    }

    public static class TechnicalExpert {
        final AtomicInteger calls = new AtomicInteger();

        @Agent(outputKey = "technicalResponse")
        public String technical(@V("request") String request) {
            calls.incrementAndGet();
            return "technical answer";
        }
    }

    // ── Routers ──

    public interface ExpertRouter {

        @Agent
        String ask(@V("request") String request);
    }

    public interface ExpertRouterWithScope {

        @Agent
        ResultWithAgenticScope<String> ask(@V("request") String request);
    }

    public interface MultiExpertRouter {

        @Agent
        Map<String, Object> ask(@V("request") String request);
    }

    public interface MultiExpertRouterWithScope {

        @Agent
        ResultWithAgenticScope<Map<String, Object>> ask(@V("request") String request);
    }

    // ── Agents and workflows composing the router into a sequence ──

    public static class Summarizer {

        @Agent(outputKey = "summary")
        public String summarize(@V("response") String response) {
            return "summary of " + response;
        }
    }

    public static class Synthesizer {

        @Agent(outputKey = "summary")
        public String synthesize(@V("responses") Map<String, Object> responses) {
            return "synthesis of " + responses.keySet();
        }
    }

    public interface ExpertPipeline {

        @Agent
        String process(@V("request") String request);
    }

    private final MedicalExpert medicalExpert = new MedicalExpert();
    private final LegalExpert legalExpert = new LegalExpert();
    private final TechnicalExpert technicalExpert = new TechnicalExpert();

    private static ChoiceAnswer choice(String value, Map<String, Double> probabilities) {
        return ChoiceAnswer.builder().value(value).probabilities(probabilities).build();
    }

    private static DecisionModelMock modelAnswering(ChoiceAnswer answer) {
        return DecisionModelMock.thatAlwaysAnswers(Map.of(QUESTION_NAME, answer));
    }

    private static DecisionModelMock modelAnsweringRelevance(Map<String, Double> probabilities) {
        Map<String, YesNoAnswer> answers = new LinkedHashMap<>();
        probabilities.forEach((agentName, probability) -> answers.put(agentName, YesNoAnswer.of(probability)));
        return DecisionModelMock.thatAlwaysAnswers(answers);
    }

    private <T> T router(Class<T> routerType, DecisionRouterPlanner planner) {
        return AgenticServices.plannerBuilder(routerType)
                .subAgents(medicalExpert, legalExpert, technicalExpert)
                .outputKey("response")
                .planner(() -> planner)
                .build();
    }

    private ExpertRouter router(DecisionModelMock model) {
        return router(ExpertRouter.class, new DecisionRouterPlanner(model));
    }

    private MultiExpertRouter multiRouter(DecisionModelMock model, double activationThreshold) {
        return router(MultiExpertRouter.class, new DecisionRouterPlanner(model, activationThreshold));
    }

    // ── Without activation threshold ──

    @Test
    void shouldInvokeOnlyTheChosenAgent() {
        DecisionModelMock model = modelAnswering(choice("legal", Map.of("legal", 0.8, "medical", 0.2)));

        String response = router(model).ask(REQUEST);

        assertThat(response).isEqualTo("legal answer");
        assertThat(legalExpert.calls).hasValue(1);
        assertThat(medicalExpert.calls).hasValue(0);
        assertThat(technicalExpert.calls).hasValue(0);
    }

    @Test
    void shouldAskOneChoiceQuestionWithTheSubagentsAsOptions() {
        DecisionModelMock model = modelAnswering(choice("medical", Map.of()));

        router(model).ask(REQUEST);

        DecisionRequest request = model.request();
        assertThat(request.input()).isEqualTo(Map.of("request", REQUEST));
        assertThat(request.questions()).containsOnlyKeys(QUESTION_NAME);
        ChoiceQuestion question = (ChoiceQuestion) request.questions().get(QUESTION_NAME);
        assertThat(question.text()).isEqualTo("Which agent is best suited to handle this request?");
        // an option added without a description is described by its name
        assertThat(question.options())
                .containsExactly(
                        entry("medical", "Answers medical questions"),
                        entry("legal", "Answers legal questions"),
                        entry("technical", "technical"));
    }

    @Test
    void shouldWriteTheResultUnderTheRouterOutputKey() {
        DecisionModelMock model = modelAnswering(choice("medical", Map.of()));

        ResultWithAgenticScope<String> result = router(ExpertRouterWithScope.class, new DecisionRouterPlanner(model))
                .ask(REQUEST);

        assertThat(result.result()).isEqualTo("medical answer");
        assertThat(result.agenticScope().readState("response", "")).isEqualTo("medical answer");
    }

    @Test
    void shouldRouteWhenTheModelReportsNoProbabilities() {
        DecisionModelMock model = modelAnswering(choice("technical", Map.of()));

        assertThat(router(model).ask(REQUEST)).isEqualTo("technical answer");
    }

    // ── With activation threshold ──

    @Test
    void shouldInvokeInParallelAllAgentsReachingTheThreshold() {
        CyclicBarrier barrier = new CyclicBarrier(2);
        var medical = new Object() {
            @Agent(description = "Answers medical questions", outputKey = "medicalResponse")
            public String medical(@V("request") String request) throws Exception {
                barrier.await(10, TimeUnit.SECONDS);
                return "medical answer";
            }
        };
        var legal = new Object() {
            @Agent(description = "Answers legal questions", outputKey = "legalResponse")
            public String legal(@V("request") String request) throws Exception {
                barrier.await(10, TimeUnit.SECONDS);
                return "legal answer";
            }
        };
        // each agent has its own probability, so both can be above 0.5, which a choice question could not express
        DecisionModelMock model = modelAnsweringRelevance(Map.of("medical", 0.9, "legal", 0.85, "technical", 0.05));

        MultiExpertRouter router = AgenticServices.plannerBuilder(MultiExpertRouter.class)
                .subAgents(medical, legal, technicalExpert)
                .outputKey("responses")
                .planner(() -> new DecisionRouterPlanner(model, 0.5))
                .build();

        // both agents must be waiting on the barrier at the same time, otherwise it times out
        assertThat(router.ask(REQUEST))
                .containsOnly(entry("medical", "medical answer"), entry("legal", "legal answer"));
        assertThat(technicalExpert.calls).hasValue(0);
    }

    @Test
    void shouldActivateAnAgentWhoseProbabilityEqualsTheThreshold() {
        DecisionModelMock model = modelAnsweringRelevance(Map.of("medical", 0.5, "legal", 0.3, "technical", 0.2));

        assertThat(multiRouter(model, 0.5).ask(REQUEST)).containsOnly(entry("medical", "medical answer"));
    }

    @Test
    void shouldReturnASingleOutputWhenOnlyOneAgentReachesTheThreshold() {
        DecisionModelMock model = modelAnsweringRelevance(Map.of("medical", 0.1, "legal", 0.85, "technical", 0.05));

        assertThat(multiRouter(model, 0.5).ask(REQUEST)).containsOnly(entry("legal", "legal answer"));
        assertThat(medicalExpert.calls).hasValue(0);
        assertThat(technicalExpert.calls).hasValue(0);
    }

    @Test
    void shouldInvokeNoAgentWhenNoneReachesTheThreshold() {
        DecisionModelMock model = modelAnsweringRelevance(Map.of("medical", 0.4, "legal", 0.35, "technical", 0.25));

        assertThat(multiRouter(model, 0.5).ask(REQUEST)).isEmpty();
        assertThat(medicalExpert.calls).hasValue(0);
        assertThat(legalExpert.calls).hasValue(0);
        assertThat(technicalExpert.calls).hasValue(0);
    }

    @Test
    void shouldAskOneYesNoQuestionPerSubagentWithAThreshold() {
        DecisionModelMock model = modelAnsweringRelevance(Map.of("medical", 0.9, "legal", 0.1, "technical", 0.1));

        multiRouter(model, 0.5).ask(REQUEST);

        DecisionRequest request = model.request();
        assertThat(request.input()).isEqualTo(Map.of("request", REQUEST));
        assertThat(request.questions()).containsOnlyKeys("medical", "legal", "technical");
        assertThat(request.questions().values())
                .allSatisfy(question -> assertThat(question)
                        .isInstanceOfSatisfying(
                                YesNoQuestion.class,
                                yesNo -> assertThat(yesNo.yesWhen()).isNull()));
        assertThat(request.questions().get("medical").text())
                .isEqualTo("Should the agent 'medical' (Answers medical questions) handle this request?");
        // an agent without a description is asked about by its name only
        assertThat(request.questions().get("technical").text())
                .isEqualTo("Should the agent 'technical' handle this request?");
    }

    @Test
    void shouldGateASingleSubagentWithAThreshold() {
        MultiExpertRouter irrelevant = AgenticServices.plannerBuilder(MultiExpertRouter.class)
                .subAgents(medicalExpert)
                .planner(() -> new DecisionRouterPlanner(modelAnsweringRelevance(Map.of("medical", 0.2)), 0.5))
                .build();
        MultiExpertRouter relevant = AgenticServices.plannerBuilder(MultiExpertRouter.class)
                .subAgents(medicalExpert)
                .planner(() -> new DecisionRouterPlanner(modelAnsweringRelevance(Map.of("medical", 0.8)), 0.5))
                .build();

        assertThat(irrelevant.ask(REQUEST)).isEmpty();
        assertThat(medicalExpert.calls).hasValue(0);
        assertThat(relevant.ask(REQUEST)).containsOnly(entry("medical", "medical answer"));
        assertThat(medicalExpert.calls).hasValue(1);
    }

    @Test
    void shouldRejectAThresholdOutsideZeroToOne() {
        DecisionModelMock model = modelAnswering(choice("medical", Map.of()));

        assertThatThrownBy(() -> new DecisionRouterPlanner(model, 1.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Activation threshold");
        assertThatThrownBy(() -> new DecisionRouterPlanner(model, -0.1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Activation threshold");
        assertThatThrownBy(() -> new DecisionRouterPlanner(model, 0.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Activation threshold");
    }

    // ── Both modes ──

    @Test
    void shouldRejectSubagentsWithTheSameName() {
        var anotherMedicalExpert = new Object() {
            @Agent(outputKey = "otherMedicalResponse")
            public String medical(@V("request") String request) {
                return "another medical answer";
            }
        };
        DecisionModelMock model = modelAnswering(choice("medical", Map.of()));

        ExpertRouter router = AgenticServices.plannerBuilder(ExpertRouter.class)
                .subAgents(medicalExpert, anotherMedicalExpert)
                .planner(() -> new DecisionRouterPlanner(model))
                .build();

        assertThatThrownBy(() -> router.ask(REQUEST))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("distinct names")
                .hasMessageContaining("'medical'");
    }

    @Test
    void shouldRejectARouterNotReturningAMapWithAThreshold() {
        DecisionModelMock model = modelAnsweringRelevance(Map.of("medical", 0.9, "legal", 0.8, "technical", 0.1));

        assertThatThrownBy(() -> router(ExpertRouter.class, new DecisionRouterPlanner(model, 0.3))
                        .ask(REQUEST))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("activationThreshold=0.3")
                .hasMessageContaining("returns java.lang.String")
                .hasMessageContaining("declare a Map return type, or remove the activation threshold");
        assertThat(model.requests()).isEmpty();
        assertThat(medicalExpert.calls).hasValue(0);
    }

    @Test
    void shouldAcceptAResultWithAgenticScopeOfAMapWithAThreshold() {
        DecisionModelMock model = modelAnsweringRelevance(Map.of("medical", 0.9, "legal", 0.8, "technical", 0.1));

        ResultWithAgenticScope<Map<String, Object>> result = router(
                        MultiExpertRouterWithScope.class, new DecisionRouterPlanner(model, 0.5))
                .ask(REQUEST);

        assertThat(result.result()).containsOnly(entry("medical", "medical answer"), entry("legal", "legal answer"));
    }

    @Test
    void shouldRejectAnUntypedRouter() {
        DecisionModelMock model = modelAnswering(choice("medical", Map.of()));

        UntypedAgent router = AgenticServices.plannerBuilder()
                .subAgents(medicalExpert, legalExpert)
                .planner(() -> new DecisionRouterPlanner(model))
                .build();

        assertThatThrownBy(() -> router.invoke(Map.of("request", REQUEST)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("typed agent interface");
    }

    @Test
    void shouldPropagateADecisionModelFailure() {
        DecisionModelMock model = DecisionModelMock.thatAlwaysThrowsExceptionWithMessage("decision failed");

        assertThatThrownBy(() -> router(model).ask(REQUEST)).hasMessageContaining("decision failed");
        assertThat(medicalExpert.calls).hasValue(0);
        assertThat(legalExpert.calls).hasValue(0);
        assertThat(technicalExpert.calls).hasValue(0);
    }

    @Test
    void shouldBeAStepOfASequenceWithoutThreshold() {
        DecisionModelMock model = modelAnswering(choice("legal", Map.of()));
        ExpertRouter router = router(model);

        ExpertPipeline pipeline = AgenticServices.sequenceBuilder(ExpertPipeline.class)
                .subAgents(router, new Summarizer())
                .outputKey("summary")
                .build();

        assertThat(pipeline.process(REQUEST)).isEqualTo("summary of legal answer");
        assertThat(model.request().input()).isEqualTo(Map.of("request", REQUEST));
    }

    @Test
    void shouldBeAStepOfASequenceWithThreshold() {
        DecisionModelMock model = modelAnsweringRelevance(Map.of("medical", 0.9, "legal", 0.7, "technical", 0.05));
        MultiExpertRouter router = AgenticServices.plannerBuilder(MultiExpertRouter.class)
                .subAgents(medicalExpert, legalExpert, technicalExpert)
                .outputKey("responses")
                .planner(() -> new DecisionRouterPlanner(model, 0.5))
                .build();

        ExpertPipeline pipeline = AgenticServices.sequenceBuilder(ExpertPipeline.class)
                .subAgents(router, new Synthesizer())
                .outputKey("summary")
                .build();

        String summary = pipeline.process(REQUEST);

        assertThat(summary).startsWith("synthesis of [").contains("medical").contains("legal");
        assertThat(technicalExpert.calls).hasValue(0);
    }

    @Test
    void shouldBeReportedAsARouter() {
        DecisionModelMock model = modelAnswering(choice("medical", Map.of()));

        assertThat(new DecisionRouterPlanner(model).topology()).isEqualTo(AgenticSystemTopology.ROUTER);
        assertThat(HtmlReportGenerator.generateTopology(router(model)))
                .contains("<span class=\"topology-badge rtr\">Router</span>")
                .contains("<span class=\"agent-name\">medical</span>")
                .contains("<span class=\"agent-name\">legal</span>")
                .contains("<span class=\"agent-name\">technical</span>");
    }
}
