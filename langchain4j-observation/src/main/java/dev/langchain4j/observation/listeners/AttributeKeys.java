package dev.langchain4j.observation.listeners;

public enum AttributeKeys {
    TOKEN_USAGE("gen_ai.client.token.usage"),
    OPERATION_NAME("gen_ai.operation.name"),
    PROVIDER_NAME("gen_ai.provider.name"),
    SYSTEM("gen_ai.system"),
    REQUEST_MODEL("gen_ai.request.model"),
    RESPONSE_MODEL("gen_ai.response.model"),
    TOKEN_TYPE("gen_ai.token.type");

    private final String value;

    AttributeKeys(final String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
