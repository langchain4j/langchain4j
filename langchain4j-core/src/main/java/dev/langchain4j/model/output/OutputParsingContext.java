package dev.langchain4j.model.output;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.rag.content.Content;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class OutputParsingContext {
    private final TokenUsage tokenUsage;
    private final Response<AiMessage> response;
    private final List<Content> sources;
}
