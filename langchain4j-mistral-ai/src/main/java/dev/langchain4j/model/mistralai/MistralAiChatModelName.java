package dev.langchain4j.model.mistralai;

/**
 * Represents the available chat completion models for Mistral AI.
 * <p>
 * The chat completion models are used to generate responses for chat-based applications.
 * Each model has a specific power and capability level.
 *
 * @see <a href="https://docs.mistral.ai/models/model-selection-guide/">Mistral Model Selection</a>
 */
public enum MistralAiChatModelName {
    OPEN_MISTRAL_7B("open-mistral-7b"),

    OPEN_MIXTRAL_8x7B("open-mixtral-8x7b"),
    OPEN_MIXTRAL_8X22B("open-mixtral-8x22b"),

    MISTRAL_SMALL_LATEST("mistral-small-latest"),

    MISTRAL_MEDIUM_LATEST("mistral-medium-latest"),

    MISTRAL_LARGE_LATEST("mistral-large-latest"),

    MAGISTRAL_SMALL_LATEST("magistral-small-latest"),

    MAGISTRAL_MEDIUM_LATEST("magistral-medium-latest"),

    MISTRAL_MODERATION_LATEST("mistral-moderation-latest"),

    OPEN_MISTRAL_NEMO("open-mistral-nemo"),

    CODESTRAL_LATEST("codestral-latest"),

    VOXTRAL_MINI_LATEST("voxtral-mini-latest"),

    VOXTRAL_SMALL_LATEST("voxtral-small-latest");

    private final String value;

    MistralAiChatModelName(String value) {
        this.value = value;
    }

    public String toString() {
        return this.value;
    }
}
