package dev.langchain4j.service;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Verify that the AIServices builder doesn't allow setting more than out of
 * (retriever, contentRetriever, retrievalAugmentor).
 */
class AiServicesBuilderTest {

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

    @Test
    void should_raise_an_error_when_tools_are_classes() {

        // given
        interface Assistant {

            String chat(String userMessage);
        }

        class HelloWorld {

            @Tool("Say hello")
            void add(String name) {
                System.out.printf("Hello %s!", name);
            }
        }

        ChatModel chatModel = ChatModelMock.thatAlwaysResponds("Hello there!");

        assertThatExceptionOfType(IllegalConfigurationException.class)
                .isThrownBy(() -> AiServices.builder(Assistant.class)
                        .chatModel(chatModel)
                        .tools(HelloWorld.class)
                        .build());
    }
}
