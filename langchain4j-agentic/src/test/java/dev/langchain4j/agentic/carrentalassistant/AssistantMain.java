package dev.langchain4j.agentic.carrentalassistant;

import dev.langchain4j.agentic.AgenticServices;
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

import static dev.langchain4j.agentic.Models.baseModel;

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
        CustomerInfoExtractionService customerInfoExtraction = AgenticServices.agentBuilder(CustomerInfoExtractionService.class)
                .chatModel(baseModel())
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10))
                .outputName("customerInfo")
                .build();

        TowingAgentService towingAgentService = AgenticServices.agentBuilder(TowingAgentService.class)
                .chatModel(baseModel())
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10))
                .outputName("towingResponse")
                .build();

        ResponseGeneratorService responseGeneratorService = AgenticServices.agentBuilder(ResponseGeneratorService.class)
                .chatModel(baseModel())
                .outputName("response")
                .build();

        return AgenticServices.sequenceBuilder(CarRentalAssistant.class)
                .beforeCall(cognisphere -> {
                    if (cognisphere.readState("customerInfo") == null) {
                        cognisphere.writeState("customerInfo", new CustomerInfo());
                    }
                })
                .subAgents(customerInfoExtraction, towingAgentService, emergencyService(), responseGeneratorService)
                .outputName("response")
                .build();
    }

    private static UntypedAgent emergencyService() {
        EmergencyExtractorService emergencyExtractor = AgenticServices.agentBuilder(EmergencyExtractorService.class)
                .chatModel(baseModel())
                .outputName("emergencies")
                .build();

        EmergencyResponseService emergencyResponseService = AgenticServices.agentBuilder(EmergencyResponseService.class)
                .chatModel(baseModel())
                .outputName("emergencyResponse")
                .build();

        FireAgentService fireAgent = AgenticServices.agentBuilder(FireAgentService.class)
                .chatModel(baseModel())
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10))
                .outputName("fireResponse")
                .build();
        MedicalAgentService medicalAgent = AgenticServices.agentBuilder(MedicalAgentService.class)
                .chatModel(baseModel())
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10))
                .outputName("medicalResponse")
                .build();
        PoliceAgentService policeAgent = AgenticServices.agentBuilder(PoliceAgentService.class)
                .chatModel(baseModel())
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10))
                .outputName("policeResponse")
                .build();

        UntypedAgent emergencyExperts = AgenticServices.conditionalBuilder()
                .beforeCall(cognisphere -> {
                    Emergencies emergencies = (Emergencies) cognisphere.readState("emergencies");
                    writeEmergency(cognisphere, emergencies.getFire(), "fire");
                    writeEmergency(cognisphere, emergencies.getMedical(), "medical");
                    writeEmergency(cognisphere, emergencies.getPolice(), "police");
                })
                .subAgents( cognisphere -> cognisphere.hasState("fireEmergency"), fireAgent)
                .subAgents( cognisphere -> cognisphere.hasState("medicalEmergency"), medicalAgent)
                .subAgents( cognisphere -> cognisphere.hasState("policeEmergency"), policeAgent)
                .build();

        return AgenticServices.sequenceBuilder()
                .subAgents(emergencyExtractor, emergencyExperts, emergencyResponseService)
                .outputName("emergencyResponse")
                .build();
    }

    private static void writeEmergency(Cognisphere cognisphere, String emergency, String type) {
        if (emergency == null || emergency.isBlank()) {
            cognisphere.writeState(type + "Emergency", null);
            cognisphere.writeState(type + "Response", "");
        } else {
            cognisphere.writeState(type + "Emergency", emergency);
        }
    }
}
