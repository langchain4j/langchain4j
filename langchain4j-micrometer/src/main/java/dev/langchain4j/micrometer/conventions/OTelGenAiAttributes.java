package dev.langchain4j.micrometer.conventions;

public enum OTelGenAiAttributes {
    // GenAI General
    /**
     * The name of the operation being performed.
     */
    OPERATION_NAME("gen_ai.operation.name"),
    /**
     * The model provider as identified by the client instrumentation.
     */
    SYSTEM("gen_ai.system"),

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

    /**
     * The number of dimensions the resulting output embeddings have.
     */
    REQUEST_EMBEDDING_DIMENSIONS("gen_ai.request.embedding.dimensions"),

    /**
     * The format in which the generated image is returned.
     */
    REQUEST_IMAGE_RESPONSE_FORMAT("gen_ai.request.image.response_format"),
    /**
     * The size of the image to generate.
     */
    REQUEST_IMAGE_SIZE("gen_ai.request.image.size"),
    /**
     * The style of the image to generate.
     */
    REQUEST_IMAGE_STYLE("gen_ai.request.image.style"),

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

    // GenAI Usage

    /**
     * The number of tokens used in the model input.
     */
    USAGE_INPUT_TOKENS("gen_ai.usage.input_tokens"),
    /**
     * The number of tokens used in the model output.
     */
    USAGE_OUTPUT_TOKENS("gen_ai.usage.output_tokens"),
    /**
     * The total number of tokens used in the model exchange.
     */
    USAGE_TOTAL_TOKENS("gen_ai.usage.total_tokens"),

    // GenAI Content

    /**
     * The full prompt sent to the model.
     */
    PROMPT("gen_ai.prompt"),
    /**
     * The full response received from the model.
     */
    COMPLETION("gen_ai.completion"),

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
