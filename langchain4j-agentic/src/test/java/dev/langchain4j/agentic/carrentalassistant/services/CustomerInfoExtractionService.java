package dev.langchain4j.agentic.carrentalassistant.services;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.carrentalassistant.domain.CustomerInfo;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * AI service interface for extracting customer information from messages.
 */
public interface CustomerInfoExtractionService {
    
    @SystemMessage("""
        You are a customer information extraction assistant for a car rental company.
        Your role is to analyze the history of customer messages and extract relevant information.
        
        Extract the following information when present:
        - Customer name
        - Customer ID
        - Booking reference number
        - Car make (brand)
        - Car model
        - Car year
        - Current location
        
        Only extract information that is explicitly mentioned in the message.
        Do not make assumptions or infer information that isn't clearly stated.
        If a piece of information is not present, leave that field null.
        """)
    @UserMessage("""
        Extract customer information from this message:
        {{message}}
        and update the existing customer information:
        {{customerInfo}}
        """)
    @Agent("Extract customer information from user message")
    CustomerInfo extractCustomerInfo(@MemoryId String memoryId, @V("message") String message, @V("customerInfo") CustomerInfo customerInfo);
}
