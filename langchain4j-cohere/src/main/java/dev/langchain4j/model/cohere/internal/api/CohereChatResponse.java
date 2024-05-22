package dev.langchain4j.model.cohere.internal.api;

import lombok.Getter;

import java.util.List;
import java.util.Map;


@Getter
public class CohereChatResponse {

    String text;

    String generationId;

    List<Citation> citations;

    List<Map<String, String>> documents;

    Boolean isSearchRequired;

    List<SearchQuery> searchQueries;

    List<SearchResult> searchResults;

    String finishReason;

    List<ToolCall> toolCalls;

    List<ChatHistory> chatHistory;

    Meta meta;

}
