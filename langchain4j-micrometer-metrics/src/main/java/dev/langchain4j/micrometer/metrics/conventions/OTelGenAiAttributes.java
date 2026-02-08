package dev.langchain4j.micrometer.metrics.conventions;

public enum OTelGenAiAttributes {
    // GenAI General
    /**
     * The name of the operation being performed.
     */
    OPERATION_NAME("gen_ai.operation.name"),
    /**
     * The Generative AI provider as identified by the client or server instrumentation.
     */
    PROVIDER_NAME("gen_ai.provider.name"),

    // Token
    /**
     * The type of token that is counted: input, output, total.
     */
    TOKEN_TYPE("gen_ai.token.type"),

    // GenAI Request
    /**
     * The name of the model a request is being made to.
     */
    REQUEST_MODEL("gen_ai.request.model"),
    /**
     * The frequency penalty setting for the model request.
     */
    REQUEST_FREQUENCY_PENALTY("gen_ai.request.frequency_penalty"),
    /**
     * The maximum number of tokens the model generates for a request.
     */
    REQUEST_MAX_TOKENS("gen_ai.request.max_tokens"),
    /**
     * The presence penalty setting for the model request.
     */
    REQUEST_PRESENCE_PENALTY("gen_ai.request.presence_penalty"),
    /**
     * List of sequences that the model will use to stop generating further tokens.
     */
    REQUEST_STOP_SEQUENCES("gen_ai.request.stop_sequences"),
    /**
     * The temperature setting for the model request.
     */
    REQUEST_TEMPERATURE("gen_ai.request.temperature"),
    /**
     * The top_k sampling setting for the model request.
     */
    REQUEST_TOP_K("gen_ai.request.top_k"),
    /**
     * The top_p sampling setting for the model request.
     */
    REQUEST_TOP_P("gen_ai.request.top_p"),

    // GenAI Response

    /**
     * Reasons the model stopped generating tokens, corresponding to each generation received.
     */
    RESPONSE_FINISH_REASONS("gen_ai.response.finish_reasons"),
    /**
     * The unique identifier for the AI response.
     */
    RESPONSE_ID("gen_ai.response.id"),
    /**
     * The name of the model that generated the response.
     */
    RESPONSE_MODEL("gen_ai.response.model"),

    // Error

    /**
     * The type of error that occurred.
     */
    ERROR_TYPE("error.type"),


    // GenAi server information
    /**
     * The GenAI server port.
     */
    SERVER_PORT("server.port"),

    /**
     * The GenAI server address.
     */
    SERVER_ADDRESS("server.address");

    private final String value;

    OTelGenAiAttributes(final String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
