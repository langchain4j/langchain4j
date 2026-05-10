package dev.langchain4j.service.guardrail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailException;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import org.junit.jupiter.api.Test;

class OutputGuardrailRemoveViolatingMessageTests extends BaseGuardrailTests {

    @Test
    void failureWithoutFlagLeavesViolatingMessageInMemory() {
        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(10);
        MyAiService aiService = createAiService(
                MyAiService.class,
                builder -> builder.chatModel(new MyChatModel()).chatMemoryProvider(id -> memory));

        assertThatExceptionOfType(OutputGuardrailException.class)
                .isThrownBy(() -> aiService.guardedWithPlainFailure("mem1"));

        // The violating AiMessage must still be in memory (default behaviour — no removal)
        assertThat(memory.messages()).hasSize(2).last().isInstanceOf(AiMessage.class);
    }

    @Test
    void failureWithRemovalFlagRemovesViolatingMessageFromMemory() {
        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(10);
        MyAiService aiService = createAiService(
                MyAiService.class,
                builder -> builder.chatModel(new MyChatModel()).chatMemoryProvider(id -> memory));

        assertThatExceptionOfType(OutputGuardrailException.class)
                .isThrownBy(() -> aiService.guardedWithRemoval("mem2"));

        // After failure + removal flag: only the UserMessage remains, AiMessage was removed
        assertThat(memory.messages()).hasSize(1).first().isInstanceOf(dev.langchain4j.data.message.UserMessage.class);
    }

    @Test
    void exceptionCarriesResultWhenRemovalIsRequested() {
        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(10);
        MyAiService aiService = createAiService(
                MyAiService.class,
                builder -> builder.chatModel(new MyChatModel()).chatMemoryProvider(id -> memory));

        OutputGuardrailException ex = org.junit.jupiter.api.Assertions.assertThrows(
                OutputGuardrailException.class, () -> aiService.guardedWithRemoval("mem3"));
        assertThat(ex.result()).isNotNull();
        assertThat(ex.result().shouldRemoveViolatingMessage()).isTrue();
    }

    @Test
    void fatalWithRemovalFlagRemovesViolatingMessageFromMemory() {
        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(10);
        MyAiService aiService = createAiService(
                MyAiService.class,
                builder -> builder.chatModel(new MyChatModel()).chatMemoryProvider(id -> memory));

        assertThatExceptionOfType(OutputGuardrailException.class)
                .isThrownBy(() -> aiService.guardedWithFatalRemoval("mem4"));

        assertThat(memory.messages()).hasSize(1).first().isInstanceOf(dev.langchain4j.data.message.UserMessage.class);
    }

    @Test
    void secondCallAfterRemovalUsesCleanMemory() {
        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(10);
        MyAiService aiService = createAiService(
                MyAiService.class,
                builder -> builder.chatModel(new MyChatModel()).chatMemoryProvider(id -> memory));

        // First call fails and removes violating message
        assertThatExceptionOfType(OutputGuardrailException.class)
                .isThrownBy(() -> aiService.guardedWithRemoval("mem5"));

        assertThat(memory.messages()).hasSize(1);

        // Second call: only the first UserMessage + new UserMessage + new AiMessage in memory
        // The old violating AiMessage is gone
        assertThatExceptionOfType(OutputGuardrailException.class)
                .isThrownBy(() -> aiService.guardedWithRemoval("mem5"));

        // After second failure + removal: 2 UserMessages remain (no AiMessages)
        assertThat(memory.messages()).hasSize(2);
        assertThat(memory.messages())
                .allSatisfy(m -> assertThat(m).isInstanceOf(dev.langchain4j.data.message.UserMessage.class));
    }

    public interface MyAiService {
        @UserMessage("Say Hi!")
        @OutputGuardrails(PlainFailureGuardrail.class)
        String guardedWithPlainFailure(@MemoryId String mem);

        @UserMessage("Say Hi!")
        @OutputGuardrails(RemovalFailureGuardrail.class)
        String guardedWithRemoval(@MemoryId String mem);

        @UserMessage("Say Hi!")
        @OutputGuardrails(FatalRemovalGuardrail.class)
        String guardedWithFatalRemoval(@MemoryId String mem);
    }

    public static class PlainFailureGuardrail implements OutputGuardrail {
        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            return failure("plain failure — no removal");
        }
    }

    public static class RemovalFailureGuardrail implements OutputGuardrail {
        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            return failureWithMessageRemoval("violated — remove from memory");
        }
    }

    public static class FatalRemovalGuardrail implements OutputGuardrail {
        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            return fatalWithMessageRemoval("fatal violation — remove from memory");
        }
    }
}
