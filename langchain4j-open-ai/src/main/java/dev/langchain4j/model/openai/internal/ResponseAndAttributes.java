package dev.langchain4j.model.openai.internal;

import java.util.Map;

public class ResponseAndAttributes<Response> {

    public static final String RAW_RESPONSE_ATTRIBUTE = "rawResponse";
    public static final String RAW_EVENT_ATTRIBUTE = "rawEvent";

    private final Response response;
    private final Map<String, Object> attributes;

    public ResponseAndAttributes(Response response, Map<String, Object> attributes) {
        this.response = response;
        this.attributes = attributes;
    }

    public Response response() {
        return response;
    }

    public Map<String, Object> attributes() {
        return attributes;
    }
}
