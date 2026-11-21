package com.example.support.expert;

import com.example.support.assistant.ExpertAssistant;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

@Component
public class ExpertTools {

    private final ExpertAssistant expertAssistant;

    public ExpertTools(ExpertAssistant expertAssistant) {
        this.expertAssistant = expertAssistant;
    }

    @Tool("Asks a senior expert a question that you cannot answer yourself")
    public String askExpert(String question) {
        return expertAssistant.answer(question);
    }
}
