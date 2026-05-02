package dev.langchain4j.store.embedding;

import static dev.langchain4j.data.segment.TextSegment.textSegment;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.DocumentTransformer;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.data.segment.TextSegmentTransformer;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

class EmbeddingStoreIngestorTest {

    @Test
    void should_extract_text_then_split_into_segments_then_embed_them_and_store_in_embedding_store() {

        Document firstDocument = Document.from("First sentence.");
        Document secondDocument = Document.from("Second sentence. Third sentence.");
        Document thirdDocument = Document.from("Fourth sentence.");
        Document fourthDocument = Document.from("Fifth sentence.");
        Document fifthDocument = Document.from("Sixth Sentence");

        DocumentTransformer documentTransformer = mock(DocumentTransformer.class);
        when(documentTransformer.transformAll(singletonList(firstDocument))).thenReturn(singletonList(firstDocument));
        when(documentTransformer.transformAll(asList(secondDocument, thirdDocument)))
                .thenReturn(asList(secondDocument, thirdDocument));
        when(documentTransformer.transformAll(asList(fourthDocument, fifthDocument)))
                .thenReturn(asList(fourthDocument, fifthDocument));

        DocumentSplitter documentSplitter = mock(DocumentSplitter.class);
        when(documentSplitter.splitAll(singletonList(firstDocument)))
                .thenReturn(singletonList(textSegment("First sentence.")));
        when(documentSplitter.splitAll(asList(secondDocument, thirdDocument)))
                .thenReturn(asList(
                        textSegment("Second sentence."),
                        textSegment("Third sentence."),
                        textSegment("Fourth sentence.")));
        when(documentSplitter.splitAll(asList(fourthDocument, fifthDocument)))
                .thenReturn(asList(textSegment("Fifth sentence."), textSegment("Sixth sentence.")));

        TextSegmentTransformer textSegmentTransformer = mock(TextSegmentTransformer.class);
        when(textSegmentTransformer.transformAll(singletonList(textSegment("First sentence."))))
                .thenReturn(singletonList(textSegment("Transformed first sentence.")));
        when(textSegmentTransformer.transformAll(asList(
                        textSegment("Second sentence."),
                        textSegment("Third sentence."),
                        textSegment("Fourth sentence."))))
                .thenReturn(asList(
                        textSegment("Transformed second sentence."),
                        textSegment("Transformed third sentence."),
                        textSegment("Transformed fourth sentence.")));
        when(textSegmentTransformer.transformAll(
                        asList(textSegment("Fifth sentence."), textSegment("Sixth sentence."))))
                .thenReturn(
                        asList(textSegment("Transformed fifth sentence."), textSegment("Transformed sixth sentence.")));

        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        TokenUsage firstTokenUsage = new TokenUsage(1, 2, 3);
        TokenUsage secondTokenUsage = new TokenUsage(3, 5, 8);
        TokenUsage thirdTokenUsage = new TokenUsage(8, 10, 12);

        when(embeddingModel.embedAll(singletonList(textSegment("Transformed first sentence."))))
                .thenReturn(Response.from(singletonList(Embedding.from(new float[] {1})), firstTokenUsage));
        when(embeddingModel.embedAll(asList(
                        textSegment("Transformed second sentence."),
                        textSegment("Transformed third sentence."),
                        textSegment("Transformed fourth sentence."))))
                .thenReturn(Response.from(
                        asList(
                                Embedding.from(new float[] {2}),
                                Embedding.from(new float[] {3}),
                                Embedding.from(new float[] {4})),
                        secondTokenUsage));
        when(embeddingModel.embedAll(
                        asList(textSegment("Transformed fifth sentence."), textSegment("Transformed sixth sentence."))))
                .thenReturn(Response.from(
                        asList(Embedding.from(new float[] {5}), Embedding.from(new float[] {6})), thirdTokenUsage));

        @SuppressWarnings("unchecked")
        EmbeddingStore<TextSegment> embeddingStore = mock(EmbeddingStore.class);

        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .documentTransformer(documentTransformer)
                .documentSplitter(documentSplitter)
                .textSegmentTransformer(textSegmentTransformer)
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();

        // Method overloads.
        IngestionResult ingestionResult1 = ingestor.ingest(firstDocument);
        IngestionResult ingestionResult2 = ingestor.ingest(secondDocument, thirdDocument);
        IngestionResult ingestionResult3 = ingestor.ingest(asList(fourthDocument, fifthDocument));

        // Assertions.
        assertThat(ingestionResult1.tokenUsage()).isEqualTo(firstTokenUsage);
        assertThat(ingestionResult2.tokenUsage()).isEqualTo(secondTokenUsage);
        assertThat(ingestionResult3.tokenUsage()).isEqualTo(thirdTokenUsage);

        verify(documentTransformer).transformAll(singletonList(firstDocument));
        verify(documentTransformer).transformAll(asList(secondDocument, thirdDocument));
        verify(documentTransformer).transformAll(asList(fourthDocument, fifthDocument));
        verifyNoMoreInteractions(documentTransformer);

        verify(documentSplitter).splitAll(singletonList(firstDocument));
        verify(documentSplitter).splitAll(asList(secondDocument, thirdDocument));
        verify(documentSplitter).splitAll(asList(fourthDocument, fifthDocument));
        verifyNoMoreInteractions(documentSplitter);

        verify(textSegmentTransformer).transformAll(singletonList(textSegment("First sentence.")));
        verify(textSegmentTransformer)
                .transformAll(asList(
                        textSegment("Second sentence."),
                        textSegment("Third sentence."),
                        textSegment("Fourth sentence.")));
        verify(textSegmentTransformer)
                .transformAll(asList(textSegment("Fifth sentence."), textSegment("Sixth sentence.")));
        verifyNoMoreInteractions(textSegmentTransformer);

        verify(embeddingModel).embedAll(singletonList(textSegment("Transformed first sentence.")));
        verify(embeddingModel)
                .embedAll(asList(
                        textSegment("Transformed second sentence."),
                        textSegment("Transformed third sentence."),
                        textSegment("Transformed fourth sentence.")));
        verify(embeddingModel)
                .embedAll(
                        asList(textSegment("Transformed fifth sentence."), textSegment("Transformed sixth sentence.")));
        verifyNoMoreInteractions(embeddingModel);

        verify(embeddingStore)
                .addAll(
                        singletonList(new Embedding(new float[] {1})),
                        singletonList(textSegment("Transformed first sentence.")));
        verify(embeddingStore)
                .addAll(
                        asList(
                                new Embedding(new float[] {2}),
                                new Embedding(new float[] {3}),
                                new Embedding(new float[] {4})),
                        asList(
                                textSegment("Transformed second sentence."),
                                textSegment("Transformed third sentence."),
                                textSegment("Transformed fourth sentence.")));
        verify(embeddingStore)
                .addAll(
                        asList(new Embedding(new float[] {5}), new Embedding(new float[] {6})),
                        asList(textSegment("Transformed fifth sentence."), textSegment("Transformed sixth sentence.")));
        verifyNoMoreInteractions(embeddingStore);
    }

