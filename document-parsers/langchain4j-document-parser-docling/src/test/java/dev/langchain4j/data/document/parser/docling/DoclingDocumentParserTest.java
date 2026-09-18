package dev.langchain4j.data.document.parser.docling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.docling.serve.api.DoclingServeApi;
import ai.docling.serve.api.chunk.request.HierarchicalChunkDocumentRequest;
import ai.docling.serve.api.chunk.request.HybridChunkDocumentRequest;
import ai.docling.serve.api.chunk.response.Chunk;
import ai.docling.serve.api.chunk.response.ChunkDocumentResponse;
import ai.docling.serve.api.convert.request.BatchConvertDocumentRequest;
import ai.docling.serve.api.convert.request.ConvertDocumentRequest;
import ai.docling.serve.api.convert.request.options.ConvertDocumentOptions;
import ai.docling.serve.api.convert.request.source.FileSource;
import ai.docling.serve.api.convert.request.target.PresignedUrlTarget;
import ai.docling.serve.api.convert.response.DocumentResponse;
import ai.docling.serve.api.convert.response.InBodyConvertDocumentResponse;
import dev.langchain4j.data.document.BlankDocumentException;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.Metadata;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DoclingDocumentParserTest {

    @Mock
    private DoclingServeApi mockApi;

    @Test
    void shouldThrowWhenInputStreamIsNull() {
        DoclingDocumentParser parser = new DoclingDocumentParser(mockApi);

        assertThatThrownBy(() -> parser.parse(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null");
    }

    @Test
    void shouldThrowWhenInputStreamIsEmpty() {
        DoclingDocumentParser parser = new DoclingDocumentParser(mockApi);

        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(new byte[0])))
                .isInstanceOf(BlankDocumentException.class);
    }

    @Test
    void shouldThrowWhenApiInstanceIsNull() {
        assertThatThrownBy(() -> new DoclingDocumentParser(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldReturnDocumentWithParsedText() {
        mockResponseWithMarkdown("# Parsed Content");

        DoclingDocumentParser parser = new DoclingDocumentParser(mockApi);
        Document document = parser.parse(new ByteArrayInputStream("some bytes".getBytes()));

        assertThat(document.text()).isEqualTo("# Parsed Content");
    }

    @Test
    void shouldIncludeDocumentSizeBytesInMetadata() {
        byte[] content = "document content".getBytes();
        mockResponseWithMarkdown("Parsed text");

        DoclingDocumentParser parser = new DoclingDocumentParser(mockApi);
        Document document = parser.parse(new ByteArrayInputStream(content));

        assertThat(document.metadata().getString("document_size_bytes")).isEqualTo(String.valueOf(content.length));
    }

    @Test
    void shouldHandleEmptyDocumentWhenApiReturnsEmptyContent() {
        mockResponseWithMarkdown("");

        assertThatThrownBy(() -> new DoclingDocumentParser(mockApi).parse(new ByteArrayInputStream("data".getBytes())))
                .isInstanceOf(BlankDocumentException.class);
    }

    @Test
    void shouldImplementDocumentParserInterface() {
        assertThat(new DoclingDocumentParser(mockApi)).isInstanceOf(DocumentParser.class);
    }

    @Test
    void shouldReturnDocumentWithParsedText_usingBuilder() {
        mockResponseWithMarkdown("# Parsed Content");

        var parser = DoclingDocumentParser.builder().doclingClient(mockApi).build();
        var document = parser.parse(new ByteArrayInputStream("some bytes".getBytes()));

        assertThat(document.text()).isEqualTo("# Parsed Content");
    }

    @Test
    void shouldThrowWhenDoclingClientIsNull_usingBuilder() {
        assertThatThrownBy(() -> DoclingDocumentParser.builder().build()).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldUseCustomDocumentTextExtractor() {
        mockResponseWith("# Markdown", "<h1>HTML</h1>", null, null);

        var parser = DoclingDocumentParser.builder()
                .doclingClient(mockApi)
                .documentTextExtractor(response -> response.getDocument().getHtmlContent())
                .build();
        var document = parser.parse(new ByteArrayInputStream("some bytes".getBytes()));

        assertThat(document.text()).isEqualTo("<h1>HTML</h1>");
    }

    @Test
    void shouldUseDefaultMarkdownExtractorWhenNoneSpecified() {
        mockResponseWith("# Markdown", "<h1>HTML</h1>", "Plain text", null);

        var parser = DoclingDocumentParser.builder().doclingClient(mockApi).build();
        var document = parser.parse(new ByteArrayInputStream("some bytes".getBytes()));

        assertThat(document.text()).isEqualTo("# Markdown");
    }

    @Test
    void shouldThrowBlankDocumentExceptionWhenExtractorReturnsNull() {
        mockResponseWithMarkdown("# Markdown");

        var parser = DoclingDocumentParser.builder()
                .doclingClient(mockApi)
                .documentTextExtractor(response -> null)
                .build();

        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream("data".getBytes())))
                .isInstanceOf(BlankDocumentException.class);
    }

    @Test
    void shouldThrowBlankDocumentExceptionWhenExtractorReturnsBlank() {
        mockResponseWithMarkdown("# Markdown");

        var parser = DoclingDocumentParser.builder()
                .doclingClient(mockApi)
                .documentTextExtractor(response -> "   ")
                .build();

        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream("data".getBytes())))
                .isInstanceOf(BlankDocumentException.class);
    }

    @Test
    void shouldAllowAccessToFullResponse() {
        when(mockApi.convertSourceAsync(any(ConvertDocumentRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(InBodyConvertDocumentResponse.builder()
                        .document(DocumentResponse.builder()
                                .markdownContent("content")
                                .build())
                        .status("SUCCESS")
                        .processingTime(1.5)
                        .build()));

        var parser = DoclingDocumentParser.builder()
                .doclingClient(mockApi)
                .documentTextExtractor(response -> "%s (status=%s, time=%.1f)"
                        .formatted(
                                response.getDocument().getMarkdownContent(),
                                response.getStatus(),
                                response.getProcessingTime()))
                .build();
        var document = parser.parse(new ByteArrayInputStream("data".getBytes()));

        assertThat(document.text()).isEqualTo("content (status=SUCCESS, time=1.5)");
    }

    @Test
    void shouldBuildWithConvertRequestOptions() {
        mockResponseWithMarkdown("# Content");

        var options = ConvertDocumentOptions.builder().build();
        var parser = DoclingDocumentParser.builder()
                .doclingClient(mockApi)
                .documentRequest(
                        ConvertDocumentRequest.builder().options(options).build())
                .build();
        var document = parser.parse(new ByteArrayInputStream("data".getBytes()));

        assertThat(document.text()).isEqualTo("# Content");
    }

    @Test
    void shouldBuildWithDeprecatedOptionsDelegate() {
        mockResponseWithMarkdown("# Content");

        var options = ConvertDocumentOptions.builder().build();
        var parser = DoclingDocumentParser.builder()
                .doclingClient(mockApi)
                .options(options)
                .build();
        var document = parser.parse(new ByteArrayInputStream("data".getBytes()));

        assertThat(document.text()).isEqualTo("# Content");
    }

    @Test
    void shouldRouteHierarchicalChunkRequestToChunkEndpoint() {
        when(mockApi.chunkSourceWithHierarchicalChunkerAsync(any(HierarchicalChunkDocumentRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(ChunkDocumentResponse.builder()
                        .chunk(Chunk.builder().text("first").build())
                        .chunk(Chunk.builder().text("second").build())
                        .build()));

        var parser = DoclingDocumentParser.builder()
                .doclingClient(mockApi)
                .documentRequest(HierarchicalChunkDocumentRequest.builder().build())
                .build();
        var document = parser.parse(new ByteArrayInputStream("data".getBytes()));

        assertThat(document.text()).isEqualTo("first\nsecond");
        verify(mockApi, never()).convertSourceAsync(any());
        verify(mockApi, never()).chunkSourceWithHybridChunkerAsync(any());
    }

    @Test
    void shouldRouteHybridChunkRequestToChunkEndpoint() {
        when(mockApi.chunkSourceWithHybridChunkerAsync(any(HybridChunkDocumentRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(ChunkDocumentResponse.builder()
                        .chunk(Chunk.builder().text("only").build())
                        .build()));

        var parser = DoclingDocumentParser.builder()
                .doclingClient(mockApi)
                .documentRequest(HybridChunkDocumentRequest.builder().build())
                .build();
        var document = parser.parse(new ByteArrayInputStream("data".getBytes()));

        assertThat(document.text()).isEqualTo("only");
        verify(mockApi, never()).convertSourceAsync(any());
    }

    @Test
    void shouldUseCustomChunkTextExtractor() {
        when(mockApi.chunkSourceWithHybridChunkerAsync(any(HybridChunkDocumentRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(ChunkDocumentResponse.builder()
                        .chunk(Chunk.builder().text("alpha").build())
                        .chunk(Chunk.builder().text("beta").build())
                        .build()));

        var parser = DoclingDocumentParser.builder()
                .doclingClient(mockApi)
                .documentRequest(HybridChunkDocumentRequest.builder().build())
                .chunkTextExtractor(response -> response.getChunks().stream()
                        .map(Chunk::getText)
                        .reduce((a, b) -> a + " | " + b)
                        .orElse(""))
                .build();
        var document = parser.parse(new ByteArrayInputStream("data".getBytes()));

        assertThat(document.text()).isEqualTo("alpha | beta");
    }

    @Test
    void shouldThrowWhenBatchConvertRequestIsUsed() {
        var batchRequest = BatchConvertDocumentRequest.builder()
                .target(PresignedUrlTarget.builder().build())
                .build();

        assertThatThrownBy(() -> DoclingDocumentParser.builder()
                        .doclingClient(mockApi)
                        .documentRequest(batchRequest)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Batch");
    }

    @Test
    void shouldThrowWhenConvertRequestCarriesNonInBodyTarget() {
        var request = ConvertDocumentRequest.builder()
                .target(PresignedUrlTarget.builder().build())
                .build();

        assertThatThrownBy(() -> DoclingDocumentParser.builder()
                        .doclingClient(mockApi)
                        .documentRequest(request)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("in-body");
    }

    @Test
    void shouldIgnoreSourceCarriedByTemplate() {
        mockResponseWithMarkdown("# Content");
        var template = ConvertDocumentRequest.builder()
                .source(FileSource.builder()
                        .base64String("aWdub3JlZA==")
                        .filename("ignored.pdf")
                        .build())
                .build();

        var parser = DoclingDocumentParser.builder()
                .doclingClient(mockApi)
                .documentRequest(template)
                .build();
        parser.parse(new ByteArrayInputStream("data".getBytes()));

        var request = captureConvertRequest();
        assertThat(request.getSources())
                .singleElement()
                .isInstanceOfSatisfying(
                        FileSource.class,
                        source -> assertThat(source.getFilename()).isEqualTo("document"));
    }

    @Test
    void shouldParseAsync() {
        mockResponseWithMarkdown("# Async");

        var parser = DoclingDocumentParser.builder().doclingClient(mockApi).build();
        var stage = parser.parseAsync(new ByteArrayInputStream("data".getBytes()));

        assertThat(stage.toCompletableFuture())
                .succeedsWithin(Duration.ofSeconds(5))
                .extracting(Document::text)
                .isEqualTo("# Async");
    }

    @Test
    void shouldReturnFailedStageForNullInput() {
        var parser = DoclingDocumentParser.builder().doclingClient(mockApi).build();

        // parseAsync must not throw synchronously — the failure is delivered through the returned stage.
        assertThat(parser.parseAsync(null).toCompletableFuture())
                .failsWithin(Duration.ofSeconds(5))
                .withThrowableOfType(ExecutionException.class)
                .withCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldReturnFailedStageForEmptyInput() {
        var parser = DoclingDocumentParser.builder().doclingClient(mockApi).build();

        assertThat(parser.parseAsync(new ByteArrayInputStream(new byte[0])).toCompletableFuture())
                .failsWithin(Duration.ofSeconds(5))
                .withThrowableOfType(ExecutionException.class)
                .withCauseInstanceOf(BlankDocumentException.class);
    }

    @Test
    void shouldReturnFailedStageWhenExecutorFails() {
        var parser = DoclingDocumentParser.builder()
                .doclingClient(mockApi)
                .requestExecutor((client, request) -> CompletableFuture.failedFuture(new RuntimeException("boom")))
                .build();

        assertThat(parser.parseAsync(new ByteArrayInputStream("data".getBytes()))
                        .toCompletableFuture())
                .failsWithin(Duration.ofSeconds(5))
                .withThrowableOfType(ExecutionException.class)
                .withCauseInstanceOf(RuntimeException.class)
                .withMessageContaining("boom");

        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream("data".getBytes())))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Docling failed to parse document")
                .cause()
                .hasMessage("boom");
    }

    @Test
    void shouldUseCustomDocumentExtractor() {
        mockResponseWithMarkdown("# Markdown");

        var parser = DoclingDocumentParser.builder()
                .doclingClient(mockApi)
                .documentExtractor(response ->
                        Document.from(response.getDocument().getMarkdownContent(), Metadata.from("provenance", "0-9")))
                .build();
        var document = parser.parse(new ByteArrayInputStream("data".getBytes()));

        assertThat(document.text()).isEqualTo("# Markdown");
        assertThat(document.metadata().getString("provenance")).isEqualTo("0-9");
        assertThat(document.metadata().getString("document_size_bytes")).isNotNull();
    }

    @Test
    void shouldUseCustomChunkExtractor() {
        when(mockApi.chunkSourceWithHybridChunkerAsync(any(HybridChunkDocumentRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(ChunkDocumentResponse.builder()
                        .chunk(Chunk.builder().text("a").build())
                        .chunk(Chunk.builder().text("b").build())
                        .build()));

        var parser = DoclingDocumentParser.builder()
                .doclingClient(mockApi)
                .documentRequest(HybridChunkDocumentRequest.builder().build())
                .chunkExtractor(response ->
                        Document.from("count=" + response.getChunks().size(), Metadata.from("kind", "chunks")))
                .build();
        var document = parser.parse(new ByteArrayInputStream("data".getBytes()));

        assertThat(document.text()).isEqualTo("count=2");
        assertThat(document.metadata().getString("kind")).isEqualTo("chunks");
    }

    @Test
    void shouldFailWhenBothDocumentExtractorVariantsAreConfigured() {
        assertThatThrownBy(() -> DoclingDocumentParser.builder()
                        .doclingClient(mockApi)
                        .documentTextExtractor(
                                response -> response.getDocument().getMarkdownContent())
                        .documentExtractor(response -> Document.from("ignored"))
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("documentExtractor")
                .hasMessageContaining("documentTextExtractor");
    }

    @Test
    void shouldFailWhenBothChunkExtractorVariantsAreConfigured() {
        assertThatThrownBy(() -> DoclingDocumentParser.builder()
                        .doclingClient(mockApi)
                        .documentRequest(HybridChunkDocumentRequest.builder().build())
                        .chunkTextExtractor(response -> "text")
                        .chunkExtractor(response -> Document.from("ignored"))
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chunkExtractor")
                .hasMessageContaining("chunkTextExtractor");
    }

    @Test
    void shouldUseCustomRequestExecutor() {
        var response = InBodyConvertDocumentResponse.builder()
                .document(DocumentResponse.builder()
                        .markdownContent("# From executor")
                        .build())
                .build();

        var parser = DoclingDocumentParser.builder()
                .doclingClient(mockApi)
                .requestExecutor((client, request) -> CompletableFuture.completedFuture(response))
                .build();
        var document = parser.parse(new ByteArrayInputStream("data".getBytes()));

        assertThat(document.text()).isEqualTo("# From executor");
        verify(mockApi, never()).convertSourceAsync(any());
    }

    @Test
    void shouldAllowUnsupportedRequestTypeWithCustomExecutor() {
        var response = InBodyConvertDocumentResponse.builder()
                .document(DocumentResponse.builder().markdownContent("# Batch").build())
                .build();
        var batchRequest = BatchConvertDocumentRequest.builder()
                .target(PresignedUrlTarget.builder().build())
                .build();

        var parser = DoclingDocumentParser.builder()
                .doclingClient(mockApi)
                .documentRequest(batchRequest)
                .requestExecutor((client, request) -> CompletableFuture.completedFuture(response))
                .build();
        var document = parser.parse(new ByteArrayInputStream("data".getBytes()));

        assertThat(document.text()).isEqualTo("# Batch");
    }

    private ConvertDocumentRequest captureConvertRequest() {
        var captor = ArgumentCaptor.forClass(ConvertDocumentRequest.class);
        verify(mockApi).convertSourceAsync(captor.capture());
        return captor.getValue();
    }

    private void mockResponseWithMarkdown(String markdown) {
        mockResponseWith(markdown, null, null, null);
    }

    private void mockResponseWith(String markdown, String html, String text, String doctags) {
        when(mockApi.convertSourceAsync(any(ConvertDocumentRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(InBodyConvertDocumentResponse.builder()
                        .document(DocumentResponse.builder()
                                .markdownContent(markdown)
                                .htmlContent(html)
                                .textContent(text)
                                .doctagsContent(doctags)
                                .build())
                        .build()));
    }
}
