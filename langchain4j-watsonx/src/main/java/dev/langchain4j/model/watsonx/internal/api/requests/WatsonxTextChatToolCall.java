package dev.langchain4j.model.watsonx.internal.api.requests;

import dev.langchain4j.agent.tool.ToolExecutionRequest;

public record WatsonxTextChatToolCall(Integer index, String id, String type, WatsonxTextChatToolCall.TextChatFunctionCall function) {

    public record TextChatFunctionCall(String name, String arguments) {
    }

    public static WatsonxTextChatToolCall of(ToolExecutionRequest toolExecutionRequest) {
        return new WatsonxTextChatToolCall(null, toolExecutionRequest.id(), "function",
            new WatsonxTextChatToolCall.TextChatFunctionCall(toolExecutionRequest.name(), toolExecutionRequest.arguments()));
    }


    public ToolExecutionRequest convert() {
        return ToolExecutionRequest.builder()
            .id(id)
            .name(function.name)
            .arguments(function.arguments)
            .build();
    }
}
