package com.example.support;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SupportService {

    private final SupportAssistant supportAssistant;

    public SupportService(SupportAssistant supportAssistant) {
        this.supportAssistant = supportAssistant;
    }

    public String answer(String customerId, String question) {
        return supportAssistant.chat(customerId, question.strip());
    }
}
