package dev.langchain4j.agentic.decision;

import static dev.langchain4j.agentic.Models.baseModel;
import static dev.langchain4j.agentic.decision.DecisionModels.typeSafe;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.Agents.LegalExpert;
import dev.langchain4j.agentic.Agents.MedicalExpert;
import dev.langchain4j.agentic.Agents.TechnicalExpert;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.decision.DecisionAgents.ConfidentCategoryRouter;
import dev.langchain4j.agentic.decision.DecisionAgents.RequestCategory;
import dev.langchain4j.agentic.declarative.ActivationCondition;
import dev.langchain4j.agentic.declarative.ConditionalAgent;
import dev.langchain4j.agentic.declarative.DecisionModelSupplier;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.observability.AgentInvocation;
import dev.langchain4j.agentic.observability.AgentMonitor;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.service.V;
import dev.langchain4j.service.decision.Choice;
import dev.langchain4j.service.decision.Decide;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Scenario 1: a decision agent classifies the request and a conditional workflow routes it to one expert, with a
 * human fallback when no category is likely enough.
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TYPESAFE_API_KEY", matches = ".+")
public class DecisionClassifierRoutingIT {

    private static final String LEGAL_REQUEST = "Can my landlord keep my deposit because I painted a wall?";
    private static final String MEDICAL_REQUEST =
            "I have had a headache and a mild fever for three days, what should I do?";
    private static final String TECHNICAL_REQUEST = "My laptop does not boot after the last update, how can I fix it?";
    private static final String OFF_TOPIC_REQUEST = "How do I bake a chocolate cake for six people?";

    private static boolean confident(AgenticScope scope, RequestCategory category) {
        Choice<RequestCategory> choice = (Choice<RequestCategory>) scope.readState("category");
        return choice.value() == category && choice.probabilityOf(category) >= 0.5;
    }

    private static UntypedAgent routingSystem(AgentMonitor monitor) {
        ConfidentCategoryRouter router = AgenticServices.decisionAgentBuilder(ConfidentCategoryRouter.class)
                .decisionModel(typeSafe())
                .build();
        MedicalExpert medical = AgenticServices.agentBuilder(MedicalExpert.class)
                .chatModel(baseModel())
                .outputKey("response")
                .build();
        LegalExpert legal = AgenticServices.agentBuilder(LegalExpert.class)
                .chatModel(baseModel())
                .outputKey("response")
                .build();
        TechnicalExpert technical = AgenticServices.agentBuilder(TechnicalExpert.class)
                .chatModel(baseModel())
                .outputKey("response")
                .build();
        Object human = AgenticServices.nonAiAgentBuilder(
                        scope -> "A human will answer: " + scope.readState("request", ""))
                .name("human")
                .description("Forwards the request to a human")
                .outputKey("response")
                .build();
        UntypedAgent experts = AgenticServices.conditionalBuilder()
                .subAgents("the request is medical", scope -> confident(scope, RequestCategory.MEDICAL), medical)
                .subAgents("the request is legal", scope -> confident(scope, RequestCategory.LEGAL), legal)
                .subAgents("the request is technical", scope -> confident(scope, RequestCategory.TECHNICAL), technical)
                .subAgents(
                        "no expert is likely enough",
                        scope -> !confident(scope, RequestCategory.MEDICAL)
                                && !confident(scope, RequestCategory.LEGAL)
                                && !confident(scope, RequestCategory.TECHNICAL),
                        human)
                .build();
        return AgenticServices.sequenceBuilder()
                .subAgents(router, experts)
                .outputKey("response")
                .listener(monitor)
                .build();
    }

    private static List<String> invokedExperts(AgentMonitor monitor) {
        AgentInvocation conditional = monitor.successfulExecutions()
                .get(0)
                .topLevelInvocations()
                .nestedInvocations()
                .get(1);
        return conditional.nestedInvocations().stream()
                .filter(invocation -> !invocation.skipped())
                .map(invocation -> invocation.agent().type().getSimpleName())
                .toList();
    }

    private static AgentInvocation decisionInvocation(AgentMonitor monitor) {
        return monitor.successfulExecutions()
                .get(0)
                .topLevelInvocations()
                .nestedInvocations()
                .get(0);
    }

