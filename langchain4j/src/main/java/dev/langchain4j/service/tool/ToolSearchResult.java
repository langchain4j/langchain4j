package dev.langchain4j.service.tool;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ToolExecutionResultMessage;

import java.util.List;

import static dev.langchain4j.internal.Utils.copy;

/**
 * @since 1.10.0
 */
public class ToolSearchResult {

    private final List<ToolSpecification> foundTools;
    private final List<ToolExecutionResultMessage> searchResultMessages;

    public ToolSearchResult(List<ToolSpecification> foundTools) {
        this.foundTools = copy(foundTools); // TODO
        this.searchResultMessages = List.of(); // TODO
    }

    public ToolSearchResult(List<ToolSpecification> foundTools, List<ToolExecutionResultMessage> searchResultMessages) {
        this.foundTools = copy(foundTools); // TODO
        this.searchResultMessages = copy(searchResultMessages); // TODO
    }

    public List<ToolSpecification> foundTools() { // TODO name
        return foundTools;
    }

    public List<ToolExecutionResultMessage> searchResultMessages() {
        return searchResultMessages;
    }

    // TODO builder?

    // TODO eht
}
