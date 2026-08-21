package dev.langchain4j.agentic.patterns.debate.codereview;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

import static dev.langchain4j.agentic.patterns.debate.DebatePlanner.DEBATE_CONTEXT_KEY;

public class CodeReviewAgents {

    public interface BugHunter {

        @UserMessage("""
                You are a code reviewer focused on correctness and bug detection. \
                Analyze the following Java code snippet for logical errors, off-by-one mistakes, null safety issues, \
                race conditions, and any other bugs.
                If previous debate context is provided, consider the other reviewers' findings and refine your analysis: \
                confirm valid findings, dispute false positives, and add anything missed.
                Keep your response to 3-4 sentences. End with a one-word verdict: APPROVE or REJECT.
                Code: {{code}}
                Previous debate context: {{debateContext}}
                """)
        @Agent(description = "Reviews code for correctness and bugs", name = "BugHunter")
        String review(@V("code") String code, @V(DEBATE_CONTEXT_KEY) String debateContext);
    }

    public interface SecurityReviewer {

        @UserMessage("""
                You are a code reviewer focused on security vulnerabilities. \
                Analyze the following Java code snippet for injection flaws, unsafe deserialization, \
                information leakage, improper input validation, and other OWASP top 10 risks.
                If previous debate context is provided, consider the other reviewers' findings and refine your analysis: \
                confirm valid findings, dispute false positives, and add anything missed.
                Keep your response to 3-4 sentences. End with a one-word verdict: APPROVE or REJECT.
                Code: {{code}}
                Previous debate context: {{debateContext}}
                """)
        @Agent(description = "Reviews code for security vulnerabilities", name = "SecurityReviewer")
        String review(@V("code") String code, @V(DEBATE_CONTEXT_KEY) String debateContext);
    }

    public interface DesignCritic {

        @UserMessage("""
                You are a code reviewer focused on design quality and maintainability. \
                Analyze the following Java code snippet for SOLID violations, poor abstractions, \
                code smells, readability issues, and missing error handling.
                If previous debate context is provided, consider the other reviewers' findings and refine your analysis: \
                confirm valid findings, dispute false positives, and add anything missed.
                Keep your response to 3-4 sentences. End with a one-word verdict: APPROVE or REJECT.
                Code: {{code}}
                Previous debate context: {{debateContext}}
                """)
        @Agent(description = "Reviews code for design quality and maintainability", name = "DesignCritic")
        String review(@V("code") String code, @V(DEBATE_CONTEXT_KEY) String debateContext);
    }

    public interface ReviewSummarizer {

        @UserMessage("""
                You are a senior engineer summarizing a code review. \
                Multiple reviewers have analyzed a code snippet from different angles: bugs, security, and design. \
                Synthesize their findings into a final review summary. List the confirmed issues, \
                note any disagreements that were resolved, and give a final recommendation.
                Keep your response to 4-5 sentences.
                Debate context: {{debateContext}}
                """)
        @Agent(description = "Summarizes the code review debate into a final verdict", name = "Summarizer")
        String summarize(@V(DEBATE_CONTEXT_KEY) String debateContext);
    }

    public interface CodeReviewPanel {

        @Agent
        String review(@V("code") String code);
    }
}
