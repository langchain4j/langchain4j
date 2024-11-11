package dev.langchain4j.model.mistralai.internal.api;


import java.util.List;

public record MistralAiModerationResponse(String id, String model, List<MistralModerationResult> results) {
}
