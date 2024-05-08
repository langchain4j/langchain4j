package dev.langchain4j.model.cohere.internal.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.NonNull;

import java.util.List;
import java.util.Map;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@Builder
@JsonInclude(NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CohereChatRequest {

    @NonNull
    String message;

    String model;

    Boolean stream;

    String preamble;

    List<ChatHistory> chatHistory;

    String conversationId;

    String promptTruncation;

    List<Connector> connectors;

    Boolean searchQueriesOnly;

    List<Map<String, String>> documents;

    Double temperature;

    Integer maxTokens;

    Integer k;

    Double p;

    Double seed;

    List<String> stopSequences;

    Double frequencyPenalty;

    Double presencePenalty;

    List<Tool> tools;

    List<ToolResult> toolResults;

}
