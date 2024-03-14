package dev.langchain4j.service;

import dev.langchain4j.exception.IllegalConfigurationException;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.retriever.Retriever;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;

/**
 * Verify that the AIServices builder doesn't allow setting more than out of
 * (retriever, contentRetriever, retrievalAugmentor).
 */
public class AiServicesBuilderTest {

    @Test
    public void testRetrieverAndContentRetriever() {
        Retriever retriever = mock(Retriever.class);
        Mockito.when(retriever.toContentRetriever()).thenReturn((query) -> {
            throw new RuntimeException("Should not be called");
        });
        ContentRetriever contentRetriever = mock(ContentRetriever.class);

        assertThatExceptionOfType(IllegalConfigurationException.class).isThrownBy(() -> {
            AiServices.builder(AiServices.class)
                    .retriever(retriever)
                    .contentRetriever(contentRetriever)
                    .build();
        });
    }

    @Test
    public void testRetrieverAndRetrievalAugmentor() {
        Retriever retriever = mock(Retriever.class);
        Mockito.when(retriever.toContentRetriever()).thenReturn((query) -> {
            throw new RuntimeException("Should not be called");
        });
        RetrievalAugmentor retrievalAugmentor = mock(RetrievalAugmentor.class);

        assertThatExceptionOfType(IllegalConfigurationException.class).isThrownBy(() -> {
            AiServices.builder(AiServices.class)
                    .retriever(retriever)
                    .retrievalAugmentor(retrievalAugmentor)
                    .build();
        });
    }

    @Test
    public void testContentRetrieverAndRetrievalAugmentor() {
        ContentRetriever contentRetriever = mock(ContentRetriever.class);
        RetrievalAugmentor retrievalAugmentor = mock(RetrievalAugmentor.class);

        assertThatExceptionOfType(IllegalConfigurationException.class).isThrownBy(() -> {
            AiServices.builder(AiServices.class)
                    .contentRetriever(contentRetriever)
                    .retrievalAugmentor(retrievalAugmentor)
                    .build();
        });
    }

    @Test
    public void testContentRetrieverAndRetriever() {
        Retriever retriever = mock(Retriever.class);
        ContentRetriever contentRetriever = mock(ContentRetriever.class);

        assertThatExceptionOfType(IllegalConfigurationException.class).isThrownBy(() -> {
            AiServices.builder(AiServices.class)
                    .contentRetriever(contentRetriever)
                    .retriever(retriever)
                    .build();
        });
    }

    @Test
    public void testRetrievalAugmentorAndRetriever() {
        Retriever retriever = mock(Retriever.class);
        RetrievalAugmentor retrievalAugmentor = mock(RetrievalAugmentor.class);

        assertThatExceptionOfType(IllegalConfigurationException.class).isThrownBy(() -> {
            AiServices.builder(AiServices.class)
                    .retrievalAugmentor(retrievalAugmentor)
                    .retriever(retriever)
                    .build();
        });
    }

    @Test
    public void testRetrievalAugmentorAndContentRetriever() {
        ContentRetriever contentRetriever = mock(ContentRetriever.class);
        RetrievalAugmentor retrievalAugmentor = mock(RetrievalAugmentor.class);

        assertThatExceptionOfType(IllegalConfigurationException.class).isThrownBy(() -> {
            AiServices.builder(AiServices.class)
                    .retrievalAugmentor(retrievalAugmentor)
                    .contentRetriever(contentRetriever)
                    .build();
        });
    }

}
