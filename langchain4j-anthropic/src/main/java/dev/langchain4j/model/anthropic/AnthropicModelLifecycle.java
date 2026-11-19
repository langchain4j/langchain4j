package dev.langchain4j.model.anthropic;

/**
 * Lifecycle stages of a model in the Anthropic Models API.
 * See more details <a href="https://platform.claude.com/docs/en/api/models-list">here</a>.
 */
public enum AnthropicModelLifecycle {
    ACTIVE("active"),
    DEPRECATED("deprecated"),
    RETIRED("retired");

    private final String stringValue;

    AnthropicModelLifecycle(String stringValue) {
        this.stringValue = stringValue;
    }

    @Override
    public String toString() {
        return stringValue;
    }
}
