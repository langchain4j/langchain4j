package dev.langchain4j.service;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the asynchronous chat-memory API: the bundled {@link MessageWindowChatMemory} composes the
 * store's async methods, and a store that does not implement the async methods fails loudly (rather than being
 * silently offloaded) when used with an asynchronous AI Service.
 */
class AsyncChatMemoryTest {

    @Test
    void message_window_chat_memory_async_methods_compose_the_store() throws Exception {

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        chatMemory.addAsync(List.of(UserMessage.from("Hello"))).toCompletableFuture().get(5, SECONDS);
        chatMemory.addAsync(List.of(AiMessage.from("Hi there"))).toCompletableFuture().get(5, SECONDS);

        List<ChatMessage> messages =
                chatMemory.messagesAsync().toCompletableFuture().get(5, SECONDS);

        assertThat(messages).containsExactly(UserMessage.from("Hello"), AiMessage.from("Hi there"));
        // the async writes are visible through the synchronous view too
        assertThat(chatMemory.messages()).isEqualTo(messages);
    }

    @Test
    void message_window_chat_memory_addAsync_adds_a_batch_of_messages() throws Exception {

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        chatMemory.addAsync(List.of(UserMessage.from("Hello"), AiMessage.from("Hi there")))
                .toCompletableFuture()
                .get(5, SECONDS);

        assertThat(chatMemory.messages()).containsExactly(UserMessage.from("Hello"), AiMessage.from("Hi there"));
    }

    /**
     * A store implementing only the synchronous methods; the async methods fall back to the throwing defaults.
     */
    static class SyncOnlyChatMemoryStore implements ChatMemoryStore {

        private List<ChatMessage> messages = List.of();

        @Override
        public List<ChatMessage> getMessages(Object memoryId) {
            return messages;
        }

        @Override
        public void updateMessages(Object memoryId, List<ChatMessage> messages) {
            this.messages = messages;
        }

        @Override
        public void deleteMessages(Object memoryId) {
            this.messages = List.of();
        }
    }

    @Test
    void addAsync_throws_when_store_does_not_support_async() {

        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .maxMessages(10)
                .chatMemoryStore(new SyncOnlyChatMemoryStore())
                .build();

        assertThatThrownBy(() -> chatMemory.addAsync(List.of(UserMessage.from("Hello"), AiMessage.from("Hi"))))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("getMessagesAsync");
    }

    interface Assistant {

        CompletableFuture<String> chat(String userMessage);
    }

    static class StubChatModel implements ChatModel {

        @Override
        public CompletableFuture<ChatResponse> doChatAsync(ChatRequest chatRequest) {
            return CompletableFuture.completedFuture(ChatResponse.builder()
                    .aiMessage(AiMessage.from("Berlin"))
                    .metadata(ChatResponseMetadata.builder().build())
                    .build());
        }

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            throw new AssertionError("synchronous chat() must not be called");
        }
    }

    @Test
    void completable_future_ai_service_fails_when_chat_memory_store_does_not_support_async() {

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(new StubChatModel())
                .chatMemory(MessageWindowChatMemory.builder()
                        .maxMessages(10)
                        .chatMemoryStore(new SyncOnlyChatMemoryStore())
                        .build())
                .build();

        try {
            assistant.chat("What is the capital of Germany?").get(5, SECONDS);
            fail("expected the future to fail because the store does not support async");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            assertThat(e.getCause())
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("getMessagesAsync");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    interface SystemMessagedAssistant {

        @SystemMessage("You are a helpful assistant.")
        CompletableFuture<String> chat(String userMessage);
    }

    @Test
    void completable_future_ai_service_fails_when_store_unsupported_and_a_system_message_is_present() {

        // With a system message the assembly calls addAsync synchronously before any composition, so the
        // UnsupportedOperationException is thrown synchronously - it must still surface as a failed future
        // (not thrown out of the AI Service method).
        SystemMessagedAssistant assistant = AiServices.builder(SystemMessagedAssistant.class)
                .chatModel(new StubChatModel())
                .chatMemory(MessageWindowChatMemory.builder()
                        .maxMessages(10)
                        .chatMemoryStore(new SyncOnlyChatMemoryStore())
                        .build())
                .build();

        CompletableFuture<String> future = assistant.chat("Hi"); // must not throw here
        assertThatThrownBy(() -> future.get(5, SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(UnsupportedOperationException.class);
    }
}
