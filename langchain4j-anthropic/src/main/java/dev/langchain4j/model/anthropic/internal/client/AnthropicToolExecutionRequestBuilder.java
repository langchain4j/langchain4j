package dev.langchain4j.model.anthropic.internal.client;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolExecutionRequest;

@Internal
class AnthropicToolExecutionRequestBuilder {

    private final String id;
    private final String name;
    private final StringBuilder argumentsBuilder = new StringBuilder();

    public AnthropicToolExecutionRequestBuilder(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public void appendArguments(String partialArguments) {
        this.argumentsBuilder.append(partialArguments);
    }

    public ToolExecutionRequest build() {
        return ToolExecutionRequest
                .builder()
                .id(id)
                .name(name)
                .arguments(argumentsBuilder.toString())
                .build();
    }
}
