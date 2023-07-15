package dev.langchain4j.store.embedding;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.SentenceSplitter;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Result;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.langchain4j.data.segment.TextSegment.textSegment;
import static java.util.Arrays.asList;
import static org.mockito.Mockito.*;

class EmbeddingStoreIngestorTest {

    @Test
    public void should_split_documents_then_embed_them_and_store_in_embedding_store() {

        Document firstDocument = Document.from("First sentence.");
        Document secondDocument = Document.from("Second sentence. Third sentence.");
        List<Document> documents = asList(firstDocument, secondDocument);

        DocumentSplitter splitter = mock(SentenceSplitter.class);
        List<TextSegment> segments = asList(
                textSegment("First sentence."),
                textSegment("Second sentence."),
                textSegment("Third sentence.")
        );
        when(splitter.split(documents)).thenReturn(segments);

        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        List<Embedding> embeddings = asList(
                Embedding.from(new float[]{1}),
                Embedding.from(new float[]{2}),
                Embedding.from(new float[]{3})
        );
        when(embeddingModel.embedAll(segments)).thenReturn(Result.from(embeddings));

        EmbeddingStore<TextSegment> embeddingStore = mock(EmbeddingStore.class);

        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .splitter(splitter)
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();


        ingestor.ingest(documents);


        verify(splitter).split(documents);
        verifyNoMoreInteractions(splitter);

        verify(embeddingModel).embedAll(segments);
        verifyNoMoreInteractions(embeddingModel);

        verify(embeddingStore).addAll(embeddings, segments);
        verifyNoMoreInteractions(embeddingStore);
    }
}