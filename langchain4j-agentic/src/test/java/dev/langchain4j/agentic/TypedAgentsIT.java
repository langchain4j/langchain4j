package dev.langchain4j.agentic;

import dev.langchain4j.agentic.declarative.ActivationCondition;
import dev.langchain4j.agentic.declarative.AgentState;
import dev.langchain4j.agentic.declarative.ConditionalAgent;
import dev.langchain4j.agentic.declarative.K;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.AgenticScopeAccess;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import org.junit.jupiter.api.Test;

import static dev.langchain4j.agentic.AgenticServices.createAgenticSystem;
import static dev.langchain4j.agentic.Models.baseModel;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

public class TypedAgentsIT {

    public enum Category {
        LEGAL, MEDICAL, TECHNICAL, UNKNOWN
    }

    public static class RequestCategory implements AgentState<Category> {
        @Override
        public Category defaultValue() {
            return Category.UNKNOWN;
        }
    }

    public static class UserRequest implements AgentState<String> {
    }

    public static class ExpertResponse implements AgentState<String> {
    }

    public interface CategoryRouter {

        @UserMessage("""
                Analyze the following user request and categorize it as 'legal', 'medical' or 'technical'.
                In case the request doesn't belong to any of those categories categorize it as 'unknown'.
                Reply with only one of those words and nothing else.
                The user request is: '{{UserRequest}}'.
                """)
        @Agent(description = "Categorize a user request", typedOutputKey = RequestCategory.class)
        Category classify(@K(UserRequest.class) String request);
    }

    public interface MedicalExpert {

        @UserMessage("""
                You are a medical expert.
                Analyze the following user request under a medical point of view and provide the best possible answer.
                The user request is {{UserRequest}}.
                """)
        @Agent(description = "A medical expert", typedOutputKey = ExpertResponse.class)
        String medical(@K(UserRequest.class) String request);
    }

    public interface LegalExpert {

        @UserMessage("""
                You are a legal expert.
                Analyze the following user request under a legal point of view and provide the best possible answer.
                The user request is {{UserRequest}}.
                """)
        @Agent(description = "A legal expert", typedOutputKey = ExpertResponse.class)
        String legal(@K(UserRequest.class) String request);
    }

    public interface TechnicalExpert {

        @UserMessage("""
                You are a technical expert.
                Analyze the following user request under a technical point of view and provide the best possible answer.
                The user request is {{UserRequest}}.
                """)
        @Agent(description = "A technical expert", typedOutputKey = ExpertResponse.class)
        String technical(@K(UserRequest.class) String request);
    }

    public interface ExpertChatbot extends AgenticScopeAccess {

        @Agent
        String ask(@K(UserRequest.class) String request);
    }

    @Test
    void conditional_typed_agents_tests() {
        CategoryRouter routerAgent = AgenticServices.agentBuilder(CategoryRouter.class)
                .chatModel(baseModel())
                .build();

        MedicalExpert medicalExpert = spy(AgenticServices.agentBuilder(MedicalExpert.class)
                .chatModel(baseModel())
                .build());
        LegalExpert legalExpert = spy(AgenticServices.agentBuilder(LegalExpert.class)
                .chatModel(baseModel())
                .build());
        TechnicalExpert technicalExpert = spy(AgenticServices.agentBuilder(TechnicalExpert.class)
                .chatModel(baseModel())
                .build());

        UntypedAgent expertsAgent = AgenticServices.conditionalBuilder()
                .subAgents(
                        scope -> scope.readState(RequestCategory.class) == Category.MEDICAL,
                        medicalExpert)
                .subAgents(
                        scope -> scope.readState(RequestCategory.class) == Category.LEGAL,
                        legalExpert)
                .subAgents(
                        scope -> scope.readState(RequestCategory.class) == Category.TECHNICAL,
                        technicalExpert)
                .build();

        ExpertChatbot expertChatbot = AgenticServices.sequenceBuilder(ExpertChatbot.class)
                .subAgents(routerAgent, expertsAgent)
                .outputKey(ExpertResponse.class)
                .build();

        String response = expertChatbot.ask("I broke my leg what should I do");
        assertThat(response).contains("leg");

        verify(medicalExpert).medical("I broke my leg what should I do");
    }

    public interface ExpertsRouterAgent {

        @ConditionalAgent(typedOutputKey = ExpertResponse.class,
                subAgents = {MedicalExpert.class, TechnicalExpert.class, LegalExpert.class})
        String askExpert(@K(UserRequest.class) String request);

        @ActivationCondition(MedicalExpert.class)
        static boolean activateMedical(@K(RequestCategory.class) Category category) {
            return category == Category.MEDICAL;
        }

        @ActivationCondition(TechnicalExpert.class)
        static boolean activateTechnical(@K(RequestCategory.class) Category category) {
            return category == Category.TECHNICAL;
        }

        @ActivationCondition(LegalExpert.class)
        static boolean activateLegal(AgenticScope agenticScope) {
            return agenticScope.readState(RequestCategory.class) == Category.LEGAL;
        }
    }

    public interface DeclarativeExpertChatbot {

        @SequenceAgent( typedOutputKey = ExpertResponse.class,
                subAgents = { CategoryRouter.class, ExpertsRouterAgent.class })
        String ask(@K(UserRequest.class) String request);
    }

    @Test
    void declarative_conditional_typed_agents_tests() {
        DeclarativeExpertChatbot expertChatbot = createAgenticSystem(DeclarativeExpertChatbot.class, baseModel());

        String response = expertChatbot.ask("I broke my leg what should I do");
        assertThat(response).contains("leg");
    }
}
