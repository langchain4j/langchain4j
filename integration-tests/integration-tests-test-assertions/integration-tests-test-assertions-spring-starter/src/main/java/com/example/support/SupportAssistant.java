package com.example.support;

import static dev.langchain4j.service.spring.AiServiceWiringMode.EXPLICIT;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;

@AiService(
        wiringMode = EXPLICIT,
        chatModel = "chatModel",
        tools = "bookingTools",
        contentRetriever = "policyRetriever",
        chatMemoryProvider = "chatMemoryProvider")
public interface SupportAssistant {

    @SystemMessage("You are a customer support agent of the car rental company Miles of Smiles.")
    String chat(@MemoryId String customerId, @UserMessage String message);
}
