package dev.langchain4j.openai.spring;

import lombok.Getter;
import lombok.Setter;

import java.time.Duration;

@Getter
@Setter
class ModerationModelProperties {

    String baseUrl;
    String apiKey;
    String modelName;
    Duration timeout;
    Integer maxRetries;
    Boolean logRequests;
    Boolean logResponses;
}