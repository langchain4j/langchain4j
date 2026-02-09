package dev.langchain4j.service.tool.search;

import java.util.List;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * TODO
 *
 * @since 1.12.0
 */
public class ToolSearchResult {

    private final List<String> foundToolNames; // TODO names
    private final String toolExecutionResultMessageText;

    public ToolSearchResult(List<String> foundToolNames) {
        this.foundToolNames = copy(foundToolNames);
        if (foundToolNames.isEmpty()) {
            this.toolExecutionResultMessageText = "No matching tools found";
        } else {
            this.toolExecutionResultMessageText = "Tools found: " + String.join(", ", foundToolNames);
        }
    }

    public ToolSearchResult(List<String> foundToolNames, String toolExecutionResultMessageText) {
        this.foundToolNames = copy(foundToolNames);
        this.toolExecutionResultMessageText = ensureNotNull(toolExecutionResultMessageText, "toolExecutionResultMessageText");
    }

    public List<String> foundToolNames() { // TODO name
        return foundToolNames;
    }

    /**
     * TODO
     */
    public String toolExecutionResultMessageText() { // TODO name
        return toolExecutionResultMessageText;
    }

    // TODO builder?

    // TODO eht
}
