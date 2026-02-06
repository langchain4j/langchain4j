package dev.langchain4j.service.tool.search;

import java.util.List;

import static dev.langchain4j.internal.Utils.copy;

/**
 * TODO
 *
 * @since 1.12.0
 */
public class ToolSearchResult {

    private final List<String> foundToolNames; // TODO names

    public ToolSearchResult(List<String> foundToolNames) {
        this.foundToolNames = copy(foundToolNames); // TODO
    }

    public List<String> foundToolNames() { // TODO name
        return foundToolNames;
    }

    // TODO builder?

    // TODO eht
}
