package dev.langchain4j.model.anthropic.internal.client;

import dev.langchain4j.Internal;

/**
 * @since 1.2.0
 */
@Internal
public class AnthropicCreateMessageOptions {

    private final boolean returnThinking;

    public AnthropicCreateMessageOptions(boolean returnThinking) {
        this.returnThinking = returnThinking;
    }

    public boolean returnThinking() {
        return returnThinking;
    }
}
