package com.example.support;

import dev.langchain4j.service.AiServiceStreamingEvent;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SupportService {

    private final CustomerSupportAssistant assistant;
    private final ProductAdvisor productAdvisor;
    private final TicketTriager ticketTriager;

    public SupportService(
            CustomerSupportAssistant assistant, ProductAdvisor productAdvisor, TicketTriager ticketTriager) {
        this.assistant = assistant;
        this.productAdvisor = productAdvisor;
        this.ticketTriager = ticketTriager;
    }

    public String answer(String question) {
        return assistant.chat(question);
    }

    public Uni<String> answerLater(String question) {
        return Uni.createFrom().completionStage(() -> assistant.chatAsync(question));
    }

    public Multi<String> streamAnswer(String question) {
        return Multi.createFrom().publisher(assistant.chatStream(question));
    }

    public Multi<String> recommendProducts(String wishes) {
        return Multi.createFrom().publisher(productAdvisor.recommend(wishes));
    }

    public Multi<AiServiceStreamingEvent> recommendProductsWithEvents(String wishes) {
        return Multi.createFrom().publisher(productAdvisor.recommendWithEvents(wishes));
    }

    public TicketTriage triage(String issue) {
        return ticketTriager.triage(issue);
    }

    public Uni<TicketTriage> triageLater(String issue) {
        return Uni.createFrom().completionStage(() -> ticketTriager.triageAsync(issue));
    }
}
