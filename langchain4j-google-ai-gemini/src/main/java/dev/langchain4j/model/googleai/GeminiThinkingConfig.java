package dev.langchain4j.model.googleai;

public class GeminiThinkingConfig {
    private final Float thinkingBudget;

    public GeminiThinkingConfig(Float thinkingBudget) {
        this.thinkingBudget = thinkingBudget;
    }

    public Float getThinkingBudget() {
        return thinkingBudget;
    }
}
