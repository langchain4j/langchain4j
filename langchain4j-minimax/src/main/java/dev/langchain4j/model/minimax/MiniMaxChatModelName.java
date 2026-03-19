package dev.langchain4j.model.minimax;

/**
 * Represents available MiniMax chat model names.
 * MiniMax provides OpenAI-compatible API endpoints.
 *
 * @see <a href="https://platform.minimax.io/docs/api-reference/text-openai-api">MiniMax Chat API</a>
 */
public enum MiniMaxChatModelName {

    MINIMAX_M2_7("MiniMax-M2.7"),
    MINIMAX_M2_7_HIGHSPEED("MiniMax-M2.7-highspeed");

    private final String stringValue;

    MiniMaxChatModelName(String stringValue) {
        this.stringValue = stringValue;
    }

    @Override
    public String toString() {
        return stringValue;
    }
}
