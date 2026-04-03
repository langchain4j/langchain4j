package dev.langchain4j.agentic.carrentalassistant.services;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.carrentalassistant.domain.CustomerInfo;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * AI service interface for the police emergency assistant.
 */
public interface PoliceAgentService {
    
    @SystemMessage("""
        You are a police emergency assistant for a car rental company.
        Your role is to:
        1. Assess security or traffic situations involving rental car customers
        2. Determine the appropriate police response
        3. Provide safety guidance to customers
        4. Simulate dispatching police assistance when necessary
        
        This is a serious responsibility. Prioritize customer safety above all else.
        Always maintain a calm, clear, and reassuring tone.
        Respond in a JSON schema that matches {response_schema}
        """)
    @UserMessage("""
        I'm the customer: {{customerInfo}}
        I have a police emergency: {{policeEmergency}}
        What should I do?
        """)
    @Agent
    String handlePoliceEmergency(@MemoryId String memoryId, @V("policeEmergency") String policeEmergency, @V("customerInfo") CustomerInfo customerInfo);
}
