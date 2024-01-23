package dev.langchain4j.rag.content.retriever;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EmbeddingStoreContentRetrieverTest {

    @Test
    void should_retrieve() {

        // given
        EmbeddingStore<TextSegment> embeddingStore = mock(EmbeddingStore.class);
        when(embeddingStore.findRelevant(any(), anyInt(), anyDouble())).thenReturn(asList(
                new EmbeddingMatch<>(0.9, "id 1", null, TextSegment.from("content 1")),
                new EmbeddingMatch<>(0.7, "id 2", null, TextSegment.from("content 2"))
        ));

        Embedding embedding = Embedding.from(asList(1f, 2f, 3f));
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        when(embeddingModel.embed(anyString())).thenReturn(Response.from(embedding));

        ContentRetriever contentRetriever = new EmbeddingStoreContentRetriever(embeddingStore, embeddingModel);

        Query query = Query.from("query");

        // when
        List<Content> contents = contentRetriever.retrieve(query);

        // then
        assertThat(contents).containsExactly(
                Content.from("content 1"),
                Content.from("content 2")
        );

        verify(embeddingModel).embed(query.text());

        verify(embeddingStore).findRelevant(embedding, 3, 0);
    }
}