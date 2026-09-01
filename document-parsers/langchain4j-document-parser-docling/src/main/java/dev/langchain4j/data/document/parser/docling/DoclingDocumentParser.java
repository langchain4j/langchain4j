package dev.langchain4j.data.document.parser.docling;

import static dev.langchain4j.internal.Utils.getOrDefault;

import ai.docling.serve.api.DoclingServeApi;
import ai.docling.serve.api.chunk.request.HierarchicalChunkDocumentRequest;
import ai.docling.serve.api.chunk.request.HybridChunkDocumentRequest;
import ai.docling.serve.api.chunk.response.Chunk;
import ai.docling.serve.api.chunk.response.ChunkDocumentResponse;
import ai.docling.serve.api.convert.request.BatchConvertDocumentRequest;
import ai.docling.serve.api.convert.request.ConvertDocumentRequest;
import ai.docling.serve.api.convert.request.options.ConvertDocumentOptions;
import ai.docling.serve.api.convert.request.source.FileSource;
import ai.docling.serve.api.convert.request.target.InBodyTarget;
import ai.docling.serve.api.convert.request.target.Target;
import ai.docling.serve.api.convert.response.InBodyConvertDocumentResponse;
import ai.docling.serve.api.request.DocumentRequest;
import ai.docling.serve.api.response.ProcessedDocumentResponse;
import dev.langchain4j.data.document.BlankDocumentException;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.internal.ValidationUtils;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link DocumentParser} backed by a Docling Serve instance.
 * <p>
 * The operation performed by Docling (convert, hierarchical chunking, hybrid chunking, ...) is controlled by the
 * {@link DocumentRequest} template supplied to the {@link Builder}. The template carries everything except the document
 * source: the parser injects the source of the {@link InputStream} being parsed into a fresh copy of the template for
 * each call, then routes the request to the matching Docling endpoint. How the resulting response is turned into a
 * {@link Document} is controlled by the {@linkplain Builder#documentExtractor(Function) document extractor} (for
 * conversion requests) and the {@linkplain Builder#chunkExtractor(Function) chunk extractor} (for chunk requests);
 * {@linkplain Builder#documentTextExtractor(Function) text-only} variants are also available.
 * <p>
 * How Docling is actually called is controlled by the {@linkplain Builder#requestExecutor(DoclingRequestExecutor)
 * request executor}. By default the parser calls the asynchronous convert/chunk endpoints and exposes both a blocking
 * {@link #parse(InputStream)} and a non-blocking {@link #parseAsync(InputStream)}.
 */
public class DoclingDocumentParser implements DocumentParser {

    private record PreparedRequest(DocumentRequest request, int sizeBytes) {}

    private static final Logger log = LoggerFactory.getLogger(DoclingDocumentParser.class);
    private static final String DEFAULT_FILENAME = "document";
    private static final String DOCUMENT_SIZE_BYTES = "document_size_bytes";
    private static final DocumentRequest DEFAULT_DOCUMENT_REQUEST =
            ConvertDocumentRequest.builder().build();

    private static final Function<InBodyConvertDocumentResponse, Document> DEFAULT_DOCUMENT_EXTRACTOR =
            response -> textToDocument(response.getDocument().getMarkdownContent());

    private static final Function<ChunkDocumentResponse, Document> DEFAULT_CHUNK_EXTRACTOR = response ->
            textToDocument(response.getChunks().stream().map(Chunk::getText).collect(Collectors.joining("\n")));

    private static final DoclingRequestExecutor DEFAULT_REQUEST_EXECUTOR = (client, request) -> {
        if (request instanceof ConvertDocumentRequest convertRequest) {
            return client.convertSourceAsync(convertRequest);
        } else if (request instanceof HierarchicalChunkDocumentRequest hierarchicalRequest) {
            return client.chunkSourceWithHierarchicalChunkerAsync(hierarchicalRequest);
        } else if (request instanceof HybridChunkDocumentRequest hybridRequest) {
            return client.chunkSourceWithHybridChunkerAsync(hybridRequest);
        }

        throw new IllegalArgumentException(
                "Unsupported request type: " + request.getClass().getName());
    };

    private final DoclingServeApi doclingClient;
    private final DocumentRequest documentRequest;
    private final Function<InBodyConvertDocumentResponse, Document> documentExtractor;
    private final Function<ChunkDocumentResponse, Document> chunkExtractor;
    private final DoclingRequestExecutor requestExecutor;

    /**
     * @deprecated use {@link #builder()} with {@link Builder#doclingClient(DoclingServeApi)} instead
     */
    @Deprecated(forRemoval = true)
    public DoclingDocumentParser(DoclingServeApi doclingClient) {
        this(builder().doclingClient(doclingClient));
    }

    /**
     * @deprecated use {@link #builder()} with {@link Builder#doclingClient(DoclingServeApi)} and a
     *             {@link Builder#documentRequest(DocumentRequest)} carrying a {@link ConvertDocumentRequest} with the
     *             desired {@link ConvertDocumentOptions} instead
     */
    @Deprecated(forRemoval = true)
    public DoclingDocumentParser(DoclingServeApi doclingClient, ConvertDocumentOptions options) {
        this(builder().doclingClient(doclingClient).documentRequest(convertRequest(options)));
    }

    private DoclingDocumentParser(Builder builder) {
        this.doclingClient = ValidationUtils.ensureNotNull(builder.doclingClient, "doclingClient");
        var request = getOrDefault(builder.documentRequest, DEFAULT_DOCUMENT_REQUEST);
        this.requestExecutor = getOrDefault(builder.requestExecutor, DEFAULT_REQUEST_EXECUTOR);

        // The default executor targets the in-body convert/chunk endpoints, so the request shape is validated
        // eagerly only when no custom executor takes over the invocation.
        if (builder.requestExecutor == null) {
            validateDefaultExecutorRequest(request);
        }

        this.documentRequest = request;
        this.documentExtractor = resolveDocumentExtractor(builder);
        this.chunkExtractor = resolveChunkExtractor(builder);
    }

    private static Function<InBodyConvertDocumentResponse, Document> resolveDocumentExtractor(Builder builder) {
        ensureMutuallyExclusive(
                builder.documentExtractor, builder.documentTextExtractor, "documentExtractor", "documentTextExtractor");

        return Optional.ofNullable(builder.documentExtractor)
                .orElseGet(() -> Optional.ofNullable(builder.documentTextExtractor)
                        .map(DoclingDocumentParser::wrapTextExtractor)
                        .orElse(DEFAULT_DOCUMENT_EXTRACTOR));
    }

    private static Function<ChunkDocumentResponse, Document> resolveChunkExtractor(Builder builder) {
        ensureMutuallyExclusive(
                builder.chunkExtractor, builder.chunkTextExtractor, "chunkExtractor", "chunkTextExtractor");

        return Optional.ofNullable(builder.chunkExtractor)
                .orElseGet(() -> Optional.ofNullable(builder.chunkTextExtractor)
                        .map(DoclingDocumentParser::wrapTextExtractor)
                        .orElse(DEFAULT_CHUNK_EXTRACTOR));
    }

    private static <R> Function<R, Document> wrapTextExtractor(Function<R, String> textExtractor) {
        return response -> textToDocument(textExtractor.apply(response));
    }

    private static void ensureMutuallyExclusive(
            Object documentVariant, Object textVariant, String documentName, String textName) {
        if ((documentVariant != null) && (textVariant != null)) {
            throw new IllegalArgumentException("Set at most one of %s(...) and %s(...) — they are mutually exclusive."
                    .formatted(documentName, textName));
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Document parse(InputStream inputStream) {
        try {
            return parseAsync(inputStream).toCompletableFuture().join();
        } catch (CompletionException e) {
            throw toParseException((e.getCause() != null) ? e.getCause() : e);
        } catch (RuntimeException e) {
            // parseAsync threw synchronously (or join() threw, e.g. CancellationException) — wrap consistently
            throw toParseException(e);
        }
    }

    private static RuntimeException toParseException(Throwable cause) {
        final RuntimeException result;
        if (cause instanceof BlankDocumentException blank) {
            result = blank;
        } else if (cause instanceof IllegalArgumentException illegalArgument) {
            result = illegalArgument;
        } else {
            result = new RuntimeException("Docling failed to parse document: " + cause.getMessage(), cause);
        }
        return result;
    }

    /**
     * Parses the given {@link InputStream} without blocking, returning a stage that completes with the resulting
     * {@link Document}. This is the asynchronous counterpart of {@link #parse(InputStream)} and is intended for reactive
     * or non-blocking pipelines.
     *
     * @param inputStream
     *            the document content to parse; the caller owns its lifecycle and this method does not close it
     *
     * @return a stage completing with the parsed {@link Document}
     */
    public CompletionStage<Document> parseAsync(InputStream inputStream) {
        // Offload the blocking read and request preparation so the caller thread is never blocked and any
        // failure (null input, empty document, request build) is delivered through the returned stage.
        return CompletableFuture.supplyAsync(() -> prepareRequest(inputStream))
                .thenCompose(prepared -> this.requestExecutor
                        .execute(this.doclingClient, prepared.request())
                        .thenApply(response -> buildDocument(response, prepared.sizeBytes())));
    }

    private PreparedRequest prepareRequest(InputStream inputStream) {
        ValidationUtils.ensureNotNull(inputStream, "inputStream");
        var documentBytes = readBytes(inputStream);

        if (documentBytes.length == 0) {
            throw new BlankDocumentException();
        }

        var request = this.documentRequest.toBuilder()
                .clearSources()
                .source(fileSource(documentBytes, DEFAULT_FILENAME))
                .build();

        return new PreparedRequest(request, documentBytes.length);
    }

    private Document buildDocument(ProcessedDocumentResponse response, int documentSizeBytes) {
        var document = extract(response);

        if (document == null) {
            throw new BlankDocumentException();
        }

        document.metadata().put(DOCUMENT_SIZE_BYTES, String.valueOf(documentSizeBytes));
        return document;
    }

    private Document extract(ProcessedDocumentResponse response) {
        if (response instanceof InBodyConvertDocumentResponse inBodyResponse) {
            if (!inBodyResponse.getErrors().isEmpty()) {
                var first = inBodyResponse.getErrors().get(0);
                log.warn(
                        "Docling reported {} error(s). First: [{}] {}",
                        inBodyResponse.getErrors().size(),
                        first.getComponentType(),
                        first.getErrorMessage());
            }
            return this.documentExtractor.apply(inBodyResponse);
        } else if (response instanceof ChunkDocumentResponse chunkResponse) {
            return this.chunkExtractor.apply(chunkResponse);
        }

        throw new IllegalStateException(
                "Unsupported response type: " + response.getClass().getName());
    }

    private static void validateDefaultExecutorRequest(DocumentRequest request) {
        if (!((request instanceof ConvertDocumentRequest)
                || (request instanceof HierarchicalChunkDocumentRequest)
                || (request instanceof HybridChunkDocumentRequest))) {
            throw new IllegalArgumentException("Unsupported request type: "
                    + request.getClass().getName()
                    + ". DoclingDocumentParser only supports ConvertDocumentRequest, "
                    + "HierarchicalChunkDocumentRequest, and HybridChunkDocumentRequest. "
                    + "Supply a custom DoclingRequestExecutor to use other Docling operations.");
        }

        Target target = request.getTarget();
        if ((target != null) && !(target instanceof InBodyTarget)) {
            throw new IllegalArgumentException(
                    "Only an in-body (IN_BODY) target is supported by the default DoclingRequestExecutor, but the "
                            + "request template carries a " + target.getClass().getName()
                            + ". Supply a custom DoclingRequestExecutor to handle other targets.");
        }
    }

    private static Document textToDocument(String text) {
        if ((text == null) || text.isBlank()) {
            throw new BlankDocumentException();
        }

        return Document.from(text);
    }

    private static byte[] readBytes(InputStream inputStream) {
        try {
            // Intentionally does not close the stream - the caller owns its lifecycle.
            return inputStream.readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read input stream: " + e.getMessage(), e);
        }
    }

    private static FileSource fileSource(byte[] documentBytes, String filename) {
        return FileSource.builder()
                .base64String(Base64.getEncoder().encodeToString(documentBytes))
                .filename(filename)
                .build();
    }

    private static ConvertDocumentRequest convertRequest(ConvertDocumentOptions options) {
        var requestBuilder = ConvertDocumentRequest.builder();

        if (options != null) {
            requestBuilder.options(options);
        }

        return requestBuilder.build();
    }

    public static final class Builder {
        private DoclingServeApi doclingClient;
        private DocumentRequest documentRequest;
        private Function<InBodyConvertDocumentResponse, Document> documentExtractor;
        private Function<InBodyConvertDocumentResponse, String> documentTextExtractor;
        private Function<ChunkDocumentResponse, Document> chunkExtractor;
        private Function<ChunkDocumentResponse, String> chunkTextExtractor;
        private DoclingRequestExecutor requestExecutor;

        private Builder() {}

        /**
         * Sets the Docling client that will be used to perform operations.
         *
         * @param doclingClient
         *            the {@link DoclingServeApi} instance to communicate with Docling backend services
         *
         * @return this builder instance
         */
        public Builder doclingClient(DoclingServeApi doclingClient) {
            this.doclingClient = doclingClient;
            return this;
        }

        /**
         * Sets the {@link DocumentRequest} template describing the Docling operation to perform (for example
         * {@link ConvertDocumentRequest}, {@link HierarchicalChunkDocumentRequest}, or
         * {@link HybridChunkDocumentRequest}) along with its options.
         * <p>
         * Any document source carried by the template is ignored — the parser injects the source of the document being
         * parsed into a fresh copy of the template for each call. With the default request executor the template must
         * describe an in-body operation: a {@link BatchConvertDocumentRequest} or a request carrying a
         * non-{@link InBodyTarget} target causes {@link #build()} to fail. Supplying a custom
         * {@link #requestExecutor(DoclingRequestExecutor)} lifts those restrictions.
         * <p>
         * If not set, defaults to a plain {@link ConvertDocumentRequest} with default options.
         *
         * @param documentRequest
         *            the request template
         *
         * @return this builder
         */
        public Builder documentRequest(DocumentRequest documentRequest) {
            this.documentRequest = documentRequest;
            return this;
        }

        /**
         * Sets a custom function to build a {@link Document} from a Docling {@link InBodyConvertDocumentResponse}
         * (produced by a {@link ConvertDocumentRequest}). Unlike {@link #documentTextExtractor(Function)}, this gives
         * full control over the returned {@link Document}, including its {@link Metadata} — useful for carrying, for
         * example, provenance information derived from the structured response. The parser adds the
         * {@value #DOCUMENT_SIZE_BYTES} metadata entry on top of whatever the function returns.
         * <p>
         * Mutually exclusive with {@link #documentTextExtractor(Function)}: configuring both causes {@link #build()} to
         * throw an {@link IllegalArgumentException}. If neither is set, the markdown content of the converted document is
         * used.
         *
         * @param documentExtractor
         *            the function to build a {@link Document} from a conversion response
         *
         * @return this builder
         */
        public Builder documentExtractor(Function<InBodyConvertDocumentResponse, Document> documentExtractor) {
            this.documentExtractor = documentExtractor;
            return this;
        }

        /**
         * Sets a custom function to extract text content from a Docling {@link InBodyConvertDocumentResponse} (produced
         * by a {@link ConvertDocumentRequest}). The function receives the full response, giving access to the converted
         * document, conversion errors, processing time, and status information. The returned text is wrapped in a
         * {@link Document}; for control over the {@link Document} itself use {@link #documentExtractor(Function)}.
         * <p>
         * Mutually exclusive with {@link #documentExtractor(Function)}: configuring both causes {@link #build()} to
         * throw an {@link IllegalArgumentException}. If not set, defaults to extracting the markdown content of the
         * converted document.
         *
         * @param documentTextExtractor
         *            the function to extract document text from a conversion response
         *
         * @return this builder
         */
        public Builder documentTextExtractor(Function<InBodyConvertDocumentResponse, String> documentTextExtractor) {
            this.documentTextExtractor = documentTextExtractor;
            return this;
        }

        /**
         * Sets a custom function to build a {@link Document} from a Docling {@link ChunkDocumentResponse} (produced by a
         * {@link HierarchicalChunkDocumentRequest} or {@link HybridChunkDocumentRequest}). Unlike
         * {@link #chunkTextExtractor(Function)}, this gives full control over the returned {@link Document}, including
         * its {@link Metadata}. The parser adds the {@value #DOCUMENT_SIZE_BYTES} metadata entry on top of whatever the
         * function returns.
         * <p>
         * Mutually exclusive with {@link #chunkTextExtractor(Function)}: configuring both causes {@link #build()} to
         * throw an {@link IllegalArgumentException}. If neither is set, the text of all chunks joined by newlines is
         * used.
         *
         * @param chunkExtractor
         *            the function to build a {@link Document} from a chunk response
         *
         * @return this builder
         */
        public Builder chunkExtractor(Function<ChunkDocumentResponse, Document> chunkExtractor) {
            this.chunkExtractor = chunkExtractor;
            return this;
        }

        /**
         * Sets a custom function to extract text content from a Docling {@link ChunkDocumentResponse} (produced by a
         * {@link HierarchicalChunkDocumentRequest} or {@link HybridChunkDocumentRequest}). The function receives the
         * full response, giving access to the chunks and their metadata. The returned text is wrapped in a
         * {@link Document}; for control over the {@link Document} itself use {@link #chunkExtractor(Function)}.
         * <p>
         * Mutually exclusive with {@link #chunkExtractor(Function)}: configuring both causes {@link #build()} to throw
         * an {@link IllegalArgumentException}. If not set, defaults to joining the text of all chunks separated by
         * newlines.
         *
         * @param chunkTextExtractor
         *            the function to extract document text from a chunk response
         *
         * @return this builder
         */
        public Builder chunkTextExtractor(Function<ChunkDocumentResponse, String> chunkTextExtractor) {
            this.chunkTextExtractor = chunkTextExtractor;
            return this;
        }

        /**
         * Sets the strategy used to invoke Docling for a prepared request. The default executor calls the asynchronous
         * convert/chunk endpoints matching the request type (which already submit, poll, and fetch the result on the
         * common {@link java.util.concurrent.ForkJoinPool}). Supplying a custom executor lets callers control exactly
         * how Docling is called (for example to add retry behaviour, or to run the blocking call on their own executor
         * instead of the common {@link java.util.concurrent.ForkJoinPool}) and lifts the request-type and target
         * restrictions enforced for the default executor.
         *
         * @param requestExecutor
         *            the executor that invokes Docling
         *
         * @return this builder
         */
        public Builder requestExecutor(DoclingRequestExecutor requestExecutor) {
            this.requestExecutor = requestExecutor;
            return this;
        }

        /**
         * Sets the conversion options.
         *
         * @param options
         *            the {@link ConvertDocumentOptions} to use
         *
         * @return this builder
         *
         * @deprecated use {@link Builder#documentRequest(DocumentRequest)} with a
         *             {@link ConvertDocumentRequest} carrying the desired options instead
         */
        @Deprecated
        public Builder options(ConvertDocumentOptions options) {
            return documentRequest(convertRequest(options));
        }

        public DoclingDocumentParser build() {
            return new DoclingDocumentParser(this);
        }
    }
}
