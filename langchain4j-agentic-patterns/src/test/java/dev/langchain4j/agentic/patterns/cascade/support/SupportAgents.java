package dev.langchain4j.agentic.patterns.cascade.support;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.typesafe.TypeSafeDecisionModel;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import java.util.List;
import java.util.stream.Stream;

public class SupportAgents {

    public static final String PRODUCT_REQUEST = "How do I descale my Brewmaster 3000 coffee machine?";
    public static final String UNRELATED_REQUEST = "How do I set up a home network with two routers?";

    public static final double ACCEPTANCE_THRESHOLD = 0.5;
    public static final String ACCEPTANCE_CRITERION = "The answer gives concrete instructions that solve the user's "
            + "problem, instead of saying that it cannot help or redirecting the user elsewhere";

    public static List<String> invokedTiers(AgenticScope agenticScope) {
        return Stream.of("faq", "expert")
                .filter(tier -> !agenticScope.agentInvocations(tier).isEmpty())
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

    public interface FaqAgent {

        @UserMessage("""
                You are the FAQ assistant of the Brewmaster 3000 coffee machine.
                Answer the following user request in no more than 3 sentences, using only your knowledge of the
                Brewmaster 3000 coffee machine: how to set it up, brew coffee, clean and descale it, and fix its
                common issues.
                If the request is not about the Brewmaster 3000 coffee machine, answer exactly:
                "I can only help with the Brewmaster 3000 coffee machine."
                The user request is {{request}}.
                """)
        @Agent(
                description = "Answers frequently asked questions about the Brewmaster 3000 coffee machine",
                outputKey = "faqAnswer")
        String faq(@V("request") String request);
    }

    public interface ExpertAgent {

        @UserMessage("""
                You are an expert assistant able to answer any question.
                Provide the best possible answer to the following user request in no more than 3 sentences.
                The user request is {{request}}.
                """)
        @Agent(description = "An expert assistant answering any question", outputKey = "expertAnswer")
        String expert(@V("request") String request);
    }

    public interface AnswerSummarizer {

        @UserMessage("""
                Summarize the following answer in a single sentence that a non-expert can understand.
                The answer is: {{answer}}
                """)
        @Agent(description = "Summarizes an answer", outputKey = "summary")
        String summarize(@V("answer") String answer);
    }

    public interface SupportCascade {

        @Agent
        String ask(@V("request") String request);
    }

    public interface SupportCascadeWithScope {

        @Agent
        ResultWithAgenticScope<String> ask(@V("request") String request);
    }

    public interface SupportPipeline {

        @Agent
        ResultWithAgenticScope<String> process(@V("request") String request);
    }
}
