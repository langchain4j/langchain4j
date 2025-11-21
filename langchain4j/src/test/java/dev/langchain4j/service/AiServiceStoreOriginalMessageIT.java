package dev.langchain4j.service;

import static dev.langchain4j.data.message.AiMessage.aiMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.service.AiServicesIT.chatRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.injector.DefaultContentInjector;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiServiceStoreOriginalMessageIT {

    @Spy
    ChatModel model = ChatModelMock.thatAlwaysResponds("Berlin");

    static ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(2);

    static RetrievalAugmentor augmentor = DefaultRetrievalAugmentor.builder()
            .contentRetriever(new CustomRetriever())
            .contentInjector(DefaultContentInjector.builder()
                    .promptTemplate(PromptTemplate.from("{{userMessage}}\n{{contents}}"))
                    .build())
            .build();

    static final String COMPLETE_QUERY = "What is the capital of Germany?";
    static final String AUGMENTED_MESSAGE = COMPLETE_QUERY + "\n" + "Berlin";

    private interface AiService {

        @UserMessage(value = "What is the capital of {{country}}?", storeOriginal = true)
        String chatStoringOriginalQuery(@V("country") String country);

        @UserMessage(value = "What is the capital of {{country}}?")
        String chatStoringAugmentedQuery(@V("country") String country);

        @UserMessage("What is the capital of Germany?")
        String chatStoringOriginalQueryWithNoParams();

        @UserMessage("What is the capital of Germany?")
        String chatStoringAugmentedQueryWithNoParams();
    }

    @AfterEach
    void afterEach() {
        chatMemory.clear();
    }

    @Test
    void should_store_original_and_use_augmented_in_request() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatMemory(chatMemory)
                .chatModel(model)
                .retrievalAugmentor(augmentor)
                .build();

        // when
        String aiMessage = aiService.chatStoringOriginalQuery("Germany");

        // then
        verify(model).chat(chatRequest(AUGMENTED_MESSAGE));

        assertThat(chatMemory.messages()).containsExactly(userMessage(COMPLETE_QUERY), aiMessage(aiMessage));
    }

    @Test
    void should_store_augmented_and_use_augmented_in_request() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatMemory(chatMemory)
                .chatModel(model)
                .retrievalAugmentor(augmentor)
                .build();

        // when
        String aiMessage = aiService.chatStoringAugmentedQuery("Germany");

        // then
        verify(model).chat(chatRequest(AUGMENTED_MESSAGE));

        assertThat(chatMemory.messages()).containsExactly(userMessage(AUGMENTED_MESSAGE), aiMessage(aiMessage));
    }

    @Test
    void should_store_original_and_use_augmented_no_params() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatMemory(chatMemory)
                .chatModel(model)
                .retrievalAugmentor(augmentor)
                .build();

        // when
        String aiMessage = aiService.chatStoringOriginalQueryWithNoParams();

        // then
        verify(model).chat(chatRequest(AUGMENTED_MESSAGE));

        assertThat(chatMemory.messages()).containsExactly(userMessage(AUGMENTED_MESSAGE), aiMessage(aiMessage));
    }

    @Test
    void should_store_augmented_and_use_augmented_no_params() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatMemory(chatMemory)
                .chatModel(model)
                .retrievalAugmentor(augmentor)
                .build();

        // when
        String aiMessage = aiService.chatStoringAugmentedQueryWithNoParams();

        // then
        verify(model).chat(chatRequest(AUGMENTED_MESSAGE));

        assertThat(chatMemory.messages()).containsExactly(userMessage(AUGMENTED_MESSAGE), aiMessage(aiMessage));
    }

    private static class CustomRetriever implements ContentRetriever {

        @Override
        public List<Content> retrieve(final Query query) {
            return List.of(Content.from("Berlin"));
        }
    }
}
