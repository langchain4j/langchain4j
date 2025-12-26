package dev.langchain4j.model.anthropic.internal.client;

import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.model.anthropic.internal.api.AnthropicCreateMessageResponse;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * @since 1.10.0
 */
public class ParsedAndRawResponse {

    private final AnthropicCreateMessageResponse parsedResponse;
    private final SuccessfulHttpResponse rawResponse;

    public ParsedAndRawResponse(AnthropicCreateMessageResponse parsedResponse, SuccessfulHttpResponse rawResponse) {
        this.parsedResponse = ensureNotNull(parsedResponse, "parsedResponse");
        this.rawResponse = rawResponse;
    }

    public AnthropicCreateMessageResponse parsedResponse() {
        return parsedResponse;
    }

    public SuccessfulHttpResponse rawResponse() {
        return rawResponse;
    }
}
