package dev.langchain4j.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.retriever.Retriever;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Verify that the AIServices builder doesn't allow setting more than out of
 * (retriever, contentRetriever, retrievalAugmentor).
 */
class AiServicesBuilderTest {

    @Test
    void retrieverAndContentRetriever() {
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
    void retrieverAndRetrievalAugmentor() {
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
    void contentRetrieverAndRetrievalAugmentor() {
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
    void contentRetrieverAndRetriever() {
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
    void retrievalAugmentorAndRetriever() {
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
    void retrievalAugmentorAndContentRetriever() {
        ContentRetriever contentRetriever = mock(ContentRetriever.class);
        RetrievalAugmentor retrievalAugmentor = mock(RetrievalAugmentor.class);

        assertThatExceptionOfType(IllegalConfigurationException.class).isThrownBy(() -> {
            AiServices.builder(AiServices.class)
                    .retrievalAugmentor(retrievalAugmentor)
                    .contentRetriever(contentRetriever)
                    .build();
        });
    }

    static class HelloWorld {

        @Tool("Say hello")
        void add(String name) {
            System.out.printf("Hello %s!", name);
        }
    }

    interface Assistant {

        Response<AiMessage> chat(String userMessage);
    }

    @Test
    void should_raise_an_error_when_tools_are_classes() {
        ChatLanguageModel chatLanguageModel = ChatModelMock.thatAlwaysResponds("Hello there!");

        assertThatExceptionOfType(IllegalConfigurationException.class)
                .isThrownBy(() -> AiServices.builder(Assistant.class)
                        .chatLanguageModel(chatLanguageModel)
                        .tools(HelloWorld.class)
                        .build());
    }
}
