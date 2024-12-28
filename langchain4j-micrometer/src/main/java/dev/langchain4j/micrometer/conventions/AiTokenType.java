package dev.langchain4j.micrometer.conventions;

public enum AiTokenType {
    /**
     * Input token.
     */
    INPUT("input"),
    /**
     * Output token.
     */
    OUTPUT("output"),
    /**
     * Total token.
     */
    TOTAL("total");

    private final String value;

    AiTokenType(String value) {
        this.value = value;
    }

    /**
     * Return the value of the token type.
     * @return the value of the token type
     */
    public String value() {
        return this.value;
    }
}
