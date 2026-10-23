package dev.langchain4j.agentic.patterns.cascade;

import static dev.langchain4j.agentic.patterns.cascade.ConfidenceCascadePlanner.ANSWER_KEY;
import static dev.langchain4j.agentic.patterns.cascade.ConfidenceCascadePlanner.QUESTION_NAME;
import static dev.langchain4j.agentic.patterns.cascade.ConfidenceCascadePlanner.QUESTION_TEXT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.AgenticServices.AgenticScopeFunction;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.observability.HtmlReportGenerator;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.exception.ContentFilteredException;
import dev.langchain4j.model.decision.mock.DecisionModelMock;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.FieldsInput;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.decision.response.RefusalAnswer;
import dev.langchain4j.model.decision.response.YesNoAnswer;
import dev.langchain4j.service.V;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class ConfidenceCascadePlannerTest {

    private static final String REQUEST = "How do I descale my coffee machine?";

    // ── Non-AI tiers counting their invocations ──

    public static class FaqAgent {
        final AtomicInteger calls = new AtomicInteger();

        @Agent(description = "Answers frequently asked questions", outputKey = "faqAnswer")
        public String faq(@V("request") String request) {
            calls.incrementAndGet();
            return "faq answer";
        }
    }

    public static class ExpertAgent {
        final AtomicInteger calls = new AtomicInteger();

        @Agent(description = "Answers any question", outputKey = "expertAnswer")
        public String expert(@V("request") String request) {
            calls.incrementAndGet();
            return "expert answer";
        }
    }

    public static class SeniorExpertAgent {
        final AtomicInteger calls = new AtomicInteger();

        @Agent(outputKey = "seniorAnswer")
        public String senior(@V("request") String request) {
            calls.incrementAndGet();
            return "senior answer";
        }
    }

    public record Ticket(String subject, int priority) {}

    public record StructuredAnswer(String text, List<String> steps) {}

    public static class StructuredFaqAgent {

        @Agent(outputKey = "faqAnswer")
        public StructuredAnswer faq(@V("ticket") Object ticket) {
            return new StructuredAnswer("faq answer", List.of("step 1", "step 2"));
        }
    }

    public static class ImageFaqAgent {
        private final ImageContent image;

        ImageFaqAgent(ImageContent image) {
            this.image = image;
        }

        @Agent(outputKey = "faqAnswer")
        public ImageContent faq(@V("request") String request) {
            return image;
        }
    }

    // ── Cascades ──

    public interface SupportCascade {

        @Agent
        String ask(@V("request") String request);
    }

    public interface SupportCascadeWithScope {

        @Agent
        ResultWithAgenticScope<String> ask(@V("request") String request);
    }

    public interface TicketCascade {

        @Agent
        StructuredAnswer ask(@V("ticket") Ticket ticket);
    }

    public interface TicketMapCascade {

        @Agent
        StructuredAnswer ask(@V("ticket") Map<String, Object> ticket);
    }

    public interface ImageCascade {

        @Agent
        Object ask(@V("request") String request);
    }

    public interface CascadeWithClashingArgument {

        @Agent
        String ask(@V("candidateAnswer") String candidateAnswer);
    }

    // ── Agents and workflows composing the cascade into a sequence ──

    public static class Summarizer {

        @Agent(outputKey = "summary")
        public String summarize(@V("answer") String answer) {
            return "summary of " + answer;
        }
    }

    public interface SupportPipeline {

        @Agent
        String process(@V("request") String request);
    }

    private final FaqAgent faqAgent = new FaqAgent();
    private final ExpertAgent expertAgent = new ExpertAgent();
    private final SeniorExpertAgent seniorExpertAgent = new SeniorExpertAgent();

    private static DecisionModelMock modelGrading(double probability) {
        return DecisionModelMock.thatAlwaysAnswers(Map.of(QUESTION_NAME, YesNoAnswer.from(probability)));
    }

    /** A model grading the successive answers with the given probabilities, in turn. */
    private static DecisionModelMock modelGradingInTurn(double... probabilities) {
        AtomicInteger grades = new AtomicInteger();
        return DecisionModelMock.thatAnswers(
                request -> Map.of(QUESTION_NAME, YesNoAnswer.from(probabilities[grades.getAndIncrement()])));
    }

    private static <T> T cascade(Class<T> cascadeType, ConfidenceCascadePlanner planner, Object... tiers) {
        return AgenticServices.plannerBuilder(cascadeType)
                .subAgents(tiers)
                .outputKey("answer")
                .planner(() -> planner)
                .build();
    }

    private static SupportCascade cascade(DecisionModelMock model, Object... tiers) {
        return cascade(SupportCascade.class, new ConfidenceCascadePlanner(model, 0.5), tiers);
    }

    // ── Grading and escalation ──

    @Test
    void shouldReturnTheAnswerOfTheFirstTierWhenItIsAccepted() {
        DecisionModelMock model = modelGrading(0.9);

        assertThat(cascade(model, faqAgent, expertAgent).ask(REQUEST)).isEqualTo("faq answer");
        assertThat(faqAgent.calls).hasValue(1);
        assertThat(expertAgent.calls).hasValue(0);
        assertThat(model.requests()).hasSize(1);
    }

    @Test
    void shouldEscalateToTheNextTierWhenTheAnswerIsRejected() {
        DecisionModelMock model = modelGrading(0.2);

        assertThat(cascade(model, faqAgent, expertAgent).ask(REQUEST)).isEqualTo("expert answer");
        assertThat(faqAgent.calls).hasValue(1);
        assertThat(expertAgent.calls).hasValue(1);
        // the last tier is not graded
        assertThat(model.requests()).hasSize(1);
    }

    @Test
    void shouldNotGradeTheLastTier() {
        DecisionModelMock model = modelGrading(0.1);

        assertThat(cascade(model, faqAgent, expertAgent, seniorExpertAgent).ask(REQUEST))
                .isEqualTo("senior answer");
        assertThat(model.requests()).hasSize(2);
        assertThat(seniorExpertAgent.calls).hasValue(1);
    }

    @Test
    void shouldAcceptAProbabilityEqualToTheThreshold() {
        DecisionModelMock model = modelGrading(0.5);

        assertThat(cascade(model, faqAgent, expertAgent).ask(REQUEST)).isEqualTo("faq answer");
        assertThat(expertAgent.calls).hasValue(0);
    }

    @Test
    void shouldAcceptTheAnswerOfAMiddleTier() {
        DecisionModelMock model = modelGradingInTurn(0.1, 0.8);

        assertThat(cascade(model, faqAgent, expertAgent, seniorExpertAgent).ask(REQUEST))
                .isEqualTo("expert answer");
        assertThat(seniorExpertAgent.calls).hasValue(0);
    }

    @Test
    void shouldSendTheRequestAndTheCandidateAnswerToTheDecisionModel() {
        DecisionModelMock model = modelGrading(0.9);

        cascade(model, faqAgent, expertAgent).ask(REQUEST);

        DecisionRequest request = model.request();
        assertThat(request.input()).isEqualTo(FieldsInput.from(Map.of("request", REQUEST, ANSWER_KEY, "faq answer")));
        assertThat(request.questions()).containsOnlyKeys(QUESTION_NAME);
        YesNoQuestion question = (YesNoQuestion) request.questions().get(QUESTION_NAME);
        assertThat(question.text()).isEqualTo(QUESTION_TEXT);
        assertThat(question.yesWhen()).isNull();
    }

    @Test
    void shouldSendTheAcceptanceCriterionAsTheYesWhenDescription() {
        DecisionModelMock model = modelGrading(0.9);
        String criterion = "The answer gives concrete steps and does not ask the user to contact support";

        cascade(SupportCascade.class, new ConfidenceCascadePlanner(model, 0.5, criterion), faqAgent, expertAgent)
                .ask(REQUEST);

        YesNoQuestion question = (YesNoQuestion) model.request().questions().get(QUESTION_NAME);
        assertThat(question.text()).isEqualTo(QUESTION_TEXT);
        assertThat(question.yesWhen()).isEqualTo(criterion);
    }

    @Test
    void shouldSendTheArgumentsAsTheyAreAndConvertATypedAnswerToJsonValues() {
        DecisionModelMock model = modelGrading(0.9);
        Map<String, Object> ticket = Map.of("subject", "Descaling", "priority", 2);
        TicketMapCascade cascade = cascade(
                TicketMapCascade.class,
                new ConfidenceCascadePlanner(model, 0.5),
                new StructuredFaqAgent(),
                expertAgent);

        StructuredAnswer answer = cascade.ask(ticket);

        assertThat(answer).isEqualTo(new StructuredAnswer("faq answer", List.of("step 1", "step 2")));
        assertThat(model.request().input())
                .isEqualTo(FieldsInput.from(Map.of(
                        "ticket",
                        ticket,
                        ANSWER_KEY,
                        Map.of("text", "faq answer", "steps", List.of("step 1", "step 2")))));
    }

    @Test
    void shouldSendAContentAnswerAsItIs() {
        DecisionModelMock model = modelGrading(0.9);
        ImageContent image = ImageContent.from("https://example.com/descaling.png");
        ImageCascade cascade = cascade(
                ImageCascade.class, new ConfidenceCascadePlanner(model, 0.5), new ImageFaqAgent(image), expertAgent);

        assertThat(cascade.ask(REQUEST)).isSameAs(image);
        assertThat(model.request().input()).isEqualTo(FieldsInput.from(Map.of("request", REQUEST, ANSWER_KEY, image)));
    }

    @Test
    void shouldRejectAnArgumentThatTheDecisionModelDoesNotAccept() {
        DecisionModelMock model = modelGrading(0.9);
        TicketCascade cascade = cascade(
                TicketCascade.class, new ConfidenceCascadePlanner(model, 0.5), new StructuredFaqAgent(), expertAgent);

        // the record is sent as it is, like the arguments of a decision router, and the decision model rejects it
        assertThatThrownBy(() -> cascade.ask(new Ticket("Descaling", 2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("strings, numbers, booleans")
                .hasMessageContaining(Ticket.class.getName());
        assertThat(model.requests()).isEmpty();
        assertThat(expertAgent.calls).hasValue(0);
    }

    @Test
    void shouldWriteTheResultUnderTheCascadeOutputKey() {
        DecisionModelMock model = modelGrading(0.2);

        ResultWithAgenticScope<String> result = cascade(
                        SupportCascadeWithScope.class, new ConfidenceCascadePlanner(model, 0.5), faqAgent, expertAgent)
                .ask(REQUEST);

        assertThat(result.result()).isEqualTo("expert answer");
        assertThat(result.agenticScope().readState("answer", "")).isEqualTo("expert answer");
    }

    @Test
    void shouldBeAStepOfASequence() {
        DecisionModelMock model = modelGrading(0.2);

        SupportPipeline pipeline = AgenticServices.sequenceBuilder(SupportPipeline.class)
                .subAgents(cascade(model, faqAgent, expertAgent), new Summarizer())
                .outputKey("summary")
                .build();

        assertThat(pipeline.process(REQUEST)).isEqualTo("summary of expert answer");
    }

    @Test
    void shouldBeReportedAsASequence() {
        DecisionModelMock model = modelGrading(0.9);

        assertThat(new ConfidenceCascadePlanner(model, 0.5).topology()).isEqualTo(AgenticSystemTopology.SEQUENCE);
        assertThat(HtmlReportGenerator.generateTopology(cascade(model, faqAgent, expertAgent)))
                .contains("<span class=\"topology-badge seq\">Sequence</span>")
                .contains("<span class=\"agent-name\">faq</span>")
                .contains("<span class=\"agent-name\">expert</span>");
    }

    // ── Activation conditions ──

    private static AgenticScopeFunction<String> tier(
            String name, AtomicInteger calls, Predicate<AgenticScope> activationCondition) {
        return AgenticServices.nonAiAgentBuilder(scope -> {
                    calls.incrementAndGet();
                    return name + " answer";
                })
                .name(name)
                .outputKey(name + "Answer")
                .activationCondition(activationCondition)
                .build();
    }

    private static AgenticScopeFunction<String> inactiveTier(String name, AtomicInteger calls) {
        return tier(name, calls, scope -> false);
    }

    /** A tier whose condition holds when the previous answer is graded, but no more when the tier is invoked. */
    private static AgenticScopeFunction<String> tierSkippedAfterTheGrade(String name, AtomicInteger calls) {
        AtomicInteger evaluations = new AtomicInteger();
        return tier(name, calls, scope -> evaluations.incrementAndGet() == 1);
    }

    @Test
    void shouldPassOverAnInactiveTierWithoutGradingIt() {
        AtomicInteger inactiveCalls = new AtomicInteger();
        DecisionModelMock model = modelGrading(0.1);

        String answer = cascade(model, faqAgent, inactiveTier("expert", inactiveCalls), seniorExpertAgent)
                .ask(REQUEST);

        assertThat(answer).isEqualTo("senior answer");
        assertThat(model.requests()).hasSize(1);
        assertThat(inactiveCalls).hasValue(0);
    }

    @Test
    void shouldNotGradeTheAnswerWhenNoLaterTierIsActive() {
        AtomicInteger inactiveCalls = new AtomicInteger();
        DecisionModelMock model = modelGrading(0.1);

        assertThat(cascade(model, faqAgent, inactiveTier("expert", inactiveCalls))
                        .ask(REQUEST))
                .isEqualTo("faq answer");
        assertThat(model.requests()).isEmpty();
        assertThat(inactiveCalls).hasValue(0);
    }

    @Test
    void shouldReturnTheLatestAnswerWhenTheRemainingTiersAreSkippedAfterTheGrade() {
        AtomicInteger skippedCalls = new AtomicInteger();
        DecisionModelMock model = modelGrading(0.1);

        assertThat(cascade(model, faqAgent, tierSkippedAfterTheGrade("expert", skippedCalls))
                        .ask(REQUEST))
                .isEqualTo("faq answer");
        assertThat(model.requests()).hasSize(1);
        assertThat(skippedCalls).hasValue(0);
    }

    @Test
    void shouldHaveNoResultWhenEveryTierIsSkipped() {
        AtomicInteger inactiveCalls = new AtomicInteger();
        DecisionModelMock model = modelGrading(0.9);

        assertThat(cascade(model, inactiveTier("faq", inactiveCalls), inactiveTier("expert", inactiveCalls))
                        .ask(REQUEST))
                .isNull();
        assertThat(model.requests()).isEmpty();
        assertThat(inactiveCalls).hasValue(0);
    }

    // ── Delayed answers ──

    private static AgenticScopeFunction<String> asyncTier(String name, String outputKey) {
        return AgenticServices.nonAiAgentBuilder(scope -> {
                    LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(200));
                    return name + " answer";
                })
                .name(name)
                .outputKey(outputKey)
                .async(true)
                .build();
    }

    @Test
    void shouldWaitForTheAnswerOfAnAsyncTierBeforeGradingIt() {
        DecisionModelMock model = modelGrading(0.9);

        assertThat(cascade(model, asyncTier("faq", "faqAnswer"), expertAgent).ask(REQUEST))
                .isEqualTo("faq answer");
        assertThat(model.request().input())
                .isEqualTo(FieldsInput.from(Map.of("request", REQUEST, ANSWER_KEY, "faq answer")));
        assertThat(expertAgent.calls).hasValue(0);
    }

    @Test
    void shouldFailWhenTheDelayedAnswerOfATierHasNoOutputKey() {
        DecisionModelMock model = modelGrading(0.9);

        assertThatThrownBy(() ->
                        cascade(model, asyncTier("faq", null), expertAgent).ask(REQUEST))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'faq'")
                .hasMessageContaining("output key");
        assertThat(model.requests()).isEmpty();
        assertThat(expertAgent.calls).hasValue(0);
    }

    // ── Validation and failures ──

    @Test
    void shouldRejectANullDecisionModel() {
        assertThatThrownBy(() -> new ConfidenceCascadePlanner(null, 0.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("decisionModel");
    }

    @Test
    void shouldRejectAThresholdOutsideZeroToOne() {
        DecisionModelMock model = modelGrading(0.9);

        for (double threshold : new double[] {0.0, 1.0, -0.1, 1.5, Double.NaN}) {
            assertThatThrownBy(() -> new ConfidenceCascadePlanner(model, threshold))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Acceptance threshold");
        }
    }

    @Test
    void shouldRejectABlankAcceptanceCriterion() {
        DecisionModelMock model = modelGrading(0.9);

        assertThatThrownBy(() -> new ConfidenceCascadePlanner(model, 0.5, "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Acceptance criterion");
    }

    @Test
    void shouldRejectFewerThanTwoTiers() {
        DecisionModelMock model = modelGrading(0.9);

        assertThatThrownBy(() -> cascade(model, faqAgent).ask(REQUEST))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 2 subagents");
        assertThat(faqAgent.calls).hasValue(0);
    }

    @Test
    void shouldRejectAnUntypedCascade() {
        DecisionModelMock model = modelGrading(0.9);

        UntypedAgent cascade = AgenticServices.plannerBuilder()
                .subAgents(faqAgent, expertAgent)
                .planner(() -> new ConfidenceCascadePlanner(model, 0.5))
                .build();

        assertThatThrownBy(() -> cascade.invoke(Map.of("request", REQUEST)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("typed agent interface");
        assertThat(faqAgent.calls).hasValue(0);
    }

    @Test
    void shouldRejectAnArgumentNamedLikeTheCandidateAnswerField() {
        DecisionModelMock model = modelGrading(0.9);

        assertThatThrownBy(() -> cascade(
                                CascadeWithClashingArgument.class,
                                new ConfidenceCascadePlanner(model, 0.5),
                                faqAgent,
                                expertAgent)
                        .ask(REQUEST))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'candidateAnswer'");
        assertThat(faqAgent.calls).hasValue(0);
    }

    @Test
    void shouldPropagateADecisionModelFailure() {
        DecisionModelMock model = DecisionModelMock.thatAlwaysThrowsExceptionWithMessage("decision failed");

        assertThatThrownBy(() -> cascade(model, faqAgent, expertAgent).ask(REQUEST))
                .hasMessageContaining("decision failed");
        assertThat(faqAgent.calls).hasValue(1);
        assertThat(expertAgent.calls).hasValue(0);
    }

    @Test
    void shouldPropagateARefusalOfTheDecisionModel() {
        DecisionModelMock model = DecisionModelMock.thatAlwaysAnswers(Map.of(QUESTION_NAME, RefusalAnswer.of()));

        assertThatThrownBy(() -> cascade(model, faqAgent, expertAgent).ask(REQUEST))
                .isInstanceOf(ContentFilteredException.class);
        assertThat(expertAgent.calls).hasValue(0);
    }
}
