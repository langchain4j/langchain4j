package dev.langchain4j.store.embedding;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.DocumentTransformer;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
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

        DocumentTransformer transformer = mock(DocumentTransformer.class);
        when(transformer.transformAll(documents)).thenReturn(documents);

        DocumentSplitter splitter = mock(DocumentSplitter.class);
        List<TextSegment> segments = asList(
                textSegment("First sentence."),
                textSegment("Second sentence."),
                textSegment("Third sentence.")
        );
        when(splitter.splitAll(documents)).thenReturn(segments);

        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        List<Embedding> embeddings = asList(
                Embedding.from(new float[]{1}),
                Embedding.from(new float[]{2}),
                Embedding.from(new float[]{3})
        );
        when(embeddingModel.embedAll(segments)).thenReturn(embeddings);

        EmbeddingStore<TextSegment> embeddingStore = mock(EmbeddingStore.class);

        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .transformer(transformer)
                .splitter(splitter)
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();


        ingestor.ingest(documents);


        verify(transformer).transformAll(documents);
        verifyNoMoreInteractions(transformer);

        verify(splitter).splitAll(documents);
        verifyNoMoreInteractions(splitter);

        verify(embeddingModel).embedAll(segments);
        verifyNoMoreInteractions(embeddingModel);

        verify(embeddingStore).addAll(embeddings, segments);
        verifyNoMoreInteractions(embeddingStore);
    }
}