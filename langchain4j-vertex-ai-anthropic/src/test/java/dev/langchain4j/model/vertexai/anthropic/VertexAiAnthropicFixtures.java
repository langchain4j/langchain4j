package dev.langchain4j.model.vertexai.anthropic;

import static java.time.DayOfWeek.MONDAY;

import java.time.LocalDate;

class VertexAiAnthropicFixtures {

    static final String DEFAULT_LOCATION = "us-east5";
    static final String DEFAULT_MODEL_NAME = "claude-3-5-haiku@20241022";
    static final String SIMPLE_QUESTION = "What is the capital of France?";
    static final String INVALID_MODEL_NAME = "invalid-model-name";

    static boolean isMonday() {
        return LocalDate.now().getDayOfWeek() == MONDAY;
    }
}
