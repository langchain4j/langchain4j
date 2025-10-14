package dev.langchain4j.agentic.carrentalassistant;

import static dev.langchain4j.agentic.Models.baseModel;

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
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;

public class AssistantMain {

    public static void main(String[] args) {
        CarRentalAssistant assistant = createAssistant();
        String memoryId = "1";
        AgenticScope agenticScope = null;

        while (true) {
            String userMessage = System.console().readLine("You: ");
            if (userMessage == null || userMessage.equalsIgnoreCase("exit")) {
                break;
            }

            ResultWithAgenticScope<String> response = assistant.chat(memoryId, userMessage);
            agenticScope = response.agenticScope();
            System.out.println("Assistant: " + response.result());
        }

        System.out.println(agenticScope.readState("customerInfo"));
    }

    private static CarRentalAssistant createAssistant() {
        CustomerInfoExtractionService customerInfoExtraction = AgenticServices.agentBuilder(
                        CustomerInfoExtractionService.class)
                .chatModel(baseModel())
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10))
                .outputKey("customerInfo")
                .build();

        TowingAgentService towingAgentService = AgenticServices.agentBuilder(TowingAgentService.class)
                .chatModel(baseModel())
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10))
                .outputKey("towingResponse")
                .build();

        ResponseGeneratorService responseGeneratorService = AgenticServices.agentBuilder(ResponseGeneratorService.class)
                .chatModel(baseModel())
                .outputKey("response")
                .build();

        return AgenticServices.sequenceBuilder(CarRentalAssistant.class)
                .beforeCall(agenticScope -> {
                    if (agenticScope.readState("customerInfo") == null) {
                        agenticScope.writeState("customerInfo", new CustomerInfo());
                    }
                })
                .subAgents(customerInfoExtraction, towingAgentService, emergencyService(), responseGeneratorService)
                .outputKey("response")
                .build();
    }

    private static UntypedAgent emergencyService() {
        EmergencyExtractorService emergencyExtractor = AgenticServices.agentBuilder(EmergencyExtractorService.class)
                .chatModel(baseModel())
                .outputKey("emergencies")
                .build();

        EmergencyResponseService emergencyResponseService = AgenticServices.agentBuilder(EmergencyResponseService.class)
                .chatModel(baseModel())
                .outputKey("emergencyResponse")
                .build();

        FireAgentService fireAgent = AgenticServices.agentBuilder(FireAgentService.class)
                .chatModel(baseModel())
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10))
                .outputKey("fireResponse")
                .build();
        MedicalAgentService medicalAgent = AgenticServices.agentBuilder(MedicalAgentService.class)
                .chatModel(baseModel())
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10))
                .outputKey("medicalResponse")
                .build();
        PoliceAgentService policeAgent = AgenticServices.agentBuilder(PoliceAgentService.class)
                .chatModel(baseModel())
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10))
                .outputKey("policeResponse")
                .build();

        UntypedAgent emergencyExperts = AgenticServices.conditionalBuilder()
                .beforeCall(agenticScope -> {
                    Emergencies emergencies = (Emergencies) agenticScope.readState("emergencies");
                    writeEmergency(agenticScope, emergencies.getFire(), "fire");
                    writeEmergency(agenticScope, emergencies.getMedical(), "medical");
                    writeEmergency(agenticScope, emergencies.getPolice(), "police");
                })
                .subAgents(agenticScope -> agenticScope.hasState("fireEmergency"), fireAgent)
                .subAgents(agenticScope -> agenticScope.hasState("medicalEmergency"), medicalAgent)
                .subAgents(agenticScope -> agenticScope.hasState("policeEmergency"), policeAgent)
                .build();

        return AgenticServices.sequenceBuilder()
                .subAgents(emergencyExtractor, emergencyExperts, emergencyResponseService)
                .outputKey("emergencyResponse")
                .build();
    }

    private static void writeEmergency(AgenticScope agenticScope, String emergency, String type) {
        if (emergency == null || emergency.isBlank()) {
            agenticScope.writeState(type + "Emergency", null);
            agenticScope.writeState(type + "Response", "");
        } else {
            agenticScope.writeState(type + "Emergency", emergency);
        }
    }
}
