package dev.langchain4j.model.gemini;

public enum GeminiOutcome {
    OUTCOME_UNSPECIFIED,
    OUTCOME_OK,
    OUTCOME_FAILED,
    OUTCOME_DEADLINE_EXCEEDED;

    @Override
    public String toString() {
        return this.name().toLowerCase();
    }
}
