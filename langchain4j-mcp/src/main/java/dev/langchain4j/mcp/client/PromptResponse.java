package dev.langchain4j.mcp.client;

import java.util.List;

public record PromptResponse(String description, List<PromptMessage> messages) {}
