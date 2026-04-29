package dev.langchain4j.observability.event;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.observability.api.event.ToolExecutedEvent;

import java.util.List;

/**
 * Default implementation of {@link ToolExecutedEvent}.
 */
public class DefaultToolExecutedEvent extends AbstractAiServiceEvent implements ToolExecutedEvent {

    private final ToolExecutionRequest request;
    private final List<Content> resultContents;

    public DefaultToolExecutedEvent(ToolExecutedEventBuilder builder) {
        super(builder);
        this.request = ensureNotNull(builder.request(), "request");

        boolean hasResultText = builder.resultText() != null;
        boolean hasResultContents = builder.resultContents() != null && !builder.resultContents().isEmpty();

        if (hasResultText && hasResultContents) {
            throw new IllegalArgumentException("resultText and resultContents are mutually exclusive");
        } else if (hasResultText) {
            this.resultContents = List.of(TextContent.from(builder.resultText()));
        } else if (hasResultContents) {
            this.resultContents = copy(builder.resultContents());
        } else {
            throw new IllegalArgumentException("Either resultText or resultContents must be provided");
        }
    }

    @Override
    public ToolExecutionRequest request() {
        return request;
    }

    @Override
    public String resultText() {
        if (resultContents.size() == 1 && resultContents.get(0) instanceof TextContent textContent) {
            return textContent.text();
        }
        throw new IllegalStateException(
                "resultText() cannot be called when resultContents contains non-text or multiple content elements. "
                        + "Use resultContents() instead.");
    }

    @Override
    @Experimental
    public List<Content> resultContents() {
        return resultContents;
    }
}
