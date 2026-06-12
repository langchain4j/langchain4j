package dev.langchain4j.agentic.patterns.htn.support;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.observability.MonitoredAgent;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public class SupportAgents {

    public interface ClassifyTicket {
        @UserMessage("""
                Classify the following customer support ticket into exactly one category.
                Reply with a single word: BILLING, TECHNICAL, or GENERAL.
                Ticket: {{ticket}}
                """)
        @Agent("Classify a support ticket into a category")
        String classify(@V("ticket") String ticket);
    }

    public interface LookupBillingInfo {
        @UserMessage("""
                You are a billing system agent. Based on the customer ticket below, \
                generate a plausible billing record summary (account status, last payment date, outstanding balance). \
                Return only the billing info.
                Ticket: {{ticket}}
                """)
        @Agent("Look up billing information for the customer")
        String lookupBilling(@V("ticket") String ticket);
    }

    public interface ResolveBillingIssue {
        @UserMessage("""
                You are a billing resolution specialist. Given the billing info and customer ticket, \
                determine the resolution (refund, adjustment, or explanation). \
                Return only the resolution in 2-3 sentences.
                Billing info: {{billingInfo}}
                Ticket: {{ticket}}
                """)
        @Agent("Resolve the billing issue")
        String resolveBilling(@V("billingInfo") String billingInfo, @V("ticket") String ticket);
    }

    public interface DiagnoseProblem {
        @UserMessage("""
                You are a technical support agent. Diagnose the technical problem described in the ticket. \
                Return a short diagnosis (2-3 sentences).
                Ticket: {{ticket}}
                """)
        @Agent("Diagnose the technical problem")
        String diagnose(@V("ticket") String ticket);
    }

    public interface SuggestFix {
        @UserMessage("""
                You are a technical support agent. Given the diagnosis, suggest a fix for the customer. \
                Return step-by-step instructions (3-5 steps).
                Diagnosis: {{diagnosis}}
                """)
        @Agent("Suggest a fix for the diagnosed problem")
        String suggestFix(@V("diagnosis") String diagnosis);
    }

    public interface SearchKnowledgeBase {
        @UserMessage("""
                You are a knowledge base agent. Find relevant information to answer the customer's general inquiry. \
                Return a concise answer (2-3 sentences).
                Ticket: {{ticket}}
                """)
        @Agent("Search the knowledge base for relevant information")
        String search(@V("ticket") String ticket);
    }

    public interface DraftResponse {
        @UserMessage("""
                You are a customer support agent. Draft a polite, professional response to the customer \
                incorporating all the information gathered so far.
                Ticket: {{ticket}}
                Category: {{category}}
                Resolution context: {{resolutionContext}}
                """)
        @Agent("Draft the final customer response")
        String draft(@V("ticket") String ticket, @V("category") String category,
                     @V("resolutionContext") String resolutionContext);
    }

    public interface SupportHandler extends MonitoredAgent {
        @Agent
        ResultWithAgenticScope<String> handle(@V("ticket") String ticket);
    }
}
