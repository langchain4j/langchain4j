package dev.langchain4j.model.watsonx.internal.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import dev.langchain4j.model.watsonx.internal.api.requests.WatsonxChatMessage;
import dev.langchain4j.model.watsonx.internal.api.requests.WatsonxChatParameters;
import dev.langchain4j.model.watsonx.internal.api.requests.WatsonxTextChatParameterTool;

import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record WatsonxChatCompletionRequest(
    String modelId,
    String projectId,
    List<WatsonxChatMessage> messages,
    List<WatsonxTextChatParameterTool> tools,
    @JsonUnwrapped WatsonxChatParameters parameters
) {}
