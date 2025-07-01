package dev.langchain4j.model.openai.internal;

import dev.langchain4j.http.client.SuccessfulHttpResponse;
import java.util.function.Consumer;

public interface SyncOrAsync<ResponseContent> {

    ResponseContent execute();

    default ParsedAndRawResponse<ResponseContent> executeRaw() {
        ResponseContent parsedResponse = execute();
        SuccessfulHttpResponse rawResponse = null;
        return new ParsedAndRawResponse(parsedResponse, rawResponse);
    }

    AsyncResponseHandling onResponse(Consumer<ResponseContent> responseHandler);
}
