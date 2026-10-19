package dev.langchain4j.store.embedding;

import static dev.langchain4j.data.segment.TextSegment.textSegment;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import dev.langchain4j.data.segment.EmbeddingInputMapper;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.data.segment.TextSegmentTransformer;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.request.EmbeddingInputType;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
    void should_embed_documents_with_input_type_when_configured() {

        // given
        Document document = Document.from("hello world");
        TextSegment segment = textSegment("hello world");

        DocumentSplitter documentSplitter = mock(DocumentSplitter.class);
        when(documentSplitter.splitAll(singletonList(document))).thenReturn(singletonList(segment));

        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        when(embeddingModel.embed(any(EmbeddingRequest.class)))
                .thenReturn(EmbeddingResponse.builder()
                        .embeddings(singletonList(Embedding.from(new float[] {1})))
                        .build());

        EmbeddingStore<TextSegment> embeddingStore = mock(EmbeddingStore.class);

        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(documentSplitter)
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .embeddingInputType(EmbeddingInputType.DOCUMENT)
                .build();

        // when
        ingestor.ingest(document);

        // then: documents were embedded via the new API with input_type=DOCUMENT, not via legacy embedAll
        ArgumentCaptor<EmbeddingRequest> captor = ArgumentCaptor.forClass(EmbeddingRequest.class);
        verify(embeddingModel).embed(captor.capture());
        assertThat(captor.getValue().inputType()).isEqualTo(EmbeddingInputType.DOCUMENT);
        verify(embeddingModel, never()).embedAll(any());
    }

    @Test
    void should_support_single_mapped_segment() {

        // given
        Document document = Document.from("some code");
        TextSegment original = textSegment("original");
        TextSegment mapped = textSegment("mapped original");

        DocumentSplitter documentSplitter = mock(DocumentSplitter.class);
        when(documentSplitter.splitAll(singletonList(document))).thenReturn(singletonList(original));

        EmbeddingInputMapper mapping =
                segment -> singletonList(TextSegment.from("mapped " + segment.text(), segment.metadata()));

        Embedding embedding = Embedding.from(new float[] {1});
        TokenUsage tokenUsage = new TokenUsage(1, 2, 3);

        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        when(embeddingModel.embedAll(singletonList(mapped)))
                .thenReturn(Response.from(singletonList(embedding), tokenUsage));

        @SuppressWarnings("unchecked")
        EmbeddingStore<TextSegment> embeddingStore = mock(EmbeddingStore.class);

        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(documentSplitter)
                .embeddingInputMapper(mapping)
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();

        // when
        IngestionResult ingestionResult = ingestor.ingest(document);

        // then
        assertThat(ingestionResult.tokenUsage()).isEqualTo(tokenUsage);
        verify(embeddingModel).embedAll(singletonList(mapped));
        verify(embeddingStore).addAll(singletonList(embedding), singletonList(original));
        verifyNoMoreInteractions(embeddingModel, embeddingStore);
    }

    @Test
    void should_filter_out_segment_when_mapping_returns_empty_list() {

        // given
        Document document = Document.from("some code");
        TextSegment kept = textSegment("kept");
        TextSegment filteredOut = textSegment("filtered out");
        TextSegment mappedKept = textSegment("mapped kept");

        DocumentSplitter documentSplitter = mock(DocumentSplitter.class);
        when(documentSplitter.splitAll(singletonList(document))).thenReturn(asList(kept, filteredOut));

        EmbeddingInputMapper mapping = segment -> "kept".equals(segment.text()) ? singletonList(mappedKept) : List.of();

        Embedding embedding = Embedding.from(new float[] {1});
        TokenUsage tokenUsage = new TokenUsage(1, 2, 3);

        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        when(embeddingModel.embedAll(singletonList(mappedKept)))
                .thenReturn(Response.from(singletonList(embedding), tokenUsage));

        @SuppressWarnings("unchecked")
        EmbeddingStore<TextSegment> embeddingStore = mock(EmbeddingStore.class);

        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(documentSplitter)
                .embeddingInputMapper(mapping)
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();

        // when
        IngestionResult ingestionResult = ingestor.ingest(document);

        // then
        assertThat(ingestionResult.tokenUsage()).isEqualTo(tokenUsage);
        verify(embeddingModel).embedAll(singletonList(mappedKept));
        verify(embeddingStore).addAll(singletonList(embedding), singletonList(kept));
        verifyNoMoreInteractions(embeddingModel, embeddingStore);
    }

    @Test
    void should_apply_mapping_after_text_segment_transformer() {

        // given
        Document document = Document.from("some code");
        TextSegment splitSegment = textSegment("split");
        TextSegment transformedSegment = textSegment("transformed");
        TextSegment mappedSegment = textSegment("mapped transformed");

        DocumentSplitter documentSplitter = mock(DocumentSplitter.class);
        when(documentSplitter.splitAll(singletonList(document))).thenReturn(singletonList(splitSegment));

        TextSegmentTransformer textSegmentTransformer = mock(TextSegmentTransformer.class);
        when(textSegmentTransformer.transformAll(singletonList(splitSegment)))
                .thenReturn(singletonList(transformedSegment));

        EmbeddingInputMapper mapping = segment -> singletonList(TextSegment.from("mapped " + segment.text()));

        Embedding embedding = Embedding.from(new float[] {1});
        TokenUsage tokenUsage = new TokenUsage(1, 2, 3);

        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        when(embeddingModel.embedAll(singletonList(mappedSegment)))
                .thenReturn(Response.from(singletonList(embedding), tokenUsage));

        @SuppressWarnings("unchecked")
        EmbeddingStore<TextSegment> embeddingStore = mock(EmbeddingStore.class);

        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(documentSplitter)
                .textSegmentTransformer(textSegmentTransformer)
                .embeddingInputMapper(mapping)
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();

        // when
        ingestor.ingest(document);

        // then
        verify(embeddingModel).embedAll(singletonList(mappedSegment));
        verify(embeddingStore).addAll(singletonList(embedding), singletonList(transformedSegment));
    }

    @Test
    void should_embed_mapped_segments_with_input_type_when_configured() {

        // given
        Document document = Document.from("hello world");
        TextSegment segment = textSegment("hello world");
        TextSegment mappedSegment = textSegment("mapped hello world");

        DocumentSplitter documentSplitter = mock(DocumentSplitter.class);
        when(documentSplitter.splitAll(singletonList(document))).thenReturn(singletonList(segment));

        EmbeddingInputMapper mapping = ignored -> singletonList(mappedSegment);

        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        when(embeddingModel.embed(any(EmbeddingRequest.class)))
                .thenReturn(EmbeddingResponse.builder()
                        .embeddings(singletonList(Embedding.from(new float[] {1})))
                        .build());

        @SuppressWarnings("unchecked")
        EmbeddingStore<TextSegment> embeddingStore = mock(EmbeddingStore.class);

        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(documentSplitter)
                .embeddingInputMapper(mapping)
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .embeddingInputType(EmbeddingInputType.DOCUMENT)
                .build();

        // when
        ingestor.ingest(document);

        // then
        ArgumentCaptor<EmbeddingRequest> captor = ArgumentCaptor.forClass(EmbeddingRequest.class);
        verify(embeddingModel).embed(captor.capture());
        assertThat(captor.getValue().inputType()).isEqualTo(EmbeddingInputType.DOCUMENT);
        assertThat(captor.getValue().inputs()).hasSize(1);
        assertThat(captor.getValue().inputs().get(0).text()).isEqualTo("mapped hello world");
        verify(embeddingModel, never()).embedAll(any());
        verify(embeddingStore).addAll(singletonList(Embedding.from(new float[] {1})), singletonList(segment));
    }

    @Test
    void should_filter_out_segment_when_mapping_returns_null() {

        // given
        Document document = Document.from("some code");
        TextSegment kept = textSegment("kept");
        TextSegment filteredOut = textSegment("filtered out");
        TextSegment mappedKept = textSegment("mapped kept");

        DocumentSplitter documentSplitter = mock(DocumentSplitter.class);
        when(documentSplitter.splitAll(singletonList(document))).thenReturn(asList(kept, filteredOut));

        EmbeddingInputMapper mapping = segment -> "kept".equals(segment.text()) ? singletonList(mappedKept) : null;

        Embedding embedding = Embedding.from(new float[] {1});
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        when(embeddingModel.embedAll(singletonList(mappedKept)))
                .thenReturn(Response.from(singletonList(embedding), new TokenUsage(1, 2, 3)));

        @SuppressWarnings("unchecked")
        EmbeddingStore<TextSegment> embeddingStore = mock(EmbeddingStore.class);

        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(documentSplitter)
                .embeddingInputMapper(mapping)
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();

        // when
        ingestor.ingest(document);

        // then
        verify(embeddingModel).embedAll(singletonList(mappedKept));
        verify(embeddingStore).addAll(singletonList(embedding), singletonList(kept));
    }

    @Test
    void should_not_embed_or_store_when_all_segments_are_filtered() {

        // given
        Document document = Document.from("some code");
        TextSegment first = textSegment("first");
        TextSegment second = textSegment("second");

        DocumentSplitter documentSplitter = mock(DocumentSplitter.class);
        when(documentSplitter.splitAll(singletonList(document))).thenReturn(asList(first, second));

        EmbeddingInputMapper mapping = ignored -> List.of();

        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);

        @SuppressWarnings("unchecked")
        EmbeddingStore<TextSegment> embeddingStore = mock(EmbeddingStore.class);

        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(documentSplitter)
                .embeddingInputMapper(mapping)
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .embeddingInputType(EmbeddingInputType.DOCUMENT)
                .build();

        // when
        IngestionResult result = ingestor.ingest(document);

        // then
        assertThat(result.tokenUsage()).isNull();
        verifyNoMoreInteractions(embeddingModel, embeddingStore);
    }

    @Test
    void should_use_map_all_for_batch_mapping() {

        // given
        Document document = Document.from("some code");
        TextSegment first = textSegment("first");
        TextSegment second = textSegment("second");

        DocumentSplitter documentSplitter = mock(DocumentSplitter.class);
        when(documentSplitter.splitAll(singletonList(document))).thenReturn(asList(first, second));

        TextSegment mappedFirst = textSegment("mapped first");
        TextSegment mappedSecond = textSegment("mapped second");

        EmbeddingInputMapper mapping = new EmbeddingInputMapper() {
            @Override
            public List<TextSegment> map(TextSegment segment) {
                throw new AssertionError("map() should not be called when mapAll() is overridden");
            }

            @Override
            public List<List<TextSegment>> mapAll(List<TextSegment> segments) {
                assertThat(segments).containsExactly(first, second);
                return List.of(List.of(mappedFirst), List.of(mappedSecond));
            }
        };

        Embedding firstEmbedding = Embedding.from(new float[] {1});
        Embedding secondEmbedding = Embedding.from(new float[] {2});
        TokenUsage tokenUsage = new TokenUsage(1, 2, 3);

        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        when(embeddingModel.embedAll(asList(mappedFirst, mappedSecond)))
                .thenReturn(Response.from(asList(firstEmbedding, secondEmbedding), tokenUsage));

        @SuppressWarnings("unchecked")
        EmbeddingStore<TextSegment> embeddingStore = mock(EmbeddingStore.class);

        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(documentSplitter)
                .embeddingInputMapper(mapping)
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();

        // when
        IngestionResult result = ingestor.ingest(document);

        // then
        assertThat(result.tokenUsage()).isEqualTo(tokenUsage);
        verify(embeddingModel).embedAll(asList(mappedFirst, mappedSecond));
        verify(embeddingStore).addAll(asList(firstEmbedding, secondEmbedding), asList(first, second));
        verifyNoMoreInteractions(embeddingModel, embeddingStore);
    }

    @Test
    void should_reject_map_all_result_with_wrong_size() {

        // given
        Document document = Document.from("some code");
        TextSegment first = textSegment("first");
        TextSegment second = textSegment("second");

        DocumentSplitter documentSplitter = mock(DocumentSplitter.class);
        when(documentSplitter.splitAll(singletonList(document))).thenReturn(asList(first, second));

        EmbeddingInputMapper mapping = new EmbeddingInputMapper() {
            @Override
            public List<TextSegment> map(TextSegment segment) {
                return singletonList(segment);
            }

            @Override
            public List<List<TextSegment>> mapAll(List<TextSegment> segments) {
                return List.of(List.of(textSegment("only one")));
            }
        };

        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);

        @SuppressWarnings("unchecked")
        EmbeddingStore<TextSegment> embeddingStore = mock(EmbeddingStore.class);

        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(documentSplitter)
                .embeddingInputMapper(mapping)
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();

        // when / then
        assertThatThrownBy(() -> ingestor.ingest(document))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must return one list per input segment");
        verifyNoMoreInteractions(embeddingModel, embeddingStore);
    }

    @Test
    void should_ingest_text_segments_directly_with_mapping() {

        // given
        TextSegment first = textSegment("first");
        TextSegment second = textSegment("second");
        TextSegment mappedFirst = textSegment("mapped first");
        TextSegment mappedSecond = textSegment("mapped second");

        EmbeddingInputMapper mapping = segment -> singletonList(TextSegment.from("mapped " + segment.text()));

        Embedding firstEmbedding = Embedding.from(new float[] {1});
        Embedding secondEmbedding = Embedding.from(new float[] {2});
        TokenUsage tokenUsage = new TokenUsage(1, 2, 3);

        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        when(embeddingModel.embedAll(asList(mappedFirst, mappedSecond)))
                .thenReturn(Response.from(asList(firstEmbedding, secondEmbedding), tokenUsage));

        @SuppressWarnings("unchecked")
        EmbeddingStore<TextSegment> embeddingStore = mock(EmbeddingStore.class);

        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .embeddingInputMapper(mapping)
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();

        // when
        IngestionResult result = ingestor.ingestSegments(asList(first, second));

        // then
        assertThat(result.tokenUsage()).isEqualTo(tokenUsage);
        verify(embeddingModel).embedAll(asList(mappedFirst, mappedSecond));
        verify(embeddingStore).addAll(asList(firstEmbedding, secondEmbedding), asList(first, second));
        verifyNoMoreInteractions(embeddingModel, embeddingStore);
    }

    @Test
    void should_apply_text_segment_transformer_when_ingesting_text_segments_directly() {

        // given
        TextSegment original = textSegment("original");
        TextSegment transformed = textSegment("transformed");
        TextSegment mapped = textSegment("mapped transformed");

        TextSegmentTransformer textSegmentTransformer = mock(TextSegmentTransformer.class);
        when(textSegmentTransformer.transformAll(singletonList(original))).thenReturn(singletonList(transformed));

        EmbeddingInputMapper mapping = segment -> singletonList(TextSegment.from("mapped " + segment.text()));

        Embedding embedding = Embedding.from(new float[] {1});
        TokenUsage tokenUsage = new TokenUsage(1, 2, 3);

        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        when(embeddingModel.embedAll(singletonList(mapped)))
                .thenReturn(Response.from(singletonList(embedding), tokenUsage));

        @SuppressWarnings("unchecked")
        EmbeddingStore<TextSegment> embeddingStore = mock(EmbeddingStore.class);

        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .textSegmentTransformer(textSegmentTransformer)
                .embeddingInputMapper(mapping)
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();

        // when
        ingestor.ingestSegments(original);

        // then
        verify(embeddingModel).embedAll(singletonList(mapped));
        verify(embeddingStore).addAll(singletonList(embedding), singletonList(transformed));
    }

    @Test
    void should_not_embed_or_store_when_no_text_segments_are_provided() {

        // given
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);

        @SuppressWarnings("unchecked")
        EmbeddingStore<TextSegment> embeddingStore = mock(EmbeddingStore.class);

        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();

        // when
        IngestionResult result = ingestor.ingestSegments(List.of());

        // then
        assertThat(result.tokenUsage()).isNull();
        verifyNoMoreInteractions(embeddingModel, embeddingStore);
    }
}