    @Test
    void should_not_split_when_no_splitter_is_specified() {

        // given
        String text = "Some text";
        Document document = Document.from(text);

        TextSegment expectedTextSegment = TextSegment.from(text, Metadata.from("index", "0"));
        TokenUsage tokenUsage = new TokenUsage(1, 2, 3);

        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        when(embeddingModel.embedAll(singletonList(expectedTextSegment)))
                .thenReturn(Response.from(singletonList(Embedding.from(new float[] {1})), tokenUsage));

        EmbeddingStore<TextSegment> embeddingStore = mock(EmbeddingStore.class);

        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();

        // when
        IngestionResult ingestionResult = ingestor.ingest(document);

        // then
        verify(embeddingStore)
                .addAll(singletonList(Embedding.from(new float[] {1})), singletonList(expectedTextSegment));
        verifyNoMoreInteractions(embeddingStore);

        assertThat(ingestionResult.tokenUsage()).isEqualTo(tokenUsage);
    }

    @Test
    void should_ignore_empty_segments_when_ignore_errors_is_enabled() {

        // given
        Document document = Document.from("Malformed document");

        DocumentSplitter documentSplitter = mock(DocumentSplitter.class);
        when(documentSplitter.splitAll(singletonList(document))).thenReturn(emptyList());

        TextSegmentTransformer textSegmentTransformer = mock(TextSegmentTransformer.class);

        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);

