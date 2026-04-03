package dev.langchain4j.agentic.carrentalassistant.services;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface EmergencyResponseService {

    @SystemMessage("""
        You are an agent that integrates responses from fire, medical, and police emergency services.
        Your role is to collect and integrate responses from different emergency services into a single coherent message.
        """)
    @UserMessage("""
        Fire emergency: {{fireResponse}}
        
        Medical emergency: {{medicalResponse}}
        
        Police emergency: {{policeResponse}}
        """)
    @Agent
    String summarizeEmergencies(@V("fireResponse") String fireResponse, @V("medicalResponse") String medicalResponse, @V("policeResponse") String policeResponse);
}
