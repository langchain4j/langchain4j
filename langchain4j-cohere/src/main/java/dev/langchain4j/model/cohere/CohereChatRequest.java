package dev.langchain4j.model.cohere;

import lombok.Builder;
import lombok.NonNull;

import java.util.List;
import java.util.Map;

@Builder
public class CohereChatRequest {

    @NonNull
    String message;

    String model;

    boolean stream;

    String preamble;

    List<ChatHistory> chatHistory;

    String conversationId;

    String promptTruncation;

    List<Connector> connectors;

    boolean searchQueriesOnly;

    List<Map<String, String>> documents;

    Double temperature;

    int maxTokens;

    Integer k;

    Double p;

    Double seed;

    List<String> stopSequences;

    Double frequencyPenalty;

    Double presencePenalty;

    List<Tool> tools;

    List<ToolResult> toolResults;

}
