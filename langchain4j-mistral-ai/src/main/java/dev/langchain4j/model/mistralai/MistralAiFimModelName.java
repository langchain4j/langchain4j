package dev.langchain4j.model.mistralai;

/**
 * The MistralAiFimModelName enum represents the available code generation models in the Mistral AI module.
 *
 * @see <a href="https://docs.mistral.ai/capabilities/code_generation/">More about Mistral Code generation models</a>
 */
public enum MistralAiFimModelName {
    CODESTRAL_LATEST("codestral-latest"),
    OPEN_CODESTRAL_MAMBA("open-codestral-mamba");

    private final String value;

    MistralAiFimModelName(String value) {
        this.value = value;
    }

    public String toString() {
        return this.value;
    }
}
