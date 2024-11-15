package dev.langchain4j.model.watsonx.internal.api.requests;

import java.util.List;
import java.util.Map;

public record WatsonxChatMessageUser(String role, List<Map<String, Object>> content, String name) implements WatsonxChatMessage {
}

