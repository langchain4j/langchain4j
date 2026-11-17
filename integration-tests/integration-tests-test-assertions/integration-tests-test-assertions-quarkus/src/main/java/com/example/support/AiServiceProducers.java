package com.example.support;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;

/**
 * Creates the AI Services of the application with {@link AiServices}. The models are CDI beans, provided by the
 * model provider of the application.
 */
public class AiServiceProducers {

    @Produces
    @ApplicationScoped
    CustomerSupportAssistant customerSupportAssistant(
            ChatModel chatModel,
            StreamingChatModel streamingChatModel,
            OrderTools orderTools,
            EscalationTools escalationTools,
            CustomerContext customerContext,
            PolicyRetriever policyRetriever,
            NoInternalNotesGuardrail noInternalNotesGuardrail) {
        return AiServices.builder(CustomerSupportAssistant.class)
                .chatModel(chatModel)
                .streamingChatModel(streamingChatModel)
                .tools(orderTools, escalationTools, customerContext)
                .contentRetriever(policyRetriever)
                .outputGuardrails(noInternalNotesGuardrail)
                .build();
    }

    @Produces
    @ApplicationScoped
    ProductAdvisor productAdvisor(StreamingChatModel streamingChatModel) {
        return AiServices.builder(ProductAdvisor.class)
                .streamingChatModel(streamingChatModel)
                .build();
    }

    @Produces
    @Dependent
    TicketSummarizer ticketSummarizer(ChatModel chatModel) {
        return AiServices.builder(TicketSummarizer.class).chatModel(chatModel).build();
    }

    @Produces
    @ApplicationScoped
    TicketTriager ticketTriager(ChatModel chatModel) {
        return AiServices.builder(TicketTriager.class).chatModel(chatModel).build();
    }
}
