package dev.langchain4j.model.deepseek;

/**
 * Available DeepSeek model names.
 *
 * @see <a href="https://api-docs.deepseek.com/quick_start/pricing">DeepSeek Models</a>
 */
public enum DeepSeekChatModelName {

    /**
     * DeepSeek-V3 chat model. Latest flagship model with 671B total parameters.
     * Supports 64K context window.
     */
    DEEPSEEK_CHAT("deepseek-chat"),

    /**
     * DeepSeek-R1 reasoning model. Specialized for complex reasoning tasks
     * with chain-of-thought capabilities.
     */
    DEEPSEEK_REASONER("deepseek-reasoner");

    private final String stringValue;

    DeepSeekChatModelName(String stringValue) {
        this.stringValue = stringValue;
    }

    @Override
    public String toString() {
        return stringValue;
    }
}
