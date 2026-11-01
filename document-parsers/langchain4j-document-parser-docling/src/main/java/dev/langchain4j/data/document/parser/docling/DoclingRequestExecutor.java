package dev.langchain4j.data.document.parser.docling;

import ai.docling.serve.api.DoclingServeApi;
import ai.docling.serve.api.request.DocumentRequest;
import ai.docling.serve.api.response.ProcessedDocumentResponse;
import java.util.concurrent.CompletionStage;

/**
 * Strategy that decides how {@link DoclingDocumentParser} invokes Docling to obtain a
 * {@link ProcessedDocumentResponse} for a prepared {@link DocumentRequest} (the document source has already been
 * injected into the request by the parser).
 * <p>
 * The {@linkplain DoclingDocumentParser.Builder#requestExecutor(DoclingRequestExecutor) default executor} calls the
 * asynchronous convert/chunk endpoints matching the request type; those endpoints already submit a task, poll for
 * completion, and fetch the result on the common {@link java.util.concurrent.ForkJoinPool}. Supplying a custom executor
 * lets callers control exactly how Docling is called — for example to add retry or backoff, to run the blocking call on
 * their own executor (such as the synchronous convert/chunk methods on a dedicated pool or virtual threads) instead of
 * the common {@link java.util.concurrent.ForkJoinPool}, or for fully custom orchestration. When a custom executor is
 * supplied, the parser no longer restricts the request type or target, since the executor owns those semantics.
 */
@FunctionalInterface
public interface DoclingRequestExecutor {

    /**
     * Invokes Docling for the given request.
     *
     * @param client
     *            the Docling client configured on the parser
     * @param request
     *            the request template with the document source already injected
     *
     * @return a stage that completes with the Docling response
     */
    CompletionStage<? extends ProcessedDocumentResponse> execute(DoclingServeApi client, DocumentRequest request);
}
