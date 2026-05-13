package dev.langchain4j.memory.chat;

import static dev.langchain4j.data.message.AiMessage.aiMessage;
import static dev.langchain4j.data.message.SystemMessage.systemMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.TokenCountEstimator;
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

    private static TokenCountEstimator wordCountEstimator() {
        return new TokenCountEstimator() {
            @Override
            public int estimateTokenCountInText(String text) {
                return text == null ? 0 : text.split("\\s+").length;
            }

            @Override
            public int estimateTokenCountInMessage(ChatMessage message) {
                if (message instanceof UserMessage u) {
                    return estimateTokenCountInText(u.singleText());
                } else if (message instanceof AiMessage a) {
                    return estimateTokenCountInText(a.text());
                } else if (message instanceof SystemMessage s) {
                    return estimateTokenCountInText(s.text());
                } else if (message instanceof ToolExecutionResultMessage t) {
                    return estimateTokenCountInText(t.text());
                }
                return 0;
            }

            @Override
            public int estimateTokenCountInMessages(Iterable<ChatMessage> messages) {
                int total = 0;
                for (ChatMessage msg : messages) {
                    total += estimateTokenCountInMessage(msg);
                }
                return total;
            }
        };
    }

    // ===== Basic tests =====

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
                .filter(SystemMessage.class::isInstance)
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
        assertThat(sent.stream().filter(SystemMessage.class::isInstance)).isEmpty();

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

    // ===== retainLastMessages tests =====

    @Test
    void should_retain_last_n_messages_and_summarize_older_ones() {
        AtomicReference<List<ChatMessage>> capturedMessages = new AtomicReference<>();

        ChatModel capturingModel = new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest chatRequest) {
                capturedMessages.set(chatRequest.messages());
                return ChatResponse.builder()
                        .aiMessage(aiMessage("summary of older messages"))
                        .metadata(ChatResponseMetadata.builder().build())
                        .build();
            }
        };

        ChatMemory memory = CompactingChatMemory.builder()
                .chatModel(capturingModel)
                .retainLastMessages(2)
                .executorService(DIRECT_EXECUTOR)
                .build();

        memory.add(userMessage("first question"));
        memory.add(aiMessage("first answer"));
        memory.add(userMessage("second question"));
        memory.add(aiMessage("second answer"));
        memory.add(userMessage("third question"));

        // Should have: summary + last 2 messages (aiMessage "second answer" + userMessage "third question")
        List<ChatMessage> messages = memory.messages();
        assertThat(messages).hasSize(3);
        assertThat(messages.get(0)).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) messages.get(0)).singleText()).isEqualTo("summary of older messages");
        assertThat(messages.get(1)).isInstanceOf(AiMessage.class);
        assertThat(((AiMessage) messages.get(1)).text()).isEqualTo("second answer");
        assertThat(messages.get(2)).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) messages.get(2)).singleText()).isEqualTo("third question");

        // The summarization request should only contain the older messages
        List<ChatMessage> sent = capturedMessages.get();
        assertThat(sent).isNotNull();
        // first question + first answer + second question + compaction prompt
        assertThat(sent).hasSize(4);
        assertThat(((UserMessage) sent.get(0)).singleText()).isEqualTo("first question");
        assertThat(((AiMessage) sent.get(1)).text()).isEqualTo("first answer");
        assertThat(((UserMessage) sent.get(2)).singleText()).isEqualTo("second question");
        // last is the compaction prompt
        assertThat(sent.get(3)).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) sent.get(3)).singleText()).contains("Summarize");
    }

    @Test
    void should_retain_last_messages_with_system_message() {
        ChatMemory memory = CompactingChatMemory.builder()
                .chatModel(summaryModel("summary"))
                .retainLastMessages(1)
                .executorService(DIRECT_EXECUTOR)
                .build();

        memory.add(systemMessage("Be helpful."));
        memory.add(userMessage("first"));
        memory.add(aiMessage("answer 1"));
        memory.add(userMessage("second"));

        List<ChatMessage> messages = memory.messages();
        // system message + summary + last 1 retained message
        assertThat(messages).hasSize(3);
        assertThat(messages.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(messages.get(1)).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) messages.get(1)).singleText()).isEqualTo("summary");
        assertThat(messages.get(2)).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) messages.get(2)).singleText()).isEqualTo("second");
    }

    @Test
    void should_not_compact_when_all_messages_fit_in_retain_window() {
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
                .retainLastMessages(10)
                .executorService(DIRECT_EXECUTOR)
                .build();

        memory.add(userMessage("first"));
        memory.add(aiMessage("answer"));
        memory.add(userMessage("second"));

        // All 3 messages fit in the retain window of 10, nothing to summarize (<=1 in toSummarize)
        assertThat(modelCalled.get()).isFalse();
        assertThat(memory.messages()).hasSize(3);
    }

    // ===== compactionInterval tests =====

    @Test
    void should_compact_only_on_nth_user_message() {
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
                .compactionInterval(3)
                .executorService(DIRECT_EXECUTOR)
                .build();

        // 1st user message - no compaction
        memory.add(userMessage("first"));
        memory.add(aiMessage("response 1"));
        assertThat(modelCalled.get()).isFalse();

        // 2nd user message - no compaction
        memory.add(userMessage("second"));
        memory.add(aiMessage("response 2"));
        assertThat(modelCalled.get()).isFalse();

        // 3rd user message - compaction triggered!
        memory.add(userMessage("third"));
        assertThat(modelCalled.get()).isTrue();

        // After compaction
        assertThat(memory.messages()).hasSize(1);
        assertThat(((UserMessage) memory.messages().get(0)).singleText()).isEqualTo("summary");
    }

    @Test
    void should_reset_interval_counter_after_compaction() {
        AtomicReference<Integer> callCount = new AtomicReference<>(0);

        ChatModel countingModel = new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest chatRequest) {
                callCount.updateAndGet(v -> v + 1);
                return ChatResponse.builder()
                        .aiMessage(aiMessage("summary"))
                        .metadata(ChatResponseMetadata.builder().build())
                        .build();
            }
        };

        ChatMemory memory = CompactingChatMemory.builder()
                .chatModel(countingModel)
                .compactionInterval(2)
                .executorService(DIRECT_EXECUTOR)
                .build();

        // First cycle: 2 user messages
        memory.add(userMessage("u1"));
        memory.add(aiMessage("a1"));
        memory.add(userMessage("u2"));
        assertThat(callCount.get()).isEqualTo(1);

        // Second cycle: 2 more user messages
        memory.add(aiMessage("a2"));
        memory.add(userMessage("u3"));
        assertThat(callCount.get()).isEqualTo(1); // not yet
        memory.add(aiMessage("a3"));
        memory.add(userMessage("u4"));
        assertThat(callCount.get()).isEqualTo(2); // triggered again
    }

    @Test
    void should_reject_invalid_compaction_interval() {
        assertThatThrownBy(() -> CompactingChatMemory.builder()
                .chatModel(summaryModel("summary"))
                .compactionInterval(0)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("compactionInterval");
    }

    // ===== compactToolMessages tests =====

    @Test
    void should_preserve_tool_messages_when_compact_tool_messages_is_false() {
        AtomicReference<List<ChatMessage>> capturedMessages = new AtomicReference<>();

        ChatModel capturingModel = new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest chatRequest) {
                capturedMessages.set(chatRequest.messages());
                return ChatResponse.builder()
                        .aiMessage(aiMessage("summary without tools"))
                        .metadata(ChatResponseMetadata.builder().build())
                        .build();
            }
        };

        ChatMemory memory = CompactingChatMemory.builder()
                .chatModel(capturingModel)
                .compactToolMessages(false)
                .executorService(DIRECT_EXECUTOR)
                .build();

        ToolExecutionRequest toolRequest = ToolExecutionRequest.builder()
                .id("1")
                .name("calculator")
                .arguments("{\"a\": 2, \"b\": 2}")
                .build();

        memory.add(userMessage("What is 2+2?"));
        memory.add(AiMessage.from(toolRequest));
        memory.add(ToolExecutionResultMessage.from(toolRequest, "4"));
        memory.add(aiMessage("2+2 is 4"));
        memory.add(userMessage("What about 3+3?"));

        List<ChatMessage> messages = memory.messages();

        // The tool pair (AiMessage with tool request + ToolExecutionResultMessage) should be preserved
        // Summary should be from non-tool messages: "What is 2+2?" and "2+2 is 4"
        // Result: summary + AiMessage(tool) + ToolExecutionResultMessage + aiMessage("2+2 is 4") + userMessage("What about 3+3?")
        // Actually: toSummarize = [userMessage("What is 2+2?"), aiMessage("2+2 is 4")], but that's only after
        // tool messages are moved to retain. Let's check...

        // The non-tool messages to summarize: userMessage("What is 2+2?") + aiMessage("2+2 is 4") = 2 messages
        // The tool pair + last user message go to retain
        assertThat(capturedMessages.get()).isNotNull();
        // Summarization request should NOT contain tool messages
        assertThat(capturedMessages.get().stream()
                .filter(ToolExecutionResultMessage.class::isInstance)).isEmpty();
        assertThat(capturedMessages.get().stream()
                .filter(m -> m instanceof AiMessage a && a.hasToolExecutionRequests())).isEmpty();

        // Memory should contain: summary + tool pair + remaining messages
        boolean hasToolResult = messages.stream().anyMatch(m -> m instanceof ToolExecutionResultMessage);
        assertThat(hasToolResult).isTrue();
    }

    @Test
    void should_compact_tool_messages_by_default() {
        AtomicReference<List<ChatMessage>> capturedMessages = new AtomicReference<>();

        ChatModel capturingModel = new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest chatRequest) {
                capturedMessages.set(chatRequest.messages());
                return ChatResponse.builder()
                        .aiMessage(aiMessage("summary with tools"))
                        .metadata(ChatResponseMetadata.builder().build())
                        .build();
            }
        };

        ChatMemory memory = CompactingChatMemory.builder()
                .chatModel(capturingModel)
                .executorService(DIRECT_EXECUTOR)
                .build();

        ToolExecutionRequest toolRequest = ToolExecutionRequest.builder()
                .id("1")
                .name("calculator")
                .arguments("{\"a\": 2, \"b\": 2}")
                .build();

        memory.add(userMessage("What is 2+2?"));
        memory.add(AiMessage.from(toolRequest));
        memory.add(ToolExecutionResultMessage.from(toolRequest, "4"));
        memory.add(aiMessage("2+2 is 4"));
        memory.add(userMessage("What about 3+3?"));

        // All messages including tool messages should be in the summarization request
        List<ChatMessage> sent = capturedMessages.get();
        assertThat(sent).isNotNull();
        assertThat(sent.stream().filter(ToolExecutionResultMessage.class::isInstance).count()).isEqualTo(1);
    }

    @Test
    void should_preserve_multiple_tool_pairs_when_compact_tool_messages_is_false() {
        ChatMemory memory = CompactingChatMemory.builder()
                .chatModel(summaryModel("summary"))
                .compactToolMessages(false)
                .executorService(DIRECT_EXECUTOR)
                .build();

        ToolExecutionRequest toolRequest1 = ToolExecutionRequest.builder()
                .id("1").name("tool1").arguments("{}").build();
        ToolExecutionRequest toolRequest2 = ToolExecutionRequest.builder()
                .id("2").name("tool2").arguments("{}").build();

        memory.add(userMessage("Do task A"));
        memory.add(aiMessage("Starting task A"));
        memory.add(userMessage("Now use tools"));
        memory.add(AiMessage.from(toolRequest1));
        memory.add(ToolExecutionResultMessage.from(toolRequest1, "result1"));
        memory.add(AiMessage.from(toolRequest2));
        memory.add(ToolExecutionResultMessage.from(toolRequest2, "result2"));
        memory.add(aiMessage("Done with tools"));
        memory.add(userMessage("Final question"));

        List<ChatMessage> messages = memory.messages();

        // Both tool pairs should be preserved
        long toolResultCount = messages.stream()
                .filter(m -> m instanceof ToolExecutionResultMessage)
                .count();
        assertThat(toolResultCount).isEqualTo(2);
    }

    // ===== maxTokens + tokenCountEstimator tests =====

    @Test
    void should_not_compact_when_under_token_limit() {
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
                .maxTokens(1000)
                .tokenCountEstimator(wordCountEstimator())
                .executorService(DIRECT_EXECUTOR)
                .build();

        memory.add(userMessage("short message"));
        memory.add(aiMessage("short reply"));
        memory.add(userMessage("another short one"));

        // Total words: ~6, well under 1000 tokens
        assertThat(modelCalled.get()).isFalse();
        assertThat(memory.messages()).hasSize(3);
    }

    @Test
    void should_compact_when_over_token_limit() {
        ChatMemory memory = CompactingChatMemory.builder()
                .chatModel(summaryModel("compact summary"))
                .maxTokens(5)
                .tokenCountEstimator(wordCountEstimator())
                .executorService(DIRECT_EXECUTOR)
                .build();

        memory.add(userMessage("this is a somewhat longer message with many words"));
        memory.add(aiMessage("and here is another response with plenty of words too"));
        memory.add(userMessage("yet another message that pushes us over the limit"));

        // Over token limit, compaction should have occurred
        List<ChatMessage> messages = memory.messages();
        // The summary should be present
        assertThat(messages.stream()
                .filter(m -> m instanceof UserMessage u && u.singleText().equals("compact summary"))
                .count()).isEqualTo(1);
    }

    @Test
    void should_retain_recent_messages_within_token_limit_when_compacting() {
        AtomicReference<List<ChatMessage>> capturedMessages = new AtomicReference<>();

        ChatModel capturingModel = new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest chatRequest) {
                capturedMessages.set(chatRequest.messages());
                return ChatResponse.builder()
                        .aiMessage(aiMessage("older stuff summary"))
                        .metadata(ChatResponseMetadata.builder().build())
                        .build();
            }
        };

        // Each word counts as 1 token. maxTokens=3 means retain messages up to 3 tokens
        ChatMemory memory = CompactingChatMemory.builder()
                .chatModel(capturingModel)
                .maxTokens(3)
                .tokenCountEstimator(wordCountEstimator())
                .executorService(DIRECT_EXECUTOR)
                .build();

        memory.add(userMessage("alpha beta")); // 2 tokens
        memory.add(aiMessage("gamma delta")); // 2 tokens
        memory.add(userMessage("epsilon")); // 1 token
        memory.add(aiMessage("zeta eta")); // 2 tokens
        memory.add(userMessage("theta")); // 1 token
        // Total = 8 tokens > 3 limit

        List<ChatMessage> messages = memory.messages();

        // "theta" (1 token) fits, "zeta eta" (2 tokens) fits (total 3), "epsilon" (1 token) won't fit (total 4)
        // So: summarize ["alpha beta", "gamma delta", "epsilon"], retain ["zeta eta", "theta"]
        assertThat(messages).hasSize(3); // summary + 2 retained
        assertThat(messages.get(0)).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) messages.get(0)).singleText()).isEqualTo("older stuff summary");
        assertThat(((AiMessage) messages.get(1)).text()).isEqualTo("zeta eta");
        assertThat(((UserMessage) messages.get(2)).singleText()).isEqualTo("theta");

        // Summarization request should contain only the older messages + compaction prompt
        List<ChatMessage> sent = capturedMessages.get();
        assertThat(sent).hasSize(4); // 3 older messages + compaction prompt
        assertThat(((UserMessage) sent.get(0)).singleText()).isEqualTo("alpha beta");
        assertThat(((AiMessage) sent.get(1)).text()).isEqualTo("gamma delta");
        assertThat(((UserMessage) sent.get(2)).singleText()).isEqualTo("epsilon");
    }

    @Test
    void should_reject_max_tokens_without_estimator() {
        assertThatThrownBy(() -> CompactingChatMemory.builder()
                .chatModel(summaryModel("summary"))
                .maxTokens(100)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tokenCountEstimator");
    }

    // ===== compactionPrompt tests =====

    @Test
    void should_use_custom_compaction_prompt() {
        AtomicReference<List<ChatMessage>> capturedMessages = new AtomicReference<>();

        ChatModel capturingModel = new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest chatRequest) {
                capturedMessages.set(chatRequest.messages());
                return ChatResponse.builder()
                        .aiMessage(aiMessage("custom summary"))
                        .metadata(ChatResponseMetadata.builder().build())
                        .build();
            }
        };

        String customPrompt = "Please create a brief recap.";
        ChatMemory memory = CompactingChatMemory.builder()
                .chatModel(capturingModel)
                .compactionPrompt(customPrompt)
                .executorService(DIRECT_EXECUTOR)
                .build();

        memory.add(userMessage("first"));
        memory.add(aiMessage("response"));
        memory.add(userMessage("second"));

        List<ChatMessage> sent = capturedMessages.get();
        assertThat(sent).isNotNull();
        UserMessage lastSent = (UserMessage) sent.get(sent.size() - 1);
        assertThat(lastSent.singleText()).isEqualTo(customPrompt);
    }

    // ===== Combined features tests =====

    @Test
    void should_combine_retain_last_messages_with_compact_tool_messages_false() {
        ChatMemory memory = CompactingChatMemory.builder()
                .chatModel(summaryModel("summary"))
                .retainLastMessages(1)
                .compactToolMessages(false)
                .executorService(DIRECT_EXECUTOR)
                .build();

        ToolExecutionRequest toolRequest = ToolExecutionRequest.builder()
                .id("1").name("search").arguments("{}").build();

        memory.add(userMessage("search for X"));
        memory.add(AiMessage.from(toolRequest));
        memory.add(ToolExecutionResultMessage.from(toolRequest, "found X"));
        memory.add(aiMessage("I found X for you"));
        memory.add(userMessage("tell me more about X"));

        List<ChatMessage> messages = memory.messages();

        // Tool messages should be preserved, last 1 message retained, rest summarized
        assertThat(messages.stream().filter(m -> m instanceof ToolExecutionResultMessage).count()).isEqualTo(1);
        // Last message should be the retained one
        assertThat(messages.get(messages.size() - 1)).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) messages.get(messages.size() - 1)).singleText()).isEqualTo("tell me more about X");
    }

    @Test
    void should_combine_compaction_interval_with_retain_last_messages() {
        AtomicReference<Integer> callCount = new AtomicReference<>(0);

        ChatModel countingModel = new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest chatRequest) {
                callCount.updateAndGet(v -> v + 1);
                return ChatResponse.builder()
                        .aiMessage(aiMessage("summary"))
                        .metadata(ChatResponseMetadata.builder().build())
                        .build();
            }
        };

        ChatMemory memory = CompactingChatMemory.builder()
                .chatModel(countingModel)
                .compactionInterval(2)
                .retainLastMessages(1)
                .executorService(DIRECT_EXECUTOR)
                .build();

        // 1st user message - no compaction (interval not reached)
        memory.add(userMessage("u1"));
        memory.add(aiMessage("a1"));
        assertThat(callCount.get()).isEqualTo(0);

        // 2nd user message - compaction triggered
        memory.add(userMessage("u2"));
        assertThat(callCount.get()).isEqualTo(1);

        // Result: summary + last 1 message ("u2")
        List<ChatMessage> messages = memory.messages();
        assertThat(messages).hasSize(2);
        assertThat(((UserMessage) messages.get(0)).singleText()).isEqualTo("summary");
        assertThat(((UserMessage) messages.get(1)).singleText()).isEqualTo("u2");
    }

    // ===== ChatMemoryStore tests =====

    @Test
    void should_use_provided_chat_memory_store() {
        AtomicBoolean storeUsed = new AtomicBoolean(false);

        dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore customStore =
                new dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore() {
                    @Override
                    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
                        storeUsed.set(true);
                        super.updateMessages(memoryId, messages);
                    }
                };

        ChatMemory memory = CompactingChatMemory.builder()
                .chatModel(summaryModel("summary"))
                .chatMemoryStore(customStore)
                .executorService(DIRECT_EXECUTOR)
                .build();

        memory.add(userMessage("hello"));
        assertThat(storeUsed.get()).isTrue();
    }

    // ===== Validation tests =====

    @Test
    void should_reject_negative_retain_last_messages() {
        assertThatThrownBy(() -> CompactingChatMemory.builder()
                .chatModel(summaryModel("summary"))
                .retainLastMessages(-1)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("retainLastMessages");
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
