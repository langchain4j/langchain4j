package dev.langchain4j.service;

import static dev.langchain4j.service.AiServicesIT.chatRequest;
import static dev.langchain4j.service.AiServicesIT.verifyNoMoreInteractionsFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import dev.langchain4j.exception.IllegalConfigurationException;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class AiServiceChatMemoryConfigTest {
    @Spy
    ChatLanguageModel chatLanguageModel = ChatModelMock.thatAlwaysResponds("Berlin");

    @AfterEach
    void afterEach() {
        verifyNoMoreInteractionsFor(chatLanguageModel);
    }

    interface AiService {
        String chat(@MemoryId String memoryId, @UserMessage String message);
    }

    @Test
    void test_memoryId_requires_chatMemoryProvider_configuration_1() {
        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatLanguageModel(chatLanguageModel)
                .build();
        // when-then
        assertThatThrownBy(() -> aiService.chat("1", "Hello"))
                .isExactlyInstanceOf(IllegalConfigurationException.class)
                .hasMessage("When the @MemoryId annotation is used, the chatMemoryProvider needs to be configured.");
    }

    @Test
    void test_memoryId_requires_chatMemoryProvider_configuration_2() {
        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatLanguageModel(chatLanguageModel)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10))
                .build();
        // when-then
        assertThat(aiService.chat("1", "Hello")).isEqualTo("Berlin");
        verify(chatLanguageModel).chat(chatRequest("Hello"));
    }
}
