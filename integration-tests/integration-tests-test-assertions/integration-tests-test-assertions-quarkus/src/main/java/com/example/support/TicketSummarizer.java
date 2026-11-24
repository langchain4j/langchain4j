package com.example.support;

import dev.langchain4j.service.SystemMessage;

public interface TicketSummarizer {

    String SYSTEM_MESSAGE = "Summarize the issue of the customer in one sentence for a human support agent.";

    @SystemMessage(SYSTEM_MESSAGE)
    String summarize(String issue);
}
