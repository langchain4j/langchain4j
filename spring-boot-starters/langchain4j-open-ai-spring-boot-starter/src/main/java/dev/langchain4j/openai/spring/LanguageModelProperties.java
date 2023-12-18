package dev.langchain4j.openai.spring;

import lombok.Getter;
import lombok.Setter;

import java.time.Duration;

@Getter
@Setter
class LanguageModelProperties {

    String baseUrl;
    String apiKey;
    String organizationId;
    String modelName;
    Double temperature;
    Duration timeout;
    Integer maxRetries;
    Boolean logRequests;
    Boolean logResponses;
}