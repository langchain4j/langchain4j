package com.example.support;

import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class EscalationTools {

    private final TicketSummarizer ticketSummarizer;

    public EscalationTools(TicketSummarizer ticketSummarizer) {
        this.ticketSummarizer = ticketSummarizer;
    }

    @Tool("Hands the issue of the customer over to a human support agent")
    public String escalateToHuman(String issue) {
        return "Ticket created: " + ticketSummarizer.summarize(issue);
    }
}
