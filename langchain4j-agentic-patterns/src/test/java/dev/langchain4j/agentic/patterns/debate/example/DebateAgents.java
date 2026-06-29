package dev.langchain4j.agentic.patterns.debate.example;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.observability.MonitoredAgent;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

import static dev.langchain4j.agentic.patterns.debate.DebatePlanner.DEBATE_CONTEXT_KEY;

public class DebateAgents {

    public interface UtilitarianDebater {

        @UserMessage("""
                You are a utilitarian ethics debater. \
                Consider the following question and argue from a utilitarian perspective, maximizing overall well-being.
                If previous debate context is provided, consider the other debaters' arguments and refine your position.
                Keep your response to 2-3 sentences. End with a one-word verdict: AGREE or DISAGREE.
                Question: {{question}}
                Previous debate context: {{debateContext}}
                """)
        @Agent(description = "Argues from a utilitarian ethics perspective", name = "Utilitarian")
        String debate(@V("question") String question, @V(DEBATE_CONTEXT_KEY) String debateContext);
    }

    public interface DeontologicalDebater {

        @UserMessage("""
                You are a deontological ethics debater. \
                Consider the following question and argue based on moral rules, duties, and rights.
                If previous debate context is provided, consider the other debaters' arguments and refine your position.
                Keep your response to 2-3 sentences. End with a one-word verdict: AGREE or DISAGREE.
                Question: {{question}}
                Previous debate context: {{debateContext}}
                """)
        @Agent(description = "Argues from a deontological ethics perspective", name = "Deontologist")
        String debate(@V("question") String question, @V(DEBATE_CONTEXT_KEY) String debateContext);
    }

    public interface PragmatistDebater {

        @UserMessage("""
                You are a pragmatist debater. \
                Consider the following question and argue based on practical consequences and real-world outcomes.
                If previous debate context is provided, consider the other debaters' arguments and refine your position.
                Keep your response to 2-3 sentences. End with a one-word verdict: AGREE or DISAGREE.
                Question: {{question}}
                Previous debate context: {{debateContext}}
                """)
        @Agent(description = "Argues from a pragmatist perspective", name = "Pragmatist")
        String debate(@V("question") String question, @V(DEBATE_CONTEXT_KEY) String debateContext);
    }

    public interface EthicsJudge {

        @UserMessage("""
                You are an impartial ethics judge. \
                Review the debate context where multiple debaters have argued about a question from different perspectives.
                Synthesize their arguments and provide a balanced, well-reasoned final verdict in 3-4 sentences.
                Debate context: {{debateContext}}
                """)
        @Agent(description = "Renders a final verdict by synthesizing debate arguments", name = "Judge")
        String judge(@V("debateContext") String debateContext);
    }

    public interface EthicsPanel extends MonitoredAgent {

        @Agent
        String debate(@V("question") String question);
    }
}
