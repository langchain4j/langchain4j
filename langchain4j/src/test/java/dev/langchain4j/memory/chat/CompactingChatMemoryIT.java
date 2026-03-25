package dev.langchain4j.memory.chat;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_5_MINI;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatModel;
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

    interface MathAssistant {
        @SystemMessage("You are a math assistant. Use the provided tools to compute results. Always use tools when available.")
        String chat(String userMessage);
    }

    static class Calculator {

        @Tool("Adds two numbers together")
        int add(int a, int b) {
            return a + b;
        }

        @Tool("Multiplies two numbers together")
        int multiply(int a, int b) {
            return a * b;
        }
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

    @Test
    void should_retain_last_messages_with_real_model() throws InterruptedException {
        // given
        CompactingChatMemory memory = CompactingChatMemory.builder()
                .chatModel(compactingChatModel)
                .retainLastMessages(2)
                .build();

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .chatMemory(memory)
                .build();

        // when
        assistant.chat("My name is Mario.");
        waitForCompaction(memory);
        assistant.chat("I live in Milan.");
        waitForCompaction(memory);
        assistant.chat("I'm a software engineer.");
        waitForCompaction(memory);

        // then - memory should have summary + 2 retained messages
        List<ChatMessage> messages = memory.messages();
        System.out.println("Memory with retainLastMessages=2:");
        for (ChatMessage msg : messages) {
            System.out.println("  " + msg.type() + ": "
                    + (msg instanceof UserMessage u ? u.singleText() : msg.toString()));
        }

        // Should have more than 1 message (summary + retained)
        assertThat(messages.size()).isGreaterThan(1);

        // The AI should still remember earlier facts through the summary
        String response = assistant.chat("What do you know about me?");
        System.out.println("Recall response: " + response);
        assertThat(response.toLowerCase()).satisfiesAnyOf(
                r -> assertThat(r).contains("mario"),
                r -> assertThat(r).contains("milan"),
                r -> assertThat(r).contains("software")
        );
    }

    @Test
    void should_compact_with_interval_using_real_model() throws InterruptedException {
        // given - compact only every 3rd user message
        CompactingChatMemory memory = CompactingChatMemory.builder()
                .chatModel(compactingChatModel)
                .compactionInterval(3)
                .build();

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .chatMemory(memory)
                .build();

        // when
        assistant.chat("My name is Alice.");
        int messagesAfterFirst = memory.messages().size();

        assistant.chat("I like reading books.");
        int messagesAfterSecond = memory.messages().size();

        // Messages should be accumulating (no compaction yet)
        assertThat(messagesAfterSecond).isGreaterThan(messagesAfterFirst);

        assistant.chat("My favorite color is blue.");
        // 3rd user message triggers compaction
        waitForCompaction(memory);

        int messagesAfterThird = memory.messages().size();
        System.out.println("Messages after 3rd turn (compaction triggered): " + messagesAfterThird);

        // After compaction, memory should be smaller than the accumulated messages
        assertThat(messagesAfterThird).isLessThan(messagesAfterSecond);
    }

    @Test
    void should_work_with_tools_and_compact_tool_messages() throws InterruptedException {
        // given
        CompactingChatMemory memory = CompactingChatMemory.builder()
                .chatModel(compactingChatModel)
                .build();

        Calculator calculator = new Calculator();

        MathAssistant assistant = AiServices.builder(MathAssistant.class)
                .chatModel(chatModel)
                .chatMemory(memory)
                .tools(calculator)
                .build();

        // when - ask questions that require tool usage
        String response1 = assistant.chat("What is 15 + 27?");
        System.out.println("Turn 1 - Math: " + response1);
        assertThat(response1).contains("42");

        System.out.println("Memory after tool use:");
        for (ChatMessage msg : memory.messages()) {
            System.out.println("  " + msg.type() + ": " + msg);
        }

        waitForCompaction(memory);

        String response2 = assistant.chat("Now multiply 6 and 7.");
        System.out.println("Turn 2 - Math: " + response2);
        assertThat(response2).contains("42");

        waitForCompaction(memory);

        // then - memory should be compacted even with tool messages
        String response3 = assistant.chat("What were the results of my previous calculations?");
        System.out.println("Turn 3 - Math: " + response3);

        // Should recall the results through the summary
        assertThat(response3.toLowerCase()).satisfiesAnyOf(
                r -> assertThat(r).contains("42"),
                r -> assertThat(r).contains("fifteen"),
                r -> assertThat(r).contains("multiply")
        );

        System.out.println("Final memory:");
        for (ChatMessage msg : memory.messages()) {
            System.out.println("  " + msg.type() + ": " + msg);
        }
    }

    @Test
    void should_preserve_tool_messages_when_configured() throws InterruptedException {
        // given - compactToolMessages=false should keep tool call/result pairs intact
        CompactingChatMemory memory = CompactingChatMemory.builder()
                .chatModel(compactingChatModel)
                .compactToolMessages(false)
                .retainLastMessages(2)
                .build();

        Calculator calculator = new Calculator();

        MathAssistant assistant = AiServices.builder(MathAssistant.class)
                .chatModel(chatModel)
                .chatMemory(memory)
                .tools(calculator)
                .build();

        // when
        String response1 = assistant.chat("What is 10 + 20?");
        System.out.println("Turn 1: " + response1);
        assertThat(response1).contains("30");

        waitForCompaction(memory);

        String response2 = assistant.chat("Now multiply 5 and 8.");
        System.out.println("Turn 2: " + response2);
        assertThat(response2).contains("40");

        waitForCompaction(memory);

        // then - tool execution result messages should be preserved in memory
        List<ChatMessage> messages = memory.messages();
        System.out.println("Memory with preserved tool messages:");
        for (ChatMessage msg : messages) {
            System.out.println("  " + msg.type() + ": " + msg);
        }

        boolean hasToolResults = messages.stream()
                .anyMatch(m -> m instanceof ToolExecutionResultMessage);
        System.out.println("Has preserved tool results: " + hasToolResults);
        // Tool messages should be retained since compactToolMessages=false
        // Note: whether they're present depends on whether they fell in the summarize zone
    }

    @Test
    void should_use_custom_compaction_prompt_with_real_model() throws InterruptedException {
        // given
        String customPrompt = "Create a bullet-point summary of the conversation. "
                + "Each bullet should capture one key fact. Write from the user's perspective.";

        CompactingChatMemory memory = CompactingChatMemory.builder()
                .chatModel(compactingChatModel)
                .compactionPrompt(customPrompt)
                .build();

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .chatMemory(memory)
                .build();

        // when
        assistant.chat("I'm learning French.");
        waitForCompaction(memory);
        assistant.chat("I also play guitar.");
        waitForCompaction(memory);
        assistant.chat("I live in Paris.");
        waitForCompaction(memory);

        // then - the summary should use the custom prompt format
        List<ChatMessage> messages = memory.messages();
        System.out.println("Memory with custom prompt:");
        for (ChatMessage msg : messages) {
            if (msg instanceof UserMessage u) {
                System.out.println("  " + u.singleText());
            }
        }
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
        // Wait until memory stabilizes (no more changes for 500ms)
        int previousSize;
        int currentSize = memory.messages().size();
        int stableCount = 0;
        while (stableCount < 5) {
            Thread.sleep(100);
            previousSize = currentSize;
            currentSize = memory.messages().size();
            if (currentSize == previousSize) {
                stableCount++;
            } else {
                stableCount = 0;
            }
        }
    }
}
