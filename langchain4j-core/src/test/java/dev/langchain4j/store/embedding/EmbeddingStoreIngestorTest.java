package dev.langchain4j.store.embedding;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.DocumentTransformer;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.data.segment.TextSegmentTransformer;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.langchain4j.data.segment.TextSegment.textSegment;
import static java.util.Arrays.asList;
import static org.mockito.Mockito.*;

class EmbeddingStoreIngestorTest {

    @Test
    public void should_extract_text_then_split_into_segments_then_embed_them_and_store_in_embedding_store() {

        Document firstDocument = Document.from("First sentence.");
        Document secondDocument = Document.from("Second sentence. Third sentence.");
        List<Document> documents = asList(firstDocument, secondDocument);

        DocumentTransformer documentTransformer = mock(DocumentTransformer.class);
        when(documentTransformer.transformAll(documents)).thenReturn(documents);

        DocumentSplitter documentSplitter = mock(DocumentSplitter.class);
        List<TextSegment> segments = asList(
                textSegment("First sentence."),
                textSegment("Second sentence."),
                textSegment("Third sentence.")
        );
        when(documentSplitter.splitAll(documents)).thenReturn(segments);

        TextSegmentTransformer textSegmentTransformer = mock(TextSegmentTransformer.class);
        List<TextSegment> transformedSegments = asList(
                textSegment("Transformed first sentence."),
                textSegment("Transformed second sentence."),
                textSegment("Transformed third sentence.")
        );
        when(textSegmentTransformer.transformAll(segments)).thenReturn(transformedSegments);

        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        List<Embedding> embeddings = asList(
                Embedding.from(new float[]{1}),
                Embedding.from(new float[]{2}),
                Embedding.from(new float[]{3})
        );
        when(embeddingModel.embedAll(transformedSegments)).thenReturn(Response.from(embeddings));

        EmbeddingStore<TextSegment> embeddingStore = mock(EmbeddingStore.class);

        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .documentTransformer(documentTransformer)
                .documentSplitter(documentSplitter)
                .textSegmentTransformer(textSegmentTransformer)
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();


        ingestor.ingest(documents);


        verify(documentTransformer).transformAll(documents);
        verifyNoMoreInteractions(documentTransformer);

        verify(documentSplitter).splitAll(documents);
        verifyNoMoreInteractions(documentSplitter);

        verify(textSegmentTransformer).transformAll(segments);
        verifyNoMoreInteractions(textSegmentTransformer);

        verify(embeddingModel).embedAll(transformedSegments);
        verifyNoMoreInteractions(embeddingModel);

        verify(embeddingStore).addAll(embeddings, transformedSegments);
        verifyNoMoreInteractions(embeddingStore);
    }
}