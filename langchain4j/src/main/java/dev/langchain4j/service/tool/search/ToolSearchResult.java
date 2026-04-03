package dev.langchain4j.service.tool.search;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.message.ToolExecutionResultMessage;

import java.util.List;
import java.util.Objects;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * The result of a tool search.
 * Contains a list of the found tool names
 * and the text to be sent to the LLM inside the {@link ToolExecutionResultMessage}.
 *
 * @since 1.12.0
 */
@Experimental
public class ToolSearchResult {

    private final List<String> foundToolNames;
    private final String toolResultMessageText;

    /**
     * Creates a {@code ToolSearchResult} from a list of tool names and a tool result message text.
     *
     * @param foundToolNames        the names of the found tools
     * @param toolResultMessageText the text to be set in the {@link ToolExecutionResultMessage}
     *                              and sent to the LLM as the result of the tool search
     */
    public ToolSearchResult(List<String> foundToolNames, String toolResultMessageText) {
        this.foundToolNames = copy(foundToolNames);
        this.toolResultMessageText = ensureNotNull(toolResultMessageText, "toolResultMessageText");
    }

    /**
     * Returns the list of the found tool names.
     */
    public List<String> foundToolNames() {
        return foundToolNames;
    }

    /**
     * Returns the text to be set in the {@link dev.langchain4j.data.message.ToolExecutionResultMessage}
     * and sent to the LLM as the result of the tool search.
     */
    public String toolResultMessageText() {
        return toolResultMessageText;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ToolSearchResult that = (ToolSearchResult) o;
        return Objects.equals(foundToolNames, that.foundToolNames)
                && Objects.equals(toolResultMessageText, that.toolResultMessageText);
    }

    @Override
    public int hashCode() {
        return Objects.hash(foundToolNames, toolResultMessageText);
    }

    @Override
    public String toString() {
        return "ToolSearchResult{" +
                "foundToolNames=" + foundToolNames +
                ", toolResultMessageText='" + toolResultMessageText + '\'' +
                '}';
    }
}
