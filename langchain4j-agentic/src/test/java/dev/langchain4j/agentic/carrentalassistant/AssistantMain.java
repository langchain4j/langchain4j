package dev.langchain4j.agentic.carrentalassistant;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgentServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.carrentalassistant.domain.CustomerInfo;
import dev.langchain4j.agentic.carrentalassistant.domain.Emergencies;
import dev.langchain4j.agentic.carrentalassistant.services.CarRentalAssistant;
import dev.langchain4j.agentic.carrentalassistant.services.CustomerInfoExtractionService;
import dev.langchain4j.agentic.carrentalassistant.services.EmergencyExtractorService;
import dev.langchain4j.agentic.carrentalassistant.services.EmergencyResponseService;
import dev.langchain4j.agentic.carrentalassistant.services.FireAgentService;
import dev.langchain4j.agentic.carrentalassistant.services.MedicalAgentService;
import dev.langchain4j.agentic.carrentalassistant.services.PoliceAgentService;
import dev.langchain4j.agentic.carrentalassistant.services.ResponseGeneratorService;
import dev.langchain4j.agentic.carrentalassistant.services.TowingAgentService;
import dev.langchain4j.agentic.cognisphere.Cognisphere;
import dev.langchain4j.agentic.cognisphere.ResultWithCognisphere;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.service.V;

import java.util.function.Function;

import static dev.langchain4j.agentic.Models.BASE_MODEL;

public class AssistantMain {

    public static void main(String[] args) {
        CarRentalAssistant assistant = createAssistant();
        String memoryId = "1";
        Cognisphere cognisphere = null;

        while (true) {
            String userMessage = System.console().readLine("You: ");
            if (userMessage == null || userMessage.equalsIgnoreCase("exit")) {
                break;
            }

            ResultWithCognisphere<String> response = assistant.chat(memoryId, userMessage);
            cognisphere = response.cognisphere();
            System.out.println("Assistant: " + response.result());
        }

        System.out.println(cognisphere.readState("customerInfo"));
    }

    private static CarRentalAssistant createAssistant() {
        CustomerInfoExtractionService customerInfoExtraction = AgentServices.agentBuilder(CustomerInfoExtractionService.class)
                .chatModel(BASE_MODEL)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10))
                .outputName("customerInfo")
                .build();

        TowingAgentService towingAgentService = AgentServices.agentBuilder(TowingAgentService.class)
                .chatModel(BASE_MODEL)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10))
                .outputName("towingResponse")
                .build();

        ResponseGeneratorService responseGeneratorService = AgentServices.agentBuilder(ResponseGeneratorService.class)
                .chatModel(BASE_MODEL)
                .outputName("response")
                .build();

        return AgentServices.sequenceBuilder(CarRentalAssistant.class)
                .subAgents(new InitCognisphere(), customerInfoExtraction, towingAgentService, emergencyService(), responseGeneratorService)
                .outputName("response")
                .build();
    }

    private static UntypedAgent emergencyService() {
        EmergencyExtractorService emergencyExtractor = AgentServices.agentBuilder(EmergencyExtractorService.class)
                .chatModel(BASE_MODEL)
                .outputName("emergencies")
                .build();

        EmergencyResponseService emergencyResponseService = AgentServices.agentBuilder(EmergencyResponseService.class)
                .chatModel(BASE_MODEL)
                .outputName("emergencyResponse")
                .build();

        FireAgentService fireAgent = AgentServices.agentBuilder(FireAgentService.class)
                .chatModel(BASE_MODEL)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10))
                .outputName("fireResponse")
                .build();
        MedicalAgentService medicalAgent = AgentServices.agentBuilder(MedicalAgentService.class)
                .chatModel(BASE_MODEL)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10))
                .outputName("medicalResponse")
                .build();
        PoliceAgentService policeAgent = AgentServices.agentBuilder(PoliceAgentService.class)
                .chatModel(BASE_MODEL)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10))
                .outputName("policeResponse")
                .build();

        UntypedAgent emergencyExperts = AgentServices.conditionalBuilder()
                .subAgents( cognisphere -> hasEmergency(cognisphere, "fire", Emergencies::getFire), fireAgent)
                .subAgents( cognisphere -> hasEmergency(cognisphere, "medical", Emergencies::getMedical), medicalAgent)
                .subAgents( cognisphere -> hasEmergency(cognisphere, "police", Emergencies::getPolice), policeAgent)
                .build();

        return AgentServices.sequenceBuilder()
                .subAgents(emergencyExtractor, emergencyExperts, emergencyResponseService)
                .outputName("emergencyResponse")
                .build();
    }

    private static boolean hasEmergency(Cognisphere cognisphere, String emergencyName, Function<Emergencies, String> extractor) {
        String emergency = extractor.apply((Emergencies) cognisphere.readState("emergencies"));
        cognisphere.writeState(emergencyName + "Emergency", emergency);
        boolean hasEmergency = emergency != null && !emergency.isBlank();
        if (!hasEmergency) {
            cognisphere.writeState(emergencyName + "Response", "");
        }
        return hasEmergency;
    }

    public static class InitCognisphere {
        @Agent
        public static Cognisphere initCognisphere(Cognisphere cognisphere) {
            if (cognisphere.readState("customerInfo") == null) {
                cognisphere.writeState("customerInfo", new CustomerInfo());
            }
            return cognisphere;
        }
    }
}
