package dev.langchain4j.model.cohere.internal.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Getter;

import java.util.List;
import java.util.Map;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@Getter
@JsonInclude(NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
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
