package dev.langchain4j.model.mistralai.internal.api;

import java.util.List;

public record MistralAiModerationRequest(String model, List<String> input) {
}
