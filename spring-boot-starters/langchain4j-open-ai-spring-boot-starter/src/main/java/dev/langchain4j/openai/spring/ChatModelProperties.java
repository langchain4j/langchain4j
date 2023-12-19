package dev.langchain4j.openai.spring;

import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
import java.util.List;

@Getter
@Setter
class ChatModelProperties {

    String baseUrl;
    String apiKey;
    String organizationId;
    String modelName;
    Double temperature;
    Double topP;
    List<String> stop;
    Integer maxTokens;
    Double presencePenalty;
    Double frequencyPenalty;
    Duration timeout;
    Integer maxRetries;
    Boolean logRequests;
    Boolean logResponses;
}