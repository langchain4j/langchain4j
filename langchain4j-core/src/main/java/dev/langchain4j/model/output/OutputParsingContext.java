package dev.langchain4j.model.output;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.rag.content.Content;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Context for parsing the output of a service method.
 */
@Getter
@Builder
public class OutputParsingContext {
    /**
     * Current token usage.
     */
    private final TokenUsage tokenUsage;
    /**
     * The sources of the content.
     */
    private final List<Content> sources;
    /**
     * The response to parse.
     */
    private final Response<AiMessage> response;
}
