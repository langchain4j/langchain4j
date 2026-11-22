package com.example.support;

import dev.langchain4j.service.SystemMessage;
import java.util.concurrent.CompletableFuture;

public interface TicketTriager {

    String SYSTEM_MESSAGE = "Decide the priority and the category of the issue of the customer.";

    @SystemMessage(SYSTEM_MESSAGE)
    TicketTriage triage(String issue);

    @SystemMessage(SYSTEM_MESSAGE)
    CompletableFuture<TicketTriage> triageAsync(String issue);
}
