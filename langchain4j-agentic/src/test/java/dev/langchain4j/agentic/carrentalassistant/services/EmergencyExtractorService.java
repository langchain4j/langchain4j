package dev.langchain4j.agentic.carrentalassistant.services;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.carrentalassistant.domain.CustomerInfo;
import dev.langchain4j.agentic.carrentalassistant.domain.Emergencies;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * AI service interface for the fire emergency assistant.
 */
public interface EmergencyExtractorService {
    
    @SystemMessage("""
        You are an emergencies handler.
        Your role is to:
        1. Analyze customer messages to identify emergencies
        2. Determine the type of emergency among police, medical and fire. There could be multiple emergencies in one message.
        3. Extract relevant emergency information and put them into the corresponding field in the Emergencies object.
        4. If no emergency is detected for a specific emergency type leave the corresponding field blank.
        """)
    @UserMessage("""
        I'm the customer: {{customerInfo}}
        My message is: {{message}}
        """)
    @Agent
    Emergencies extractEmergencies(@V("message") String message, @V("customerInfo") CustomerInfo customerInfo);
}
