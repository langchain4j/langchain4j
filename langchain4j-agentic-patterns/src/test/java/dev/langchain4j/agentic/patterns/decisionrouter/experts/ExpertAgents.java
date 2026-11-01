package dev.langchain4j.agentic.patterns.decisionrouter.experts;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.typesafe.TypeSafeDecisionModel;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class ExpertAgents {

    public static final String MEDICAL_REQUEST = "I broke my leg, what should I do?";
    public static final String LEGAL_REQUEST =
            "My landlord refuses to give me back my security deposit, what are my rights?";
    public static final String TECHNICAL_REQUEST =
            "My laptop no longer connects to the wifi after the last operating system update, how can I fix it?";
    public static final String MEDICAL_AND_LEGAL_REQUEST = "I broke my leg in a car accident caused by another driver: "
            + "how should I take care of my leg, and can I sue the driver for damages?";
    public static final String UNRELATED_REQUEST = "What is a good recipe for a chocolate cake?";

    public static List<String> invokedExperts(AgenticScope agenticScope) {
        return Stream.of("medical", "legal", "technical")
                .filter(expert -> !agenticScope.agentInvocations(expert).isEmpty())
                .toList();
    }

    public static DecisionModel decisionModel() {
        return TypeSafeDecisionModel.builder()
                .apiKey(System.getenv("TYPESAFE_API_KEY"))
                .modelName("jev-1.13.0")
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    public interface MedicalExpert {

        @UserMessage("""
                You are a medical expert.
                Analyze the following user request under a medical point of view and provide the best possible answer
                in no more than 3 sentences.
                The user request is {{request}}.
                """)
        @Agent(
                description = "A medical expert, answering questions about health, injuries and treatments",
                outputKey = "medicalResponse")
        String medical(@V("request") String request);
    }

    public interface LegalExpert {

        @UserMessage("""
                You are a legal expert.
                Analyze the following user request under a legal point of view and provide the best possible answer
                in no more than 3 sentences.
                The user request is {{request}}.
                """)
        @Agent(
                description = "A legal expert, answering questions about laws, rights, contracts and lawsuits",
                outputKey = "legalResponse")
        String legal(@V("request") String request);
    }

    public interface TechnicalExpert {

        @UserMessage("""
                You are a technical expert.
                Analyze the following user request under a technical point of view and provide the best possible answer
                in no more than 3 sentences.
                The user request is {{request}}.
                """)
        @Agent(
                description = "A technical expert, answering questions about computers, software and devices",
                outputKey = "technicalResponse")
        String technical(@V("request") String request);
    }

    public interface ResponseSummarizer {

        @UserMessage("""
                Summarize the following answer in a single sentence that a non-expert can understand.
                The answer is: {{response}}
                """)
        @Agent(description = "Summarizes an expert answer", outputKey = "summary")
        String summarize(@V("response") String response);
    }

    public interface ResponseSynthesizer {

        @UserMessage("""
                Merge the answers that different experts gave to the same user request into a single answer
                of no more than 4 sentences.
                The user request is: {{request}}
                The answers of the experts, keyed by expert, are: {{responses}}
                """)
        @Agent(description = "Merges the answers of several experts into one", outputKey = "answer")
        String synthesize(@V("request") String request, @V("responses") Map<String, String> responses);
    }

    public interface ExpertRouter {

        @Agent
        String ask(@V("request") String request);
    }

    public interface MultiExpertRouter {

        @Agent(outputKey = "responses")
        Map<String, String> ask(@V("request") String request);
    }

    public interface ExpertRouterWithScope {

        @Agent
        ResultWithAgenticScope<String> ask(@V("request") String request);
    }

    public interface MultiExpertRouterWithScope {

        @Agent
        ResultWithAgenticScope<Map<String, String>> ask(@V("request") String request);
    }

    public interface ExpertPipeline {

        @Agent
        ResultWithAgenticScope<String> process(@V("request") String request);
    }
}