    private static void assertRoutedTo(String request, RequestCategory category, String expert) {
        AgentMonitor monitor = new AgentMonitor();
        ResultWithAgenticScope<String> result =
                routingSystem(monitor).invokeWithAgenticScope(Map.of("request", request));

        Choice<RequestCategory> choice =
                (Choice<RequestCategory>) result.agenticScope().readState("category");
        System.out.println(request + " -> " + choice);
        assertThat(choice.value()).isEqualTo(category);
        assertThat(choice.probabilityOf(category)).isGreaterThanOrEqualTo(0.5);
        assertThat(invokedExperts(monitor)).containsExactly(expert);
        assertThat(result.result()).isNotBlank();

        AgentInvocation decision = decisionInvocation(monitor);
        assertThat(decision.decisionResponse()).isPresent();
        assertThat(decision.totalTokenCount()).isPositive();
        assertThat(decision.modelName()).isPresent();
    }

    @Test
    void routes_a_legal_request_to_the_legal_expert() {
        assertRoutedTo(LEGAL_REQUEST, RequestCategory.LEGAL, "LegalExpert");
    }

    @Test
    void routes_a_medical_request_to_the_medical_expert() {
        assertRoutedTo(MEDICAL_REQUEST, RequestCategory.MEDICAL, "MedicalExpert");
    }

    @Test
    void routes_a_technical_request_to_the_technical_expert() {
        assertRoutedTo(TECHNICAL_REQUEST, RequestCategory.TECHNICAL, "TechnicalExpert");
    }

    @Test
    void sends_an_off_topic_request_to_a_human() {
        AgentMonitor monitor = new AgentMonitor();
        ResultWithAgenticScope<String> result =
                routingSystem(monitor).invokeWithAgenticScope(Map.of("request", OFF_TOPIC_REQUEST));

        Choice<RequestCategory> choice =
                (Choice<RequestCategory>) result.agenticScope().readState("category");
        System.out.println(OFF_TOPIC_REQUEST + " -> " + choice);
        assertThat(result.result()).isEqualTo("A human will answer: " + OFF_TOPIC_REQUEST);
        assertThat(invokedExperts(monitor)).containsExactly("AgenticScopeFunction");
    }

    // Declarative variant

    public interface DeclarativeRouter {

        @Decide("Which kind of expert should handle this request?")
        @Agent(value = "Categorizes a user request", outputKey = "category")
        RequestCategory classify(@V("request") String request);

        @DecisionModelSupplier
        static DecisionModel decisionModel() {
            return typeSafe();
        }
    }

    public static class HumanFallback {

        @Agent(value = "Forwards the request to a human", outputKey = "response")
        public static String forward(@V("request") String request) {
            return "A human will answer: " + request;
        }
    }

    public interface DeclarativeExperts {

        @ConditionalAgent(
                outputKey = "response",
                subAgents = {MedicalExpert.class, LegalExpert.class, HumanFallback.class})
        String ask(@V("category") RequestCategory category, @V("request") String request);

        @ActivationCondition(value = MedicalExpert.class, description = "the request is medical")
        static boolean medical(@V("category") RequestCategory category) {
            return category == RequestCategory.MEDICAL;
        }

        @ActivationCondition(value = LegalExpert.class, description = "the request is legal")
        static boolean legal(@V("category") RequestCategory category) {
            return category == RequestCategory.LEGAL;
        }

        @ActivationCondition(value = HumanFallback.class, description = "the request is neither medical nor legal")
        static boolean other(@V("category") RequestCategory category) {
            return category != RequestCategory.MEDICAL && category != RequestCategory.LEGAL;
        }
    }

    public interface DeclarativeRouting {

        @SequenceAgent(
                outputKey = "response",
                subAgents = {DeclarativeRouter.class, DeclarativeExperts.class})
        String ask(@V("request") String request);
    }

    @Test
    void routes_declaratively() {
        DeclarativeRouting routing = AgenticServices.createAgenticSystem(DeclarativeRouting.class, baseModel());

        assertThat(routing.ask(MEDICAL_REQUEST)).isNotBlank().doesNotStartWith("A human will answer");
        assertThat(routing.ask(OFF_TOPIC_REQUEST)).isEqualTo("A human will answer: " + OFF_TOPIC_REQUEST);
    }
}
