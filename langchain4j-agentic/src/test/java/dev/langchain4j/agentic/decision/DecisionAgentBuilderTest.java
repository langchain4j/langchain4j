package dev.langchain4j.agentic.decision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.agent.AgentInvocationException;
import dev.langchain4j.agentic.agent.DecisionAgentBuilder;
import dev.langchain4j.agentic.decision.DecisionAgents.CategoryRouter;
import dev.langchain4j.agentic.decision.DecisionAgents.ConfidentCategoryRouter;
import dev.langchain4j.agentic.decision.DecisionAgents.RequestCategory;
import dev.langchain4j.agentic.decision.DecisionAgents.TicketTriage;
import dev.langchain4j.agentic.decision.DecisionAgents.TransactionGate;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.model.decision.mock.DecisionModelMock;
import dev.langchain4j.model.decision.request.DecisionRequestParameters;
import dev.langchain4j.service.IllegalConfigurationException;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.decision.Decide;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DecisionAgentBuilderTest {

    private static final DecisionModelMock MODEL = DecisionMocks.choosing("LEGAL", 0.9);

    @Test
    void should_fail_without_decision_model() {
        assertThatThrownBy(() -> AgenticServices.decisionAgentBuilder(CategoryRouter.class)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "One and only one of decisionModel and decisionModelProvider must be set for the decision agent 'classify'");
    }

    @Test
    void should_fail_with_both_a_decision_model_and_a_provider() {
        assertThatThrownBy(() -> AgenticServices.decisionAgentBuilder(CategoryRouter.class)
                        .decisionModel(MODEL)
                        .decisionModelProvider(scope -> MODEL)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("One and only one of decisionModel and decisionModelProvider");
    }

    @Test
    void should_fail_when_the_provider_returns_null() {
        CategoryRouter router = AgenticServices.decisionAgentBuilder(CategoryRouter.class)
                .decisionModelProvider(scope -> null)
                .build();
        UntypedAgent sequence = AgenticServices.sequenceBuilder()
                .subAgents(router)
                .outputKey("category")
                .build();

        assertThatThrownBy(() -> sequence.invoke(Map.of("request", "Can my landlord keep my deposit?")))
                .isInstanceOf(AgentInvocationException.class)
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("The decision model provider of the decision agent 'classify' returned null");
    }

    public static class RouterClass {

        @Decide("Which kind of expert should handle this request?")
        @Agent(outputKey = "category")
        public RequestCategory classify(@V("request") String request) {
            return RequestCategory.UNKNOWN;
        }
    }

    @Test
    void should_reject_a_class() {
        assertThatThrownBy(() -> AgenticServices.decisionAgentBuilder(RouterClass.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A decision agent must be defined by an interface, but " + RouterClass.class.getName()
                        + " is a class");
    }

    public interface NoAgentMethod {

        @Decide("Which kind of expert should handle this request?")
        RequestCategory classify(@V("request") String request);
    }

    @Test
    void should_reject_an_interface_without_agent_method() {
        assertThatThrownBy(() -> AgenticServices.decisionAgentBuilder(NoAgentMethod.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No agent method found in class: " + NoAgentMethod.class.getName());
    }

    public interface TwoAgentMethods {

        @Decide("Which kind of expert should handle this request?")
        @Agent(outputKey = "category")
        RequestCategory classify(@V("request") String request);

        @Decide("Is this message spam?")
        @Agent(outputKey = "spam")
        boolean isSpam(@V("request") String request);
    }

    @Test
    void should_reject_an_interface_with_two_agent_methods() {
        assertThatThrownBy(() -> AgenticServices.decisionAgentBuilder(TwoAgentMethods.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Multiple agent methods found in class: " + TwoAgentMethods.class.getName());
    }

    public interface PromptedRouter {

        @UserMessage("Categorize {{request}}")
        @Decide("Which kind of expert should handle this request?")
        @Agent(outputKey = "category")
        RequestCategory classify(@V("request") String request);
    }

    @Test
    void should_reject_a_user_message() {
        assertThatThrownBy(() -> AgenticServices.decisionAgentBuilder(PromptedRouter.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Method 'classify' of the decision agent " + PromptedRouter.class.getName()
                        + " must not be annotated with @UserMessage or @SystemMessage: a decision agent has no prompt, "
                        + "its question is the one of @Decide");
    }

    public interface SystemPromptedRouter {

        @SystemMessage("You are a router")
        @Decide("Which kind of expert should handle this request?")
        @Agent(outputKey = "category")
        RequestCategory classify(@V("request") String request);
    }

    @Test
    void should_reject_a_system_message() {
        assertThatThrownBy(() -> AgenticServices.decisionAgentBuilder(SystemPromptedRouter.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Method 'classify' of the decision agent " + SystemPromptedRouter.class.getName()
                        + " must not be annotated with @UserMessage or @SystemMessage: a decision agent has no prompt, "
                        + "its question is the one of @Decide");
    }

    public interface FutureRouter {

        @Decide("Which kind of expert should handle this request?")
        @Agent(outputKey = "category")
        CompletableFuture<RequestCategory> classify(@V("request") String request);
    }

    @Test
    void should_reject_a_future_return_type() {
        assertThatThrownBy(() -> AgenticServices.decisionAgentBuilder(FutureRouter.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Method 'classify' of the decision agent " + FutureRouter.class.getName()
                        + " returns a CompletableFuture, which is not supported: declare the type of the result directly "
                        + "and make the agent asynchronous with async(true) or @Agent(async = true)");
    }

    public interface RouterWithMemoryId {

        @Decide("Which kind of expert should handle this request?")
        @Agent(outputKey = "category")
        RequestCategory classify(@MemoryId String memoryId, @V("request") String request);
    }

    @Test
    void should_reject_a_memory_id_parameter() {
        assertThatThrownBy(() -> AgenticServices.decisionAgentBuilder(RouterWithMemoryId.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("of method 'classify' of the decision agent")
                .hasMessageContaining(
                        "is annotated with @MemoryId, which is not supported: a decision agent has no chat memory");
    }

    public interface RouterWithScope {

        @Decide("Which kind of expert should handle this request?")
        @Agent(outputKey = "category")
        RequestCategory classify(AgenticScope scope, @V("request") String request);
    }

    @Test
    void should_reject_an_agentic_scope_parameter() {
        assertThatThrownBy(() -> AgenticServices.decisionAgentBuilder(RouterWithScope.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                        "has type AgenticScope, which is not supported: the arguments of a decision agent "
                                + "are read from the AgenticScope by name and sent to the decision model");
    }

    public interface RouterWithRequestParameters {

        @Decide("Which kind of expert should handle this request?")
        @Agent(outputKey = "category")
        RequestCategory classify(@V("request") String request, DecisionRequestParameters parameters);
    }

    @Test
    void should_reject_a_request_parameters_parameter() {
        assertThatThrownBy(() -> AgenticServices.decisionAgentBuilder(RouterWithRequestParameters.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                        "has type DecisionRequestParameters, which is not supported: set the request "
                                + "parameters with requestParameters(...) on the builder, or as default parameters of the decision model");
    }

    public interface TextRouter {

        @Decide("Which kind of expert should handle this request?")
        @Agent(outputKey = "category")
        String classify(@V("request") String request);
    }

    @Test
    void should_reject_an_unsupported_return_type() {
        assertThatThrownBy(() -> AgenticServices.decisionAgentBuilder(TextRouter.class))
                .isInstanceOf(IllegalConfigurationException.class)
                .hasMessageContaining("Method 'classify' has an unsupported return type: java.lang.String");
    }

    public interface UndecidedRouter {

        @Agent(outputKey = "category")
        RequestCategory classify(@V("request") String request);
    }

    @Test
    void should_reject_a_method_without_decide() {
        assertThatThrownBy(() -> AgenticServices.decisionAgentBuilder(UndecidedRouter.class))
                .isInstanceOf(IllegalConfigurationException.class)
                .hasMessageContaining("Method 'classify' must be annotated with @Decide");
    }

    @Test
    void should_reject_a_threshold_together_with_a_threshold_provider() {
        assertThatThrownBy(() -> AgenticServices.decisionAgentBuilder(CategoryRouter.class)
                        .decisionModel(MODEL)
                        .threshold(0.7)
                        .thresholdProvider(context -> 0.7)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Only one of threshold and thresholdProvider can be set for the decision agent 'classify'");
    }

    @Test
    void should_reject_a_threshold_out_of_range() {
        DecisionAgentBuilder<CategoryRouter> builder = AgenticServices.decisionAgentBuilder(CategoryRouter.class);

        assertThatThrownBy(() -> builder.threshold(1.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The threshold of the decision agent 'classify' must be between 0 and 1, but was 1.5");
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.1, 1.0001, Double.NaN})
    void should_reject_a_threshold_outside_of_zero_and_one(double threshold) {
        DecisionAgentBuilder<CategoryRouter> builder = AgenticServices.decisionAgentBuilder(CategoryRouter.class);

        assertThatThrownBy(() -> builder.threshold(threshold)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 1.0})
    void should_accept_a_threshold_of_zero_or_one(double threshold) {
        TransactionGate gate = AgenticServices.decisionAgentBuilder(TransactionGate.class)
                .decisionModel(MODEL)
                .threshold(threshold)
                .build();

        assertThat(gate).isNotNull();
    }

    @Test
    void should_reject_a_threshold_on_an_agent_without_boolean_answer() {
        assertThatThrownBy(() -> AgenticServices.decisionAgentBuilder(CategoryRouter.class)
                        .decisionModel(MODEL)
                        .threshold(0.7)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "The decision agent 'classify' has no boolean answer, so a threshold would never be used: "
                                + "apply it to the probabilities of a YesNoAnswer, Choice or Scale answer instead, "
                                + "for instance with YesNoAnswer.isYes(threshold)");
    }

    @Test
    void should_reject_a_threshold_provider_on_an_agent_without_boolean_answer() {
        assertThatThrownBy(() -> AgenticServices.decisionAgentBuilder(ConfidentCategoryRouter.class)
                        .decisionModel(MODEL)
                        .thresholdProvider(context -> 0.7)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("has no boolean answer");
    }

    @Test
    void should_accept_a_threshold_on_a_record_with_a_boolean_answer() {
        TicketTriage triage = AgenticServices.decisionAgentBuilder(TicketTriage.class)
                .decisionModel(MODEL)
                .threshold(0.7)
                .build();

        assertThat(triage).isNotNull();
    }

    @Test
    void should_read_the_agent_metadata_from_the_annotation() {
        CategoryRouter router = AgenticServices.decisionAgentBuilder(CategoryRouter.class)
                .decisionModel(MODEL)
                .build();

        assertThat(router).isInstanceOf(AgentInstance.class);
        AgentInstance agent = (AgentInstance) router;
        assertThat(agent.name()).isEqualTo("classify");
        assertThat(agent.description()).isEqualTo("Categorizes a user request");
        assertThat(agent.outputKey()).isEqualTo("category");
        assertThat(agent.outputType()).isEqualTo(RequestCategory.class);
        assertThat(agent.async()).isFalse();
        assertThat(agent.optional()).isFalse();
        assertThat(agent.topology()).isEqualTo(AgenticSystemTopology.DECISION_AGENT);
        assertThat(agent.arguments()).extracting(a -> a.name()).containsExactly("request");
        assertThat(router.toString()).isEqualTo("DecisionAgent<CategoryRouter>");
    }
}
