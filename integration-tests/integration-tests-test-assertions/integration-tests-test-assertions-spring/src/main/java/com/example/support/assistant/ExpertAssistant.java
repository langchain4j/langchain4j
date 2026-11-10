package com.example.support.assistant;

import dev.langchain4j.service.SystemMessage;

public interface ExpertAssistant {

    @SystemMessage("You are a senior car rental expert. Answer questions of support agents.")
    String answer(String question);
}
