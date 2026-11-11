package dev.langchain4j.model.xai;

/**
 * Some of the Grok models available through the xAI API.
 * See the <a href="https://docs.x.ai/docs/models">xAI models page</a> for the up-to-date list.
 */
public enum XAiChatModelName {
    GROK_4_7("grok-4.7"),
    GROK_4_20("grok-4.20");

    private final String stringValue;

    XAiChatModelName(String stringValue) {
        this.stringValue = stringValue;
    }

    @Override
    public String toString() {
        return stringValue;
    }
}
