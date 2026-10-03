package dev.langchain4j.model.deepseek;

/**
 * Available DeepSeek model names.
 *
 * @see <a href="https://api-docs.deepseek.com/quick_start/pricing">DeepSeek Models</a>
 */
public enum DeepSeekChatModelName {

    /**
     * DeepSeek-V4 Flash model. Fast, cost-effective model suitable for
     * most everyday tasks with lower latency.
     */
    DEEPSEEK_V4_FLASH("deepseek-v4-flash"),

    /**
     * DeepSeek-V4 Pro model. Full-featured flagship model with advanced
     * reasoning capabilities, larger context window, and higher accuracy.
     */
    DEEPSEEK_V4_PRO("deepseek-v4-pro");

    private final String stringValue;

    DeepSeekChatModelName(String stringValue) {
        this.stringValue = stringValue;
    }

    @Override
    public String toString() {
        return stringValue;
    }
}
