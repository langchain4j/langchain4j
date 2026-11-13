package dev.langchain4j.model.openai.internal;

import dev.langchain4j.Experimental;
import dev.langchain4j.exception.AsyncNotSupportedException;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.internal.AsyncNotSupported;
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
     * The default implementation returns a failed future carrying an {@link AsyncNotSupportedException}.
     * <p>
     * An implementation never throws and never returns {@code null}: every failure, including an invalid request,
     * completes the returned future exceptionally.
     *
     * @return a {@link CompletableFuture} of the parsed and raw response, completed without blocking the caller.
     * @since 1.20.0
     */
    @Experimental
    default CompletableFuture<ParsedAndRawResponse<ResponseContent>> executeRawAsync() {
        return AsyncNotSupported.failedFuture(getClass(), "executeRawAsync");
    }

    AsyncResponseHandling onResponse(Consumer<ResponseContent> responseHandler);
}
