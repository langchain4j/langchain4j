package dev.langchain4j.model.mistralai;

/**
 * Represents the available chat completion models for Mistral AI.
 * 
 * <p>
 * The chat completion models are used to generate responses for chat-based applications.
 * Each model has a specific power and capability level.
 * </p>
 * 
 * <p>
 * The available chat completion models are:
 * <ul>
 *   <li>{@link #MISTRAL_TINY} - powered by Mistral-7B-v0.2</li>
 *   <li>{@link #MISTRAL_SMALL} - powered by Mixtral-8X7B-v0.1</li>
 *   <li>{@link #MISTRAL_MEDIUM} - currently relies on an internal prototype model</li>
 * </ul>
 * </p>
 * 
 * @see <a href="https://docs.mistral.ai/platform/endpoints/">Mistral AI Endpoints</a>
 */
enum ChatCompletionModel {

    // powered by Mistral-7B-v0.2
    MISTRAL_TINY("mistral-tiny"),
    // powered Mixtral-8X7B-v0.1
    MISTRAL_SMALL("mistral-small"),
    // currently relies on an internal prototype model
    MISTRAL_MEDIUM("mistral-medium");

    private final String value;

    private ChatCompletionModel(String value) {
        this.value = value;
    }

    public String toString() {
        return this.value;
    }


}
