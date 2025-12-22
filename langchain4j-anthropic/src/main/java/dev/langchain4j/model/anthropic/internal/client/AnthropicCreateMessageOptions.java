package dev.langchain4j.model.anthropic.internal.client;

import dev.langchain4j.Internal;

/**
 * @since 1.2.0
 */
@Internal
public class AnthropicCreateMessageOptions {

    private final boolean returnThinking;
    private final boolean returnServerToolResults;

    public AnthropicCreateMessageOptions(boolean returnThinking) {
        this(returnThinking, false);
    }

    public AnthropicCreateMessageOptions(boolean returnThinking, boolean returnServerToolResults) {
        this.returnThinking = returnThinking;
        this.returnServerToolResults = returnServerToolResults;
    }

    public boolean returnThinking() {
        return returnThinking;
    }

    public boolean returnServerToolResults() {
        return returnServerToolResults;
    }
}
