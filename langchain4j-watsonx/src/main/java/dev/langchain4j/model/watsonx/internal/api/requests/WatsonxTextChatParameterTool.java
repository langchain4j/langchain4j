package dev.langchain4j.model.watsonx.internal.api.requests;

import dev.langchain4j.agent.tool.ToolSpecification;

import java.util.Map;

public record WatsonxTextChatParameterTool(String type, WatsonxTextChatParameterFunction function) {

    public record WatsonxTextChatParameterFunction(String name, String description, Map<String, Object> parameters) {
    }

    public static WatsonxTextChatParameterTool of(ToolSpecification toolSpecification) {

        WatsonxTextChatParameterFunction parameters = new WatsonxTextChatParameterFunction(toolSpecification.name(), toolSpecification.description(),
            toolSpecification.parameters() != null ?
            Map.of(
            "properties", toolSpecification.parameters().properties(),
            "required", toolSpecification.parameters().required()) : Map.of()
        );
        return new WatsonxTextChatParameterTool("function", parameters);
    }
}
