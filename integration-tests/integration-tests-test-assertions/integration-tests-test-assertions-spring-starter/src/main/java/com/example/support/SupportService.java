package com.example.support;

import org.springframework.stereotype.Service;

@Service
public class SupportService {

    private final SupportAssistant supportAssistant;

    public SupportService(SupportAssistant supportAssistant) {
        this.supportAssistant = supportAssistant;
    }

    public String answer(String customerId, String question) {
        return supportAssistant.chat(customerId, question.strip());
    }
}
