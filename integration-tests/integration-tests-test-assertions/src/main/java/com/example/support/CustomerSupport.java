package com.example.support;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;

/**
 * Creates the AI Services of the customer support application.
 */
public final class CustomerSupport {

    private CustomerSupport() {}

    public static PolicyExpert policyExpert(ChatModel chatModel) {
        return AiServices.builder(PolicyExpert.class)
                .chatModel(chatModel)
                .contentRetriever(new PolicyRetriever())
                .build();
    }

    public static CustomerSupportAgent customerSupportAgent(
            ChatModel chatModel, BookingRepository bookingRepository, PolicyExpert policyExpert) {
        return AiServices.builder(CustomerSupportAgent.class)
                .chatModel(chatModel)
                .chatMemoryProvider(customerId -> MessageWindowChatMemory.withMaxMessages(20))
                .tools(new BookingTools(bookingRepository), new PolicyExpertTool(policyExpert))
                .inputGuardrails(new NoCardNumbersGuardrail())
                .outputGuardrails(new ProfessionalToneGuardrail())
                .build();
    }

    public static StreamingCustomerSupportAgent streamingCustomerSupportAgent(
            StreamingChatModel streamingChatModel, BookingRepository bookingRepository) {
        return AiServices.builder(StreamingCustomerSupportAgent.class)
                .streamingChatModel(streamingChatModel)
                .tools(new BookingTools(bookingRepository))
                .build();
    }

    public static AsyncCustomerSupportAgent asyncCustomerSupportAgent(
            ChatModel chatModel, BookingRepository bookingRepository) {
        return AiServices.builder(AsyncCustomerSupportAgent.class)
                .chatModel(chatModel)
                .tools(new BookingTools(bookingRepository))
                .build();
    }

    public static BookingRequestExtractor bookingRequestExtractor(ChatModel chatModel) {
        return AiServices.builder(BookingRequestExtractor.class)
                .chatModel(chatModel)
                .build();
    }
}
