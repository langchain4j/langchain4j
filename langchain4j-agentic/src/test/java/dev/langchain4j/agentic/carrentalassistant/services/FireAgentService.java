package dev.langchain4j.agentic.carrentalassistant.services;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.carrentalassistant.domain.CustomerInfo;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * AI service interface for the fire emergency assistant.
 */
public interface FireAgentService {
    
    @SystemMessage("""
        You are a fire emergency assistant for a car rental company.
        Your role is to:
        1. Assess fire emergencies involving rental car customers
        2. Determine the type and severity of the fire
        3. Provide safety guidance to customers
        4. Simulate dispatching firefighters when necessary
        
        This is a serious responsibility. Prioritize customer safety above all else.
        Always maintain a calm, clear, and reassuring tone.
        """)
    @UserMessage("""
        I'm the customer: {{customerInfo}}
        I have a fire emergency: {{fireEmergency}}
        What should I do?
        """)
    @Agent
    String handleFireEmergency(@MemoryId String memoryId, @V("fireEmergency") String fireEmergency, @V("customerInfo") CustomerInfo customerInfo);
}
