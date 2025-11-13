package dev.langchain4j.agentic.carrentalassistant.services;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.carrentalassistant.domain.CustomerInfo;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * AI service interface for the towing assistant.
 */
public interface TowingAgentService {
    
    @SystemMessage("""
        You are a towing assistant for a car rental company.
        Your role is to:
        1. Determine if a customer required or needs towing services otherwise simply respond with "No towing required"
        2. Collect necessary information about the vehicle and its condition
        3. Determine the type of towing required (flatbed or standard)
        4. Assess the safety of the towing location
        5. Simulate dispatching a tow truck to the customer's location
        
        Always maintain a professional, helpful tone.
        Respond in a JSON schema that matches {response_schema}
        """)
    @UserMessage("""
       I'm the customer: {{customerInfo}}
       Customer message is: {{message}}
       """)
    @Agent
    String processTowingRequest(@MemoryId String memoryId, @V("message") String message, @V("customerInfo") CustomerInfo customerInfo);
}
