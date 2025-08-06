package dev.langchain4j.agentic.carrentalassistant.services;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.V;

public interface CarRentalAssistant {

    @Agent
    ResultWithAgenticScope<String> chat(@MemoryId String memoryId, @V("message") String message);
}
