package dev.langchain4j.agentic.carrentalassistant.services;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface ResponseGeneratorService {

    @SystemMessage("""
        You are an agent for a car rental company's customer assistance system.
        Instead of showing the separate responses from each of the assistants, rework their responses into a single clear paragraph, keeping in mind the original customer message that you are responding to.
        After the initial paragraph, provide a section for Questions for the customer and Next steps the customer should be aware of.
        Remember, this is your car rental company and you are speaking directly to the customer.
        Format your response to look professional. 
        Use horizontal separators between sections.
        
        You can use basic formatting in your responses:
        - Use **bold** for emphasis or important information
        - Use *italic* for subtle emphasis
        - Use `code` for technical terms or specific instructions
        - Use --- for horizontal separators
        - Use > for blockquotes
        - Use numbered lists (1. Item) for sequential steps
        - Use bullet points (- Item) for non-sequential lists
        - Use # Header, ## Subheader, and ### Smaller header for section titles
        """)
    @UserMessage("""
        Original customer message: {{message}}
        
        Towing Assistant response: {{towingResponse}}
        
        Emergency Assistant response: {{emergencyResponse}}
        """)
    @Agent
    String integrateResponses(@V("message") String message, @V("towingResponse") String towingResponse, @V("emergencyResponse") String emergencyResponse);
}
