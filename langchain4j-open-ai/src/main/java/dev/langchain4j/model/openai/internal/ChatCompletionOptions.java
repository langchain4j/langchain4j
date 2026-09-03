package dev.langchain4j.model.openai.internal;

import dev.langchain4j.Internal;

@Internal
public record ChatCompletionOptions(
        boolean returnThinking,
        boolean accumulateToolCallId
) {
}