        EmbeddingStore<TextSegment> embeddingStore = mock(EmbeddingStore.class);

        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(documentSplitter)
                .textSegmentTransformer(textSegmentTransformer)
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .ignoreErrors(true)
                .build();

        // when
        IngestionResult ingestionResult = ingestor.ingest(document);

        // then
        assertThat(ingestionResult.tokenUsage()).isNull();

        verify(documentSplitter).splitAll(singletonList(document));
        verifyNoMoreInteractions(textSegmentTransformer);
        verify(embeddingModel, never()).embedAll(emptyList());
        verifyNoMoreInteractions(embeddingModel);
        verifyNoMoreInteractions(embeddingStore);
    }

    @Test
    void should_ignore_empty_transformed_segments_when_ignore_errors_is_enabled() {

        // given
        Document document = Document.from("Malformed document");
        TextSegment segment = textSegment("Malformed document");

        DocumentSplitter documentSplitter = mock(DocumentSplitter.class);
        when(documentSplitter.splitAll(singletonList(document))).thenReturn(singletonList(segment));

        TextSegmentTransformer textSegmentTransformer = mock(TextSegmentTransformer.class);
        when(textSegmentTransformer.transformAll(singletonList(segment))).thenReturn(emptyList());

        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);

        EmbeddingStore<TextSegment> embeddingStore = mock(EmbeddingStore.class);

        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(documentSplitter)
                .textSegmentTransformer(textSegmentTransformer)
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .ignoreErrors(true)
                .build();

        // when
        IngestionResult ingestionResult = ingestor.ingest(document);

        // then
        assertThat(ingestionResult.tokenUsage()).isNull();

        verify(documentSplitter).splitAll(singletonList(document));
        verify(textSegmentTransformer).transformAll(singletonList(segment));
        verifyNoMoreInteractions(textSegmentTransformer);
        verify(embeddingModel, never()).embedAll(emptyList());
        verifyNoMoreInteractions(embeddingModel);
        verifyNoMoreInteractions(embeddingStore);
    }

    @Test
    void should_ingest_non_empty_segments_when_ignore_errors_is_enabled() {

        // given
        Document malformedDocument = Document.from("Malformed document");
        Document firstValidDocument = Document.from("First valid document");
        Document secondValidDocument = Document.from("Second valid document");
        TextSegment firstValidSegment = textSegment("First valid document");
        TextSegment secondValidSegment = textSegment("Second valid document");
        TokenUsage firstTokenUsage = new TokenUsage(1, 2, 3);
        TokenUsage secondTokenUsage = new TokenUsage(3, 5, 8);

        DocumentSplitter documentSplitter = document -> {
            if (document == malformedDocument) {
                return emptyList();
            }
            if (document == firstValidDocument) {
                return singletonList(firstValidSegment);
            }
            return singletonList(secondValidSegment);
        };

        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        when(embeddingModel.embedAll(singletonList(firstValidSegment)))
                .thenReturn(Response.from(singletonList(Embedding.from(new float[] {1})), firstTokenUsage));
        when(embeddingModel.embedAll(singletonList(secondValidSegment)))
                .thenReturn(Response.from(singletonList(Embedding.from(new float[] {2})), secondTokenUsage));

        EmbeddingStore<TextSegment> embeddingStore = mock(EmbeddingStore.class);

        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(documentSplitter)
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .ignoreErrors(true)
                .build();

        // when
        IngestionResult ingestionResult =
                ingestor.ingest(asList(malformedDocument, firstValidDocument, secondValidDocument));

        // then
        assertThat(ingestionResult.tokenUsage()).isEqualTo(new TokenUsage(4, 7, 11));

        verify(embeddingModel).embedAll(singletonList(firstValidSegment));
        verify(embeddingModel).embedAll(singletonList(secondValidSegment));
        verifyNoMoreInteractions(embeddingModel);
        verify(embeddingStore).addAll(singletonList(Embedding.from(new float[] {1})), singletonList(firstValidSegment));
        verify(embeddingStore)
                .addAll(singletonList(Embedding.from(new float[] {2})), singletonList(secondValidSegment));
        verifyNoMoreInteractions(embeddingStore);
    }

    @Test
    void should_continue_ingesting_when_ignore_errors_is_enabled() {

        // given
        Document malformedDocument = Document.from("Malformed document");
        Document validDocument = Document.from("Valid document");
        TextSegment validSegment = textSegment("Valid document");
        TokenUsage tokenUsage = new TokenUsage(1, 2, 3);

        DocumentSplitter documentSplitter = document -> {
            if (document == malformedDocument) {
                throw new RuntimeException("Cannot split document");
            }
            return singletonList(validSegment);
        };

        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        when(embeddingModel.embedAll(singletonList(validSegment)))
                .thenReturn(Response.from(singletonList(Embedding.from(new float[] {1})), tokenUsage));

        EmbeddingStore<TextSegment> embeddingStore = mock(EmbeddingStore.class);

        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(documentSplitter)
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .ignoreErrors(true)
                .build();

        // when
        IngestionResult ingestionResult = ingestor.ingest(asList(malformedDocument, validDocument));

        // then
        assertThat(ingestionResult.tokenUsage()).isEqualTo(tokenUsage);

        verify(embeddingModel).embedAll(singletonList(validSegment));
        verifyNoMoreInteractions(embeddingModel);
        verify(embeddingStore).addAll(singletonList(Embedding.from(new float[] {1})), singletonList(validSegment));
        verifyNoMoreInteractions(embeddingStore);
    }

    @Test
    void should_continue_ingesting_when_embedding_fails_and_ignore_errors_is_enabled() {

        // given
        Document malformedDocument = Document.from("Malformed document");
        Document validDocument = Document.from("Valid document");
        TextSegment malformedSegment = textSegment("Malformed document");
        TextSegment validSegment = textSegment("Valid document");
        TokenUsage tokenUsage = new TokenUsage(1, 2, 3);

        DocumentSplitter documentSplitter = document -> {
            if (document == malformedDocument) {
                return singletonList(malformedSegment);
            }
            return singletonList(validSegment);
        };

        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        when(embeddingModel.embedAll(singletonList(malformedSegment))).thenThrow(new RuntimeException("Cannot embed"));
        when(embeddingModel.embedAll(singletonList(validSegment)))
                .thenReturn(Response.from(singletonList(Embedding.from(new float[] {1})), tokenUsage));

        EmbeddingStore<TextSegment> embeddingStore = mock(EmbeddingStore.class);

        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(documentSplitter)
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .ignoreErrors(true)
                .build();

        // when
        IngestionResult ingestionResult = ingestor.ingest(asList(malformedDocument, validDocument));

        // then
        assertThat(ingestionResult.tokenUsage()).isEqualTo(tokenUsage);

        verify(embeddingModel).embedAll(singletonList(malformedSegment));
        verify(embeddingModel).embedAll(singletonList(validSegment));
        verifyNoMoreInteractions(embeddingModel);
        verify(embeddingStore).addAll(singletonList(Embedding.from(new float[] {1})), singletonList(validSegment));
        verifyNoMoreInteractions(embeddingStore);
    }

    @Test
    void should_not_skip_empty_segments_by_default() {

        // given
        Document document = Document.from("Malformed document");

        DocumentSplitter documentSplitter = mock(DocumentSplitter.class);
        when(documentSplitter.splitAll(singletonList(document))).thenReturn(emptyList());

        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        RuntimeException exception = new RuntimeException("Empty input");
        when(embeddingModel.embedAll(emptyList())).thenThrow(exception);

        EmbeddingStore<TextSegment> embeddingStore = mock(EmbeddingStore.class);

        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(documentSplitter)
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();

        // when/then
        assertThatThrownBy(() -> ingestor.ingest(document)).isSameAs(exception);

        verify(documentSplitter).splitAll(singletonList(document));
        verify(embeddingModel).embedAll(emptyList());
        verifyNoMoreInteractions(embeddingModel);
        verifyNoMoreInteractions(embeddingStore);
    }
}
