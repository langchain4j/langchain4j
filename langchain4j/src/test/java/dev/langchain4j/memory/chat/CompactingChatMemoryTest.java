package dev.langchain4j.memory.chat;

import static dev.langchain4j.data.message.AiMessage.aiMessage;
import static dev.langchain4j.data.message.SystemMessage.systemMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

class CompactingChatMemoryTest implements WithAssertions {

    /**
     * An executor that runs tasks synchronously on the calling thread, making tests deterministic.
     */
    private static final ExecutorService DIRECT_EXECUTOR = new DirectExecutorService();

    private static ChatModel summaryModel(String summaryText) {
        return new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest chatRequest) {
                return ChatResponse.builder()
                        .aiMessage(aiMessage(summaryText))
                        .metadata(ChatResponseMetadata.builder().build())
                        .build();
            }
        };
    }

    @Test
    void should_have_default_id() {
        ChatMemory memory = CompactingChatMemory.builder()
                .chatModel(summaryModel("summary"))
                .build();

        assertThat(memory.id()).isEqualTo("default");
    }

    @Test
    void should_use_custom_id() {
        ChatMemory memory = CompactingChatMemory.builder()
                .id("custom-id")
                .chatModel(summaryModel("summary"))
                .build();

        assertThat(memory.id()).isEqualTo("custom-id");
    }

    @Test
    void should_add_and_retrieve_single_message_without_compaction() {
        ChatMemory memory = CompactingChatMemory.builder()
                .chatModel(summaryModel("summary"))
                .executorService(DIRECT_EXECUTOR)
                .build();

        UserMessage msg = userMessage("hello");
        memory.add(msg);

        // Single user message, no compaction triggered
        assertThat(memory.messages()).containsExactly(msg);
    }

    @Test
    void should_compact_messages_after_second_user_message_is_added() {
        ChatMemory memory = CompactingChatMemory.builder()
                .chatModel(summaryModel("This is a summary of the conversation."))
                .executorService(DIRECT_EXECUTOR)
                .build();

        memory.add(userMessage("What is Java?"));
        memory.add(aiMessage("Java is a programming language."));
        memory.add(userMessage("Tell me more about it."));

        // After compaction, only one summarized UserMessage remains
        List<ChatMessage> messages = memory.messages();
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0)).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) messages.get(0)).singleText())
                .isEqualTo("This is a summary of the conversation.");
    }

    @Test
    void should_preserve_system_message_during_compaction() {
        ChatMemory memory = CompactingChatMemory.builder()
                .chatModel(summaryModel("Summarized conversation content."))
                .executorService(DIRECT_EXECUTOR)
                .build();

        memory.add(systemMessage("You are a helpful assistant."));
        memory.add(userMessage("What is Python?"));
        memory.add(aiMessage("Python is a programming language."));
        memory.add(userMessage("How does it compare to Java?"));

        List<ChatMessage> messages = memory.messages();
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(((SystemMessage) messages.get(0)).text())
                .isEqualTo("You are a helpful assistant.");
        assertThat(messages.get(1)).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) messages.get(1)).singleText())
                .isEqualTo("Summarized conversation content.");
    }

    @Test
    void should_replace_system_message_when_new_one_is_added() {
        ChatMemory memory = CompactingChatMemory.builder()
                .chatModel(summaryModel("summary"))
                .executorService(DIRECT_EXECUTOR)
                .build();

        memory.add(systemMessage("First system message"));
        memory.add(systemMessage("Second system message"));

        List<ChatMessage> messages = memory.messages();
        long systemMessageCount = messages.stream()
                .filter(m -> m instanceof SystemMessage)
                .count();
        assertThat(systemMessageCount).isEqualTo(1);
        assertThat(((SystemMessage) messages.get(0)).text())
                .isEqualTo("Second system message");
    }

    @Test
    void should_clear_all_messages() {
        ChatMemory memory = CompactingChatMemory.builder()
                .chatModel(summaryModel("summary"))
                .executorService(DIRECT_EXECUTOR)
                .build();

        memory.add(userMessage("hello"));
        assertThat(memory.messages()).isNotEmpty();

        memory.clear();
        assertThat(memory.messages()).isEmpty();

        // Idempotent
        memory.clear();
        assertThat(memory.messages()).isEmpty();
    }

    @Test
    void should_not_compact_single_conversation_message() {
        AtomicBoolean modelCalled = new AtomicBoolean(false);

        ChatModel trackingModel = new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest chatRequest) {
                modelCalled.set(true);
                return ChatResponse.builder()
                        .aiMessage(aiMessage("summary"))
                        .metadata(ChatResponseMetadata.builder().build())
                        .build();
            }
        };

        ChatMemory memory = CompactingChatMemory.builder()
                .chatModel(trackingModel)
                .executorService(DIRECT_EXECUTOR)
                .build();

        // Add only one user message - should not trigger compaction
        memory.add(userMessage("hello"));

        assertThat(modelCalled.get()).isFalse();
        assertThat(memory.messages()).containsExactly(userMessage("hello"));
    }

    @Test
    void should_not_include_system_message_in_summarization_request() {
        AtomicReference<List<ChatMessage>> capturedMessages = new AtomicReference<>();

        ChatModel capturingModel = new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest chatRequest) {
                capturedMessages.set(chatRequest.messages());
                return ChatResponse.builder()
                        .aiMessage(aiMessage("summary"))
                        .metadata(ChatResponseMetadata.builder().build())
                        .build();
            }
        };

        ChatMemory memory = CompactingChatMemory.builder()
                .chatModel(capturingModel)
                .executorService(DIRECT_EXECUTOR)
                .build();

        memory.add(systemMessage("Be helpful."));
        memory.add(userMessage("What is AI?"));
        memory.add(aiMessage("AI stands for Artificial Intelligence."));
        memory.add(userMessage("Explain more."));

        // System message should NOT be included in the summarization request
        List<ChatMessage> sent = capturedMessages.get();
        assertThat(sent).isNotNull();
        assertThat(sent.stream().filter(m -> m instanceof SystemMessage)).isEmpty();

        // The conversation messages should be included, plus the compaction prompt at the end
        assertThat(sent.get(0)).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) sent.get(0)).singleText()).isEqualTo("What is AI?");
        assertThat(sent.get(1)).isInstanceOf(AiMessage.class);
        assertThat(sent.get(sent.size() - 1)).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) sent.get(sent.size() - 1)).singleText()).contains("Summarize");
    }

    @Test
    void should_use_default_executor_when_none_provided() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        ChatModel latchModel = new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest chatRequest) {
                latch.countDown();
                return ChatResponse.builder()
                        .aiMessage(aiMessage("async summary"))
                        .metadata(ChatResponseMetadata.builder().build())
                        .build();
            }
        };

        ChatMemory memory = CompactingChatMemory.builder()
                .chatModel(latchModel)
                .build();

        memory.add(userMessage("first"));
        memory.add(aiMessage("response"));
        memory.add(userMessage("second"));

        // Wait for the async compaction to complete
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();

        // Give a small window for the write lock to be released after compaction
        Thread.sleep(100);

        List<ChatMessage> messages = memory.messages();
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0)).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) messages.get(0)).singleText()).isEqualTo("async summary");
    }

    @Test
    void should_compact_multiple_rounds_of_conversation() {
        ChatMemory memory = CompactingChatMemory.builder()
                .chatModel(summaryModel("round 2 summary"))
                .executorService(DIRECT_EXECUTOR)
                .build();

        // First round
        memory.add(userMessage("Hello"));
        memory.add(aiMessage("Hi there!"));
        memory.add(userMessage("How are you?"));

        // After first compaction
        assertThat(memory.messages()).hasSize(1);

        // Second round: add more messages on top of the summary
        memory.add(aiMessage("I'm doing well."));
        memory.add(userMessage("Great to hear!"));

        // After second compaction, still a single message
        List<ChatMessage> messages = memory.messages();
        assertThat(messages).hasSize(1);
        assertThat(((UserMessage) messages.get(0)).singleText())
                .isEqualTo("round 2 summary");
    }

    /**
     * A simple {@link ExecutorService} that runs tasks synchronously on the calling thread.
     * This makes tests deterministic without needing async waiting.
     */
    private static class DirectExecutorService extends java.util.concurrent.AbstractExecutorService {

        private volatile boolean shutdown = false;

        @Override
        public void execute(Runnable command) {
            command.run();
        }

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }
    }
}
