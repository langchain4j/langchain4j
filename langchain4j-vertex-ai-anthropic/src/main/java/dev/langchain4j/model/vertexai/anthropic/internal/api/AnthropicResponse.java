package dev.langchain4j.model.vertexai.anthropic.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public class AnthropicResponse {

    public String id;
    public String type;
    public String role;
    public String model;
    public List<AnthropicContent> content;
    public String stopReason;
    public String stopSequence;
    public AnthropicUsage usage;

    public AnthropicResponse() {}
}
