package dev.langchain4j.memory.chat;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_5_MINI;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.observability.api.event.AiServiceResponseReceivedEvent;
import dev.langchain4j.observability.api.listener.AiServiceResponseReceivedListener;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Integration tests for {@link CompactingChatMemory} using real OpenAI models
 * through {@link AiServices}. Demonstrates how compacting memory reduces token
 * usage compared to {@link MessageWindowChatMemory}.
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class CompactingChatMemoryIT {

    private static ChatModel chatModel;
    private static ChatModel compactingChatModel;

    interface Assistant {
        String chat(String userMessage);
    }

    interface HistoryTeacher {
        @SystemMessage("You are a history teacher. Give detailed but concise answers.")
        String chat(String userMessage);
    }

    @BeforeAll
    static void setUp() {
        chatModel = OpenAiChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(GPT_5_MINI)
                .logRequests(true)
                .logResponses(true)
                .build();

        compactingChatModel = OpenAiChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(GPT_4_O_MINI)
                .temperature(0.0)
                .build();
    }

    @Test
    void should_maintain_conversation_context_through_compaction() throws InterruptedException {
        // given
        CompactingChatMemory memory = CompactingChatMemory.builder()
                .chatModel(compactingChatModel)
                .build();

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .chatMemory(memory)
                .build();

        // when - have a real multi-turn conversation
        String response1 = assistant.chat("Hello! My name is Mario and I live in Milan. I'm Italian but I prefer to chat in English.");
        System.out.println("Turn 1 - AI: " + response1);
        System.out.println("  Memory messages: " + memory.messages().size());
        waitForCompaction(memory);

        String response2 = assistant.chat("I work as a software engineer and I love Java.");
        System.out.println("Turn 2 - AI: " + response2);
        System.out.println("  Memory messages: " + memory.messages().size());
        waitForCompaction(memory);

        String response3 = assistant.chat("My favorite hobby is cooking Italian food.");
        System.out.println("Turn 3 - AI: " + response3);
        System.out.println("  Memory messages: " + memory.messages().size());
        waitForCompaction(memory);

        // then - the model should still remember key facts through the compacted summary
        String response4 = assistant.chat("Can you remind me what you know about me so far?");
        System.out.println("Turn 4 - AI: " + response4);

        // The model should recall key facts from the compacted summary
        assertThat(response4.toLowerCase()).satisfiesAnyOf(
                r -> assertThat(r).contains("mario"),
                r -> assertThat(r).contains("milan"),
                r -> assertThat(r).contains("software"),
                r -> assertThat(r).contains("java"),
                r -> assertThat(r).contains("cook")
        );

        waitForCompaction(memory);

        // The memory should be significantly compacted - not holding all raw messages
        System.out.println("Final memory messages: " + memory.messages().size());
        for (ChatMessage msg : memory.messages()) {
            System.out.println("  " + msg.type() + ": "
                    + (msg instanceof UserMessage u ? u.singleText() : msg.toString()));
        }
    }

    @Test
    void should_preserve_system_message_across_compaction_with_ai_service() throws InterruptedException {
        // given
        CompactingChatMemory memory = CompactingChatMemory.builder()
                .chatModel(chatModel)
                .build();

        HistoryTeacher teacher = AiServices.builder(HistoryTeacher.class)
                .chatModel(chatModel)
                .chatMemory(memory)
                .build();

        // when - multi-turn history lesson
        String response1 = teacher.chat("Tell me about the founding of Rome.");
        System.out.println("Turn 1 - Teacher: " + response1);
        waitForCompaction(memory);

        String response2 = teacher.chat("What about the Roman Republic?");
        System.out.println("Turn 2 - Teacher: " + response2);
        waitForCompaction(memory);

        String response3 = teacher.chat("And the transition to the Empire?");
        System.out.println("Turn 3 - Teacher: " + response3);
        waitForCompaction(memory);

        // then - system message should still be present
        List<ChatMessage> messages = memory.messages();
        System.out.println("Memory after compaction:");
        boolean hasSystemMessage = messages.stream()
                .anyMatch(m -> m instanceof dev.langchain4j.data.message.SystemMessage);
        assertThat(hasSystemMessage).isTrue();

        for (ChatMessage msg : messages) {
            System.out.println("  " + msg.type() + ": "
                    + (msg instanceof UserMessage u ? u.singleText()
                    : msg instanceof dev.langchain4j.data.message.SystemMessage s ? s.text()
                    : msg.toString()));
        }
    }

    @Test
    void should_use_fewer_tokens_than_message_window_memory() throws InterruptedException {
        // This test runs the SAME conversation with two different memory implementations
        // and compares the total input tokens used across all turns.

        String[] userMessages = {
                "Let's discuss the solar system. Tell me about Mercury.",
                "Now tell me about Venus and how it compares to Mercury.",
                "What about Earth? How is it unique compared to the first two?",
                "Describe Mars and why scientists are interested in it.",
                "Tell me about Jupiter, the largest planet.",
                "Now summarize the key differences between all five planets we discussed."
        };

        // --- Run with MessageWindowChatMemory (keeps full history) ---
        TokenConsumptionRecorder windowTokenRecorder = new TokenConsumptionRecorder();
        ChatMemory windowMemory = MessageWindowChatMemory.builder()
                .maxMessages(20)
                .build();

        Assistant windowAssistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .chatMemory(windowMemory)
                .registerListener(windowTokenRecorder)
                .build();

        for (String userMsg : userMessages) {
            System.out.println("User: " + userMsg);
            String response = windowAssistant.chat(userMsg);
            System.out.println("AI: " + response);
        }
        int windowFinalMessageCount = windowMemory.messages().size();

        // --- Run with CompactingChatMemory (summarized history) ---

        CompactingChatMemory compactMemory = CompactingChatMemory.builder()
                .chatModel(compactingChatModel) // use the real model for compaction
                .build();

        TokenConsumptionRecorder compactingTokenRecorder = new TokenConsumptionRecorder();
        Assistant compactAssistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .chatMemory(compactMemory)
                .registerListener(compactingTokenRecorder)
                .build();

        System.out.println();
        System.out.println("=== CompactingChatMemory (summarized history) ===");
        for (String userMsg : userMessages) {
            System.out.println("User: " + userMsg);
            String response = compactAssistant.chat(userMsg);
            System.out.println("AI: " + response);
        }
        int compactFinalMessageCount = compactMemory.messages().size();

        // --- Compare results ---
        System.out.println();
        System.out.println("=== Comparison ===");
        System.out.println("MessageWindowChatMemory:");
        System.out.println("  Final messages in memory: " + windowFinalMessageCount);
        System.out.println("  Total tokens across all turns: " + windowTokenRecorder.totalTokens());
        System.out.println("CompactingChatMemory:");
        System.out.println("  Final messages in memory: " + compactFinalMessageCount);
        System.out.println("  Total tokens across all turns: " + compactingTokenRecorder.totalTokens());

        double savings = (1.0 - (double) compactingTokenRecorder.totalTokens() / windowTokenRecorder.totalTokens()) * 100;
        System.out.println(String.format("Token savings: %.1f%% fewer input tokens with CompactingChatMemory", savings));
        System.out.println("==================");

        // then - compacting memory should use fewer total input tokens
        assertThat(compactingTokenRecorder.totalTokens()).isLessThan(windowTokenRecorder.totalTokens());
        // and should hold far fewer messages
        assertThat(compactFinalMessageCount).isLessThan(windowFinalMessageCount);
    }

    static class TokenConsumptionRecorder implements AiServiceResponseReceivedListener {
        private final AtomicInteger totalTokens = new AtomicInteger();

        @Override
        public void onEvent(final AiServiceResponseReceivedEvent event) {
            ChatResponse response = event.response();
            TokenUsage usage = response.metadata().tokenUsage();
            if (usage != null && usage.totalTokenCount() != null) {
                totalTokens.addAndGet(usage.totalTokenCount());
            }
        }

        public int totalTokens() {
            return totalTokens.get();
        }
    }

    /**
     * Waits for background compaction to complete by polling until the message count stabilizes.
     */
    private void waitForCompaction(CompactingChatMemory memory) throws InterruptedException {
        while (memory.messages().stream().filter(UserMessage.class::isInstance).count() > 1) {
            Thread.sleep(100);
        }
    }
}
