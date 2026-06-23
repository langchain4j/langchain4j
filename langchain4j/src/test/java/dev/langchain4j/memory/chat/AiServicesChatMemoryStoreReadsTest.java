package dev.langchain4j.memory.chat;

import static dev.langchain4j.data.message.AiMessage.aiMessage;
import static dev.langchain4j.data.message.SystemMessage.systemMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiServicesChatMemoryStoreReadsTest {

    interface Assistant {

        String chat(@MemoryId int memoryId, @UserMessage String message);

        @SystemMessage("You are helpful")
        String chatWithSystemMessage(@MemoryId int memoryId, @UserMessage String message);

        @SystemMessage("You are funny")
        String chatWithAnotherSystemMessage(@MemoryId int memoryId, @UserMessage String message);
    }

    @Test
    void should_not_read_chat_memory_store_multiple_times_per_ai_service_call() {
        HitCountChatMemoryStore store = new HitCountChatMemoryStore();
        ChatModelMock chatModel = ChatModelMock.thatAlwaysResponds("ok");
        Assistant assistant = assistant(chatModel, store);

        HitCountChatMemoryStore.HitCounts counts = store.measureHitCounts(() -> assistant.chat(1, "hello"));

        assertThat(counts).isEqualTo(new HitCountChatMemoryStore.HitCounts(2, 2, 0));
        assertThat(store.getMessages(1)).containsExactly(userMessage("hello"), aiMessage("ok"));
        assertThat(chatModel.getRequests()).containsExactly(List.of(userMessage("hello")));
    }

    @Test
    void should_reduce_chat_memory_store_reads_when_system_message_is_present() {
        HitCountChatMemoryStore store = new HitCountChatMemoryStore();
        ChatModelMock chatModel = ChatModelMock.thatAlwaysResponds("ok");
        Assistant assistant = assistant(chatModel, store);

        HitCountChatMemoryStore.HitCounts counts =
                store.measureHitCounts(() -> assistant.chatWithSystemMessage(1, "hello"));

        assertThat(counts).isEqualTo(new HitCountChatMemoryStore.HitCounts(3, 3, 0));
        assertThat(store.getMessages(1))
                .containsExactly(systemMessage("You are helpful"), userMessage("hello"), aiMessage("ok"));
        assertThat(chatModel.getRequests())
                .containsExactly(List.of(systemMessage("You are helpful"), userMessage("hello")));
    }

    @Test
    void should_dedupe_same_system_message() {
        HitCountChatMemoryStore store = new HitCountChatMemoryStore();
        ChatModelMock chatModel = ChatModelMock.thatAlwaysResponds("ok");
        Assistant assistant = assistant(chatModel, store);

        assistant.chatWithSystemMessage(1, "hello");
        HitCountChatMemoryStore.HitCounts counts =
                store.measureHitCounts(() -> assistant.chatWithSystemMessage(1, "again"));

        assertThat(counts).isEqualTo(new HitCountChatMemoryStore.HitCounts(3, 2, 0));
        assertThat(store.getMessages(1))
                .containsExactly(
                        systemMessage("You are helpful"),
                        userMessage("hello"),
                        aiMessage("ok"),
                        userMessage("again"),
                        aiMessage("ok"));
        assertThat(chatModel.getRequests())
                .containsExactly(
                        List.of(systemMessage("You are helpful"), userMessage("hello")),
                        List.of(
                                systemMessage("You are helpful"),
                                userMessage("hello"),
                                aiMessage("ok"),
                                userMessage("again")));
    }

    @Test
    void should_preserve_default_system_message_order_when_always_keep_system_message_first_is_false() {
        HitCountChatMemoryStore store = new HitCountChatMemoryStore();
        ChatModelMock chatModel = ChatModelMock.thatAlwaysResponds("ok");
        Assistant assistant = assistant(chatModel, store, 10, false);

        assistant.chatWithSystemMessage(1, "hello");
        HitCountChatMemoryStore.HitCounts counts =
                store.measureHitCounts(() -> assistant.chatWithAnotherSystemMessage(1, "again"));

        assertThat(counts).isEqualTo(new HitCountChatMemoryStore.HitCounts(3, 3, 0));
        List<ChatMessage> messages = store.getMessages(1);
        assertThat(messages)
                .containsExactly(
                        userMessage("hello"),
                        aiMessage("ok"),
                        systemMessage("You are funny"),
                        userMessage("again"),
                        aiMessage("ok"));
        assertThat(chatModel.getRequests())
                .containsExactly(
                        List.of(systemMessage("You are helpful"), userMessage("hello")),
                        List.of(
                                userMessage("hello"),
                                aiMessage("ok"),
                                systemMessage("You are funny"),
                                userMessage("again")));
    }

    @Test
    void should_keep_system_message_first_when_always_keep_system_message_first_is_true() {
        HitCountChatMemoryStore store = new HitCountChatMemoryStore();
        ChatModelMock chatModel = ChatModelMock.thatAlwaysResponds("ok");
        Assistant assistant = assistant(chatModel, store, 10, true);

        assistant.chatWithSystemMessage(1, "hello");
        HitCountChatMemoryStore.HitCounts counts =
                store.measureHitCounts(() -> assistant.chatWithSystemMessage(1, "again"));

        assertThat(counts).isEqualTo(new HitCountChatMemoryStore.HitCounts(3, 2, 0));
        assertThat(store.getMessages(1))
                .containsExactly(
                        systemMessage("You are helpful"),
                        userMessage("hello"),
                        aiMessage("ok"),
                        userMessage("again"),
                        aiMessage("ok"));
        assertThat(chatModel.getRequests())
                .containsExactly(
                        List.of(systemMessage("You are helpful"), userMessage("hello")),
                        List.of(
                                systemMessage("You are helpful"),
                                userMessage("hello"),
                                aiMessage("ok"),
                                userMessage("again")));
    }

    @Test
    void should_replace_changing_system_message_and_keep_it_first() {
        HitCountChatMemoryStore store = new HitCountChatMemoryStore();
        ChatModelMock chatModel = ChatModelMock.thatAlwaysResponds("ok");
        Assistant assistant = assistant(chatModel, store, 10, true);

        assistant.chatWithSystemMessage(1, "hello");
        HitCountChatMemoryStore.HitCounts counts =
                store.measureHitCounts(() -> assistant.chatWithAnotherSystemMessage(1, "again"));

        assertThat(counts).isEqualTo(new HitCountChatMemoryStore.HitCounts(3, 3, 0));
        assertThat(store.getMessages(1))
                .containsExactly(
                        systemMessage("You are funny"),
                        userMessage("hello"),
                        aiMessage("ok"),
                        userMessage("again"),
                        aiMessage("ok"));
        assertThat(chatModel.getRequests())
                .containsExactly(
                        List.of(systemMessage("You are helpful"), userMessage("hello")),
                        List.of(
                                systemMessage("You are funny"),
                                userMessage("hello"),
                                aiMessage("ok"),
                                userMessage("again")));
    }

    @Test
    void should_keep_request_messages_unchanged_when_message_window_is_exceeded() {
        HitCountChatMemoryStore store = new HitCountChatMemoryStore();
        ChatModelMock chatModel = ChatModelMock.thatAlwaysResponds("ok");
        Assistant assistant = assistant(chatModel, store, 2);

        assistant.chat(1, "first");
        HitCountChatMemoryStore.HitCounts counts = store.measureHitCounts(() -> assistant.chat(1, "second"));

        assertThat(counts).isEqualTo(new HitCountChatMemoryStore.HitCounts(2, 2, 0));
        assertThat(chatModel.getRequests())
                .containsExactly(
                        List.of(userMessage("first")),
                        List.of(userMessage("first"), aiMessage("ok"), userMessage("second")));
        assertThat(store.getMessages(1)).containsExactly(userMessage("second"), aiMessage("ok"));
    }

    @Test
    void should_keep_request_messages_unchanged_when_system_message_window_is_exceeded() {
        HitCountChatMemoryStore store = new HitCountChatMemoryStore();
        ChatModelMock chatModel = ChatModelMock.thatAlwaysResponds("ok");
        Assistant assistant = assistant(chatModel, store, 2);

        assistant.chatWithSystemMessage(1, "first");
        HitCountChatMemoryStore.HitCounts counts =
                store.measureHitCounts(() -> assistant.chatWithSystemMessage(1, "second"));

        assertThat(counts).isEqualTo(new HitCountChatMemoryStore.HitCounts(3, 2, 0));
        assertThat(chatModel.getRequests())
                .containsExactly(
                        List.of(systemMessage("You are helpful"), userMessage("first")),
                        List.of(systemMessage("You are helpful"), aiMessage("ok"), userMessage("second")));
        assertThat(store.getMessages(1)).containsExactly(systemMessage("You are helpful"), aiMessage("ok"));
    }

    private static Assistant assistant(ChatModelMock chatModel, HitCountChatMemoryStore store) {
        return assistant(chatModel, store, 10);
    }

    private static Assistant assistant(ChatModelMock chatModel, HitCountChatMemoryStore store, int maxMessages) {
        return assistant(chatModel, store, maxMessages, false);
    }

    private static Assistant assistant(
            ChatModelMock chatModel,
            HitCountChatMemoryStore store,
            int maxMessages,
            boolean alwaysKeepSystemMessageFirst) {
        return AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                        .id(memoryId)
                        .maxMessages(maxMessages)
                        .alwaysKeepSystemMessageFirst(alwaysKeepSystemMessageFirst)
                        .chatMemoryStore(store)
                        .build())
                .build();
    }
}
