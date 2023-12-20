package dev.langchain4j.openai.spring;

import lombok.Getter;
import lombok.Setter;

import java.nio.file.Path;
import java.time.Duration;

@Getter
@Setter
class ImageModelProperties {

    String baseUrl;
    String apiKey;
    String organizationId;
    String modelName;
    String size;
    String quality;
    String style;
    String user;
    String responseFormat;
    Duration timeout;
    Integer maxRetries;
    Boolean logRequests;
    Boolean logResponses;
    Boolean withPersisting;
    Path persistTo;
}