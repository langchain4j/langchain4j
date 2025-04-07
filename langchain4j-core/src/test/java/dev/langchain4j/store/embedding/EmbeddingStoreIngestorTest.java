package dev.langchain4j.store.embedding;

import static dev.langchain4j.data.segment.TextSegment.textSegment;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
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
}
