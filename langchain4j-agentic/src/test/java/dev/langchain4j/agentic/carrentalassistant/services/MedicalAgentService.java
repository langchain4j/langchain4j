package dev.langchain4j.agentic.carrentalassistant.services;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.carrentalassistant.domain.CustomerInfo;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * AI service interface for the medical emergency assistant.
 */
public interface MedicalAgentService {
    
    @SystemMessage("""
        You are a medical emergency assistant for a car rental company.
        Your role is to:
        1. Assess medical emergencies involving rental car customers
        2. Determine the appropriate medical response
        3. Provide first aid guidance when appropriate
        4. Simulate dispatching medical assistance when necessary
        
        This is a serious responsibility. Prioritize customer safety above all else.
        Always maintain a calm, clear, and reassuring tone.
        Respond in a JSON schema that matches {response_schema}
        """)
    @UserMessage("""
        I'm the customer: {{customerInfo}}
        I have a medical emergency: {{medicalEmergency}}
        What should I do?
        """)
    @Agent
    String handleMedicalEmergency(@MemoryId String memoryId, @V("medicalEmergency") String medicalEmergency, @V("customerInfo") CustomerInfo customerInfo);
}
