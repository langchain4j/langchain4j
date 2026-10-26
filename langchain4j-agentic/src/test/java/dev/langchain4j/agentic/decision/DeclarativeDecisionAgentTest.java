package dev.langchain4j.agentic.decision;

import static dev.langchain4j.agentic.decision.DecisionMocks.answeringYes;
import static dev.langchain4j.agentic.decision.DecisionMocks.choosing;
import static dev.langchain4j.agentic.decision.DecisionMocks.triaging;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.AgenticServices.AgentConfigurator;
import dev.langchain4j.agentic.agent.DecisionAgentBuilder;
import dev.langchain4j.agentic.decision.DecisionAgents.Quality;
import dev.langchain4j.agentic.decision.DecisionAgents.RequestCategory;
import dev.langchain4j.agentic.decision.DecisionAgents.Team;
import dev.langchain4j.agentic.decision.DecisionAgents.Triage;
import dev.langchain4j.agentic.declarative.ActivationCondition;
import dev.langchain4j.agentic.declarative.AgentListenerSupplier;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.declarative.ConditionalAgent;
import dev.langchain4j.agentic.declarative.DecisionModelSupplier;
import dev.langchain4j.agentic.declarative.DecisionThresholdSupplier;
import dev.langchain4j.agentic.declarative.DeclarativeUtil;
import dev.langchain4j.agentic.declarative.ExitCondition;
import dev.langchain4j.agentic.declarative.HumanInTheLoop;
import dev.langchain4j.agentic.declarative.LoopAgent;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.declarative.SupplierParameterResolver;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentMonitor;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemConfigurationException;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.mock.DecisionModelMock;
import dev.langchain4j.model.decision.request.ScaleQuestion;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.decision.Decide;
import dev.langchain4j.service.decision.Scale;
import dev.langchain4j.service.decision.ThresholdContext;
import dev.langchain4j.service.decision.ThresholdProvider;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DeclarativeDecisionAgentTest {

    static final DecisionModelMock LEGAL_MODEL = choosing("LEGAL", 0.9);
    static final DecisionModelMock MEDICAL_MODEL = choosing("MEDICAL", 0.9);
    static final AtomicInteger FIXED_SUPPLIER_CALLS = new AtomicInteger();
    static final AtomicInteger DYNAMIC_SUPPLIER_CALLS = new AtomicInteger();
    static final AgentMonitor MONITOR = new AgentMonitor();
    static final List<ThresholdContext> THRESHOLD_CONTEXTS = new CopyOnWriteArrayList<>();

    @BeforeEach
    void reset() {
        FIXED_SUPPLIER_CALLS.set(0);
        DYNAMIC_SUPPLIER_CALLS.set(0);
        MONITOR.clear();
        THRESHOLD_CONTEXTS.clear();
    }

    @AfterEach
    void clearResolvers() {
        DeclarativeUtil.getSupplierParameterResolvers().clear();
    }

    public interface CategoryRouter {

        @Decide("Which kind of expert should handle this request?")
        @Agent(value = "Categorizes a user request", outputKey = "category")
        RequestCategory classify(@V("request") String request);

        @DecisionModelSupplier
        static DecisionModel decisionModel() {
            FIXED_SUPPLIER_CALLS.incrementAndGet();
            return LEGAL_MODEL;
        }

        @AgentListenerSupplier
        static AgentListener listener() {
            return MONITOR;
        }
    }

    @Test
    void should_build_a_root_decision_agent_with_a_fixed_decision_model() {
        CategoryRouter router = AgenticServices.createAgenticSystem(CategoryRouter.class);

        assertThat(router.classify("Can my landlord keep my deposit?")).isEqualTo(RequestCategory.LEGAL);
        assertThat(FIXED_SUPPLIER_CALLS).hasValue(1);
        assertThat(((AgentInstance) router).topology()).isEqualTo(AgenticSystemTopology.DECISION_AGENT);
        assertThat(MONITOR.successfulExecutions()).hasSize(1);
    }

    public interface DynamicCategoryRouter {

        @Decide("Which kind of expert should handle this request?")
        @Agent(value = "Categorizes a user request", outputKey = "category")
        RequestCategory classify(@V("request") String request);

        @DecisionModelSupplier
        static DecisionModel decisionModel(@V("request") String request) {
            DYNAMIC_SUPPLIER_CALLS.incrementAndGet();
            return request.contains("headache") ? MEDICAL_MODEL : LEGAL_MODEL;
        }
    }

    public static class Replier {

        @Agent(outputKey = "answer")
        public static String reply(@V("category") RequestCategory category) {
            return "The " + category + " expert will answer";
        }
    }

    public interface RoutingSequence {

        @SequenceAgent(
                outputKey = "answer",
                subAgents = {DynamicCategoryRouter.class, Replier.class})
        String answer(@V("request") String request);
    }

    @Test
    void should_pick_the_decision_model_from_the_scope_in_a_sequence() {
        RoutingSequence sequence = AgenticServices.createAgenticSystem(RoutingSequence.class);

        assertThat(sequence.answer("I have a headache")).isEqualTo("The MEDICAL expert will answer");
        assertThat(sequence.answer("Can my landlord keep my deposit?")).isEqualTo("The LEGAL expert will answer");
        assertThat(DYNAMIC_SUPPLIER_CALLS).hasValue(2);
    }

    public interface TransactionGate {

        @Decide("Does this money transfer look suspicious?")
        @Agent(value = "Checks whether a money transfer looks suspicious", outputKey = "suspicious")
        boolean check(@V("transfer") String transfer);

        @DecisionModelSupplier
        static DecisionModel decisionModel() {
            return answeringYes(0.7);
        }

        @DecisionThresholdSupplier
        static double threshold(ThresholdContext context) {
            THRESHOLD_CONTEXTS.add(context);
            return 0.8;
        }
    }

    public static class MoneyMover {

        @Agent(outputKey = "receipt")
        public static String move(@V("transfer") String transfer) {
            return "moved: " + transfer;
        }
    }

    public interface GatedTransfer {

        @ConditionalAgent(
                outputKey = "receipt",
                subAgents = {MoneyMover.class})
        String transfer(@V("suspicious") boolean suspicious, @V("transfer") String transfer);

        @ActivationCondition(MoneyMover.class)
        static boolean notSuspicious(@V("suspicious") boolean suspicious) {
            return !suspicious;
        }
    }

    public interface GatedTransferSequence {

        @SequenceAgent(
                outputKey = "receipt",
                subAgents = {TransactionGate.class, GatedTransfer.class})
        String transfer(@V("transfer") String transfer);
    }

    @Test
    void should_apply_the_declared_threshold_in_a_conditional_workflow() {
        GatedTransferSequence sequence = AgenticServices.createAgenticSystem(GatedTransferSequence.class);

        // the probability of "yes" is 0.7, below the threshold of 0.8, so the transfer is not suspicious
        assertThat(sequence.transfer("10 USD to the baker")).isEqualTo("moved: 10 USD to the baker");
        assertThat(sequence.transfer("20 USD to the baker")).isEqualTo("moved: 20 USD to the baker");

        // the threshold is asked at every invocation, for the question of the agent method
        assertThat(THRESHOLD_CONTEXTS).hasSize(2);
        ThresholdContext context = THRESHOLD_CONTEXTS.get(0);
        assertThat(context.serviceInterface()).isEqualTo(TransactionGate.class);
        assertThat(context.method().getName()).isEqualTo("check");
        assertThat(context.questionName()).isEqualTo("check");
    }

    /** Marks the parameters provided by the test resolver, as a dependency injection container would do. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    @interface Injected {}

    public record ThresholdConfig(double threshold) {}

    public interface ConfiguredTransactionGate {

        @Decide("Does this money transfer look suspicious?")
        @Agent(value = "Checks whether a money transfer looks suspicious", outputKey = "suspicious")
        boolean check(@V("transfer") String transfer);

        @DecisionModelSupplier
        static DecisionModel decisionModel() {
            return answeringYes(0.7);
        }

        @DecisionThresholdSupplier
        static double threshold(@Injected ThresholdConfig config, ThresholdContext context) {
            return config.threshold();
        }
    }

    @Test
    void should_resolve_the_other_parameters_of_the_threshold_supplier() {
        ThresholdConfig[] config = {new ThresholdConfig(0.8)};
        DeclarativeUtil.addSupplierParameterResolver(new SupplierParameterResolver() {
            @Override
            public boolean supports(Context context) {
                return context.parameter().isAnnotationPresent(Injected.class);
            }

            @Override
            public Object resolve(Context context) {
                return config[0];
            }
        });

        ConfiguredTransactionGate gate = AgenticServices.createAgenticSystem(ConfiguredTransactionGate.class);

        // the probability of "yes" is 0.7: below the threshold of 0.8, at least the threshold of 0.6
        assertThat(gate.check("10 USD to the baker")).isFalse();
        config[0] = new ThresholdConfig(0.6);
        assertThat(gate.check("10 USD to the baker")).isTrue();
    }

    @Test
    void should_fail_when_a_parameter_of_the_threshold_supplier_is_not_resolved() {
        assertThatThrownBy(() -> AgenticServices.createAgenticSystem(ConfiguredTransactionGate.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must have 1 arguments: [class " + ThresholdContext.class.getName() + "]");
    }

    public interface ContextlessTransactionGate {

        @Decide("Does this money transfer look suspicious?")
        @Agent(value = "Checks whether a money transfer looks suspicious", outputKey = "suspicious")
        boolean check(@V("transfer") String transfer);

        @DecisionModelSupplier
        static DecisionModel decisionModel() {
            return answeringYes(0.7);
        }

        @DecisionThresholdSupplier
        static double threshold() {
            return 0.8;
        }
    }

    @Test
    void should_fail_when_the_threshold_supplier_does_not_take_the_threshold_context() {
        assertThatThrownBy(() -> AgenticServices.createAgenticSystem(ContextlessTransactionGate.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must have 1 arguments")
                .hasMessageContaining(ThresholdContext.class.getName());
    }

    public interface ProviderReturningTransactionGate {

        @Decide("Does this money transfer look suspicious?")
        @Agent(value = "Checks whether a money transfer looks suspicious", outputKey = "suspicious")
        boolean check(@V("transfer") String transfer);

        @DecisionModelSupplier
        static DecisionModel decisionModel() {
            return answeringYes(0.7);
        }

        @DecisionThresholdSupplier
        static ThresholdProvider threshold(ThresholdContext context) {
            return ignored -> 0.8;
        }
    }

    @Test
    void should_fail_when_the_threshold_supplier_does_not_return_a_double() {
        assertThatThrownBy(() -> AgenticServices.createAgenticSystem(ProviderReturningTransactionGate.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must return double");
    }

    public interface StyleScorer {

        @Decide("How well does the story match the required style?")
        @Agent(value = "Scores a story against a style", outputKey = "styleScore")
        Scale<Quality> scoreStyle(@V("story") String story, @V("style") String style);

        @DecisionModelSupplier
        static DecisionModel decisionModel() {
            return DecisionModelMock.thatAnswersQuestions(question -> DecisionMocks.scaleAnswer(
                    (ScaleQuestion) question, SCORES.get(Math.min(SCORED.getAndIncrement(), SCORES.size() - 1)), 1.0));
        }
    }

    static final List<String> SCORES = List.of("POOR", "FAIR", "EXCELLENT");
    static final AtomicInteger SCORED = new AtomicInteger();

    public static class StyleEditor {

        @Agent(outputKey = "story")
        public static String edit(@V("story") String story) {
            return story + "!";
        }
    }

    public interface StyleLoop {

        @LoopAgent(
                outputKey = "story",
                maxIterations = 5,
                subAgents = {StyleScorer.class, StyleEditor.class})
        String improve(@V("story") String story, @V("style") String style);

        @ExitCondition
        static boolean goodEnough(@V("styleScore") Scale<Quality> styleScore) {
            // at least GOOD, the third of the four levels
            return styleScore.mean() >= 2;
        }
    }

    @Test
    void should_drive_the_exit_condition_of_a_loop() {
        SCORED.set(0);
        StyleLoop loop = AgenticServices.createAgenticSystem(StyleLoop.class);

        String story = loop.improve("Once upon a time", "fairy tale");

        // POOR and FAIR are below the bar, EXCELLENT ends the loop before the third edit
        assertThat(story).isEqualTo("Once upon a time!!");
        assertThat(SCORED).hasValue(3);
    }

    public interface TicketTriage {

        @Agent(value = "Triages a support ticket", outputKey = "triage")
        Triage triage(@V("ticket") String ticket);

        @DecisionModelSupplier
        static DecisionModel decisionModel() {
            return triaging("BILLING", 0.8, "ANGRY");
        }
    }

    @Test
    void should_recognize_a_record_returning_interface_by_its_decision_model_supplier() {
        TicketTriage triage = AgenticServices.createAgenticSystem(TicketTriage.class);

        assertThat(triage.triage("I was charged twice!").team()).isEqualTo(Team.BILLING);
    }

    public interface UnsuppliedRouter {

        @Decide("Which kind of expert should handle this request?")
        @Agent(value = "Categorizes a user request", outputKey = "category")
        RequestCategory classify(@V("request") String request);
    }

    @Test
    void should_fail_without_decision_model_supplier() {
        assertThatThrownBy(() -> AgenticServices.createAgenticSystem(UnsuppliedRouter.class))
                .isInstanceOf(AgenticSystemConfigurationException.class)
                .hasMessage("DecisionModel not provided for the decision agent " + UnsuppliedRouter.class.getName()
                        + ". Please provide one with a static method annotated with @DecisionModelSupplier.");
    }

    public interface ConflictingRouter {

        @Decide("Which kind of expert should handle this request?")
        @Agent(value = "Categorizes a user request", outputKey = "category")
        RequestCategory classify(@V("request") String request);

        @DecisionModelSupplier
        static DecisionModel decisionModel() {
            return LEGAL_MODEL;
        }

        @ChatModelSupplier
        static ChatModel chatModel() {
            return ChatModelMock.thatAlwaysResponds("LEGAL");
        }
    }

    @Test
    void should_fail_with_both_a_decision_model_supplier_and_a_chat_model_supplier() {
        assertThatThrownBy(() -> AgenticServices.createAgenticSystem(ConflictingRouter.class))
                .isInstanceOf(AgenticSystemConfigurationException.class)
                .hasMessage("The decision agent " + ConflictingRouter.class.getName()
                        + " declares a method annotated with @ChatModelSupplier: a decision agent uses a decision model, "
                        + "provided with @DecisionModelSupplier, and no chat model");
    }

    private static String notBuiltAsADecisionAgent(Class<?> agentType, Class<?> annotation) {
        return "The agent " + agentType.getName() + " declares a method annotated with @" + annotation.getSimpleName()
                + ", but it is not built as a decision agent: a decision agent is an interface with an @Agent method "
                + "annotated with @Decide, or declaring a @DecisionModelSupplier, built with "
                + "AgenticServices.decisionAgentBuilder or by the declarative API";
    }

    public interface SpamCheckingAiAgent {

        @UserMessage("Is this message spam? {{message}}")
        @Agent(value = "Checks whether a message is spam", outputKey = "spam")
        String check(@V("message") String message);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return ChatModelMock.thatAlwaysResponds("no");
        }

        @DecisionThresholdSupplier
        static double threshold(ThresholdContext context) {
            return 0.8;
        }
    }

    @Test
    void should_fail_with_a_threshold_supplier_on_an_ai_agent() {
        assertThatThrownBy(() -> AgenticServices.createAgenticSystem(SpamCheckingAiAgent.class))
                .isInstanceOf(AgenticSystemConfigurationException.class)
                .hasMessage(notBuiltAsADecisionAgent(SpamCheckingAiAgent.class, DecisionThresholdSupplier.class));
    }

    public interface ThresholdOnCategoryRouter {

        @Decide("Which kind of expert should handle this request?")
        @Agent(value = "Categorizes a user request", outputKey = "category")
        RequestCategory classify(@V("request") String request);

        @DecisionModelSupplier
        static DecisionModel decisionModel() {
            return choosing("LEGAL", 0.9);
        }

        @DecisionThresholdSupplier
        static double threshold(ThresholdContext context) {
            return 0.8;
        }
    }

    @Test
    void should_fail_with_a_threshold_supplier_on_an_agent_without_boolean_answer() {
        assertThatThrownBy(() -> AgenticServices.createAgenticSystem(ThresholdOnCategoryRouter.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The decision agent 'classify' has no boolean answer");
    }

    @Test
    void should_fail_to_build_a_decision_agent_interface_with_the_ai_agent_builder() {
        assertThatThrownBy(() -> AgenticServices.agentBuilder(CategoryRouter.class))
                .isInstanceOf(AgenticSystemConfigurationException.class)
                .hasMessage(notBuiltAsADecisionAgent(CategoryRouter.class, DecisionModelSupplier.class));
    }

    public interface RoutingSequenceWithDecisionModel {

        @SequenceAgent(
                outputKey = "answer",
                subAgents = {CategoryRouter.class, Replier.class})
        String answer(@V("request") String request);

        @DecisionModelSupplier
        static DecisionModel decisionModel() {
            return LEGAL_MODEL;
        }
    }

    @Test
    void should_fail_with_a_decision_model_supplier_on_a_workflow() {
        assertThatThrownBy(() -> AgenticServices.createAgenticSystem(RoutingSequenceWithDecisionModel.class))
                .isInstanceOf(AgenticSystemConfigurationException.class)
                .hasMessage(
                        notBuiltAsADecisionAgent(RoutingSequenceWithDecisionModel.class, DecisionModelSupplier.class));
    }

    public static class ReplierWithDecisionModel {

        @Agent(outputKey = "answer")
        public static String reply(@V("category") RequestCategory category) {
            return "The " + category + " expert will answer";
        }

        @DecisionModelSupplier
        public static DecisionModel decisionModel() {
            return LEGAL_MODEL;
        }
    }

    public interface RoutingSequenceWithDecisionReplier {

        @SequenceAgent(
                outputKey = "answer",
                subAgents = {CategoryRouter.class, ReplierWithDecisionModel.class})
        String answer(@V("request") String request);
    }

    @Test
    void should_fail_with_a_decision_model_supplier_on_a_non_ai_agent() {
        assertThatThrownBy(() -> AgenticServices.createAgenticSystem(RoutingSequenceWithDecisionReplier.class))
                .isInstanceOf(AgenticSystemConfigurationException.class)
                .hasMessage(notBuiltAsADecisionAgent(ReplierWithDecisionModel.class, DecisionModelSupplier.class));
    }

    public interface Confirmation {

        @HumanInTheLoop(description = "Asks a confirmation", outputKey = "confirmation")
        static String confirm(@V("category") RequestCategory category) {
            return "yes";
        }

        @DecisionThresholdSupplier
        static double threshold(ThresholdContext context) {
            return 0.8;
        }
    }

    public interface RoutingSequenceWithConfirmation {

        @SequenceAgent(
                outputKey = "confirmation",
                subAgents = {CategoryRouter.class, Confirmation.class})
        String answer(@V("request") String request);
    }

    @Test
    void should_fail_with_a_threshold_supplier_on_a_human_in_the_loop_agent() {
        assertThatThrownBy(() -> AgenticServices.createAgenticSystem(RoutingSequenceWithConfirmation.class))
                .isInstanceOf(AgenticSystemConfigurationException.class)
                .hasMessage(notBuiltAsADecisionAgent(Confirmation.class, DecisionThresholdSupplier.class));
    }

    @Test
    void should_expose_the_decision_agent_builder_to_the_agent_configurator() {
        List<Class<?>> configured = new ArrayList<>();
        AgentConfigurator configurator = new AgentConfigurator(context -> {
            configured.add(context.agentServiceClass());
            DecisionAgentBuilder<?> builder = context.decisionAgentBuilder();
            if (builder != null) {
                assertThat(context.agentBuilder()).isNull();
                builder.outputKey("decision");
            }
        });

        RoutingSequence sequence = AgenticServices.createAgenticSystem(RoutingSequence.class, configurator);

        assertThat(configured).contains(DynamicCategoryRouter.class);
        AgentInstance router = ((AgentInstance) sequence).subagents().get(0);
        assertThat(router.outputKey()).isEqualTo("decision");
    }

    @Test
    void should_use_a_prebuilt_decision_agent_from_the_subagent_resolver() {
        DecisionModelMock technicalModel = choosing("TECHNICAL", 0.9);
        // a prebuilt agent of another type, since the supplier of DynamicCategoryRouter would also apply
        DecisionAgents.CategoryRouter prebuilt = AgenticServices.decisionAgentBuilder(
                        DecisionAgents.CategoryRouter.class)
                .decisionModel(technicalModel)
                .build();
        AgentConfigurator configurator = new AgentConfigurator(
                context -> {}, type -> type == DynamicCategoryRouter.class ? prebuilt : null, null);

        RoutingSequence sequence = AgenticServices.createAgenticSystem(RoutingSequence.class, configurator);

        assertThat(sequence.answer("My laptop does not boot")).isEqualTo("The TECHNICAL expert will answer");
        assertThat(technicalModel.requests()).hasSize(1);
        assertThat(DYNAMIC_SUPPLIER_CALLS).hasValue(0);
    }

    @Test
    void should_build_a_decision_agent_passed_as_a_class_to_a_programmatic_workflow() {
        AgenticScope scope = AgenticServices.sequenceBuilder()
                .subAgents(CategoryRouter.class)
                .outputKey("category")
                .build()
                .invokeWithAgenticScope(Map.of("request", "Can my landlord keep my deposit?"))
                .agenticScope();

        assertThat(scope.readState("category")).isEqualTo(RequestCategory.LEGAL);
    }
}
