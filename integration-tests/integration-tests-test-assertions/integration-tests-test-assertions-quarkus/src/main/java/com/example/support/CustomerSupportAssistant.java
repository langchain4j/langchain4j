package com.example.support;

import dev.langchain4j.service.SystemMessage;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

public interface CustomerSupportAssistant {

    String SYSTEM_MESSAGE = "You are the customer support assistant of an online shop.";

    @SystemMessage(SYSTEM_MESSAGE)
    String chat(String message);

    @SystemMessage(SYSTEM_MESSAGE)
    CompletableFuture<String> chatAsync(String message);

    @SystemMessage(SYSTEM_MESSAGE)
    Flow.Publisher<String> chatStream(String message);
}
