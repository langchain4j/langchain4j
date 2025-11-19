package dev.langchain4j.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import org.junit.jupiter.api.Test;

/**
 * Verify that the AIServices builder doesn't allow setting more than one of
 * (retriever, contentRetriever, retrievalAugmentor).
 */
class AiServicesBuilderTest {

    interface TestService {
        String chat(String userMessage);
    }

    @Test
    void contentRetrieverAndRetrievalAugmentor() {
        ContentRetriever contentRetriever = mock(ContentRetriever.class);
        RetrievalAugmentor retrievalAugmentor = mock(RetrievalAugmentor.class);

        assertThatExceptionOfType(IllegalConfigurationException.class).isThrownBy(() -> {
            AiServices.builder(TestService.class)
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
            AiServices.builder(TestService.class)
                    .retrievalAugmentor(retrievalAugmentor)
                    .contentRetriever(contentRetriever)
                    .build();
        });
    }

    @Test
    void should_raise_an_error_when_tools_are_classes() {
        class HelloWorld {
            @Tool("Say hello")
            void add(String name) {
                System.out.printf("Hello %s!", name);
            }
        }

        ChatModel chatModel = ChatModelMock.thatAlwaysResponds("Hello there!");

        assertThatExceptionOfType(IllegalConfigurationException.class)
                .isThrownBy(() -> AiServices.builder(TestService.class)
                        .chatModel(chatModel)
                        .tools(HelloWorld.class)
                        .build());
    }

    @Test
    void should_throw_when_chat_model_is_null() {
        assertThatExceptionOfType(IllegalConfigurationException.class)
                .isThrownBy(() ->
                        AiServices.builder(TestService.class).chatModel(null).build())
                .withMessageContaining("chatModel");
    }

    @Test
    void should_throw_when_multiple_retrievers_set() {
        ContentRetriever contentRetriever1 = mock(ContentRetriever.class);
        ContentRetriever contentRetriever2 = mock(ContentRetriever.class);

        assertThatExceptionOfType(IllegalConfigurationException.class)
                .isThrownBy(() -> AiServices.builder(TestService.class)
                        .contentRetriever(contentRetriever1)
                        .contentRetriever(contentRetriever2)
                        .build());
    }

    @Test
    void should_allow_building_with_only_chat_model() {
        ChatModel chatModel = ChatModelMock.thatAlwaysResponds("response");

        TestService service =
                AiServices.builder(TestService.class).chatModel(chatModel).build();

        assertThat(service).isNotNull();
    }
}
