package com.example.support.assistant;

import com.example.support.booking.BookingTools;
import com.example.support.expert.ExpertTools;
import com.example.support.guardrail.NoInternalNotesGuardrail;
import com.example.support.refund.RefundTools;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.service.AiServices;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AssistantConfiguration {

    @Bean
    ExpertAssistant expertAssistant(ChatModel chatModel) {
        return AiServices.builder(ExpertAssistant.class).chatModel(chatModel).build();
    }

    @Bean
    SupportAssistant supportAssistant(
            ChatModel chatModel,
            BookingTools bookingTools,
            RefundTools refundTools,
            ExpertTools expertTools,
            ContentRetriever contentRetriever,
            NoInternalNotesGuardrail noInternalNotesGuardrail) {
        return AiServices.builder(SupportAssistant.class)
                .chatModel(chatModel)
                .tools(bookingTools, refundTools, expertTools)
                .retrievalAugmentor(DefaultRetrievalAugmentor.builder()
                        .contentRetriever(contentRetriever)
                        .offloadBlocking(true) // the retriever is blocking, and chatLater(...) is asynchronous
                        .build())
                .outputGuardrails(noInternalNotesGuardrail)
                .build();
    }

    @Bean
    StreamingSupportAssistant streamingSupportAssistant(StreamingChatModel streamingChatModel) {
        return AiServices.builder(StreamingSupportAssistant.class)
                .streamingChatModel(streamingChatModel)
                .build();
    }
}
