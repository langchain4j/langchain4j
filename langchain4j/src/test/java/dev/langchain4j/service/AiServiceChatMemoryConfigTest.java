package dev.langchain4j.service;

import static dev.langchain4j.service.AiServicesIT.chatRequest;
import static dev.langchain4j.service.AiServicesIT.verifyNoMoreInteractionsFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiServiceChatMemoryConfigTest {
    @Spy
    ChatModel chatModel = ChatModelMock.thatAlwaysResponds("Berlin");

    @AfterEach
    void afterEach() {
        verifyNoMoreInteractionsFor(chatModel);
    }

    interface AiService {
        String chat(@MemoryId String memoryId, @UserMessage String message);
    }

    @Test
    void should_throw_exception_when_chat_memory_provider_is_not_configured() {
        assertThatThrownBy(() ->
                        AiServices.builder(AiService.class).chatModel(chatModel).build())
                .isExactlyInstanceOf(IllegalConfigurationException.class)
                .hasMessage(
                        "In order to use @MemoryId, please configure the ChatMemoryProvider on the 'dev.langchain4j.service.AiServiceChatMemoryConfigTest$AiService'.");
    }

    @Test
    void should_return_response_when_chat_memory_provider_is_configured() {
        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatModel(chatModel)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10))
                .build();
        // when-then
        assertThat(aiService.chat("1", "Hello")).isEqualTo("Berlin");
        verify(chatModel).chat(chatRequest("Hello"));
    }

    @Test
    void should_handle_null_memory_id() {
        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatModel(chatModel)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10))
                .build();

        // when-then
        assertThatThrownBy(() -> aiService.chat(null, "request"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("@MemoryId")
                .hasMessageContaining("must not be null");
    }

    @Test
    void should_handle_empty_memory_id() {
        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatModel(chatModel)
                .chatMemoryProvider(memoryId -> {
                    assertThat(memoryId).isEqualTo("");
                    return MessageWindowChatMemory.withMaxMessages(10);
                })
                .build();

        // when-then
        assertThat(aiService.chat("", "request")).isEqualTo("Berlin");
        verify(chatModel).chat(chatRequest("request"));
    }

    @Test
    void should_use_different_memory_for_different_ids() {
        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatModel(chatModel)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10))
                .build();

        // when-then
        assertThat(aiService.chat("id1", "request")).isEqualTo("Berlin");
        assertThat(aiService.chat("id2", "data")).isEqualTo("Berlin");

        verify(chatModel).chat(chatRequest("request"));
        verify(chatModel).chat(chatRequest("data"));
    }

    @Test
    void should_preserve_memory_for_same_id() {
        // given
        ChatModel chatModel = ChatModelMock.thatAlwaysResponds("response");
        AiService aiService = AiServices.builder(AiService.class)
                .chatModel(chatModel)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10))
                .build();

        // when
        String response1 = aiService.chat("id1", "first value");
        String response2 = aiService.chat("id1", "second value");

        // then
        assertThat(response1).isEqualTo("response");
        assertThat(response2).isEqualTo("response");
    }

    @Test
    void should_handle_whitespace_memory_id() {
        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatModel(chatModel)
                .chatMemoryProvider(memoryId -> {
                    assertThat(memoryId).isEqualTo("   ");
                    return MessageWindowChatMemory.withMaxMessages(10);
                })
                .build();

        // when-then
        assertThat(aiService.chat("   ", "request")).isEqualTo("Berlin");
        verify(chatModel).chat(chatRequest("request"));
    }
}
