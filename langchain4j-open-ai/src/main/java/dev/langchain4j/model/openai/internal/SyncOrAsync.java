package dev.langchain4j.model.openai.internal;

import dev.langchain4j.Experimental;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface SyncOrAsync<ResponseContent> {

    ResponseContent execute();

    default ParsedAndRawResponse<ResponseContent> executeRaw() {
        ResponseContent parsedResponse = execute();
        SuccessfulHttpResponse rawHttpResponse = null;
        return new ParsedAndRawResponse<>(parsedResponse, rawHttpResponse);
    }

    /**
     * @return a {@link CompletableFuture} of the parsed and raw response, completed without blocking the caller.
     * @since 1.20.0
     */
    @Experimental
    default CompletableFuture<ParsedAndRawResponse<ResponseContent>> executeRawAsync() {
        throw new UnsupportedOperationException("Not implemented");
    }

    AsyncResponseHandling onResponse(Consumer<ResponseContent> responseHandler);
}
