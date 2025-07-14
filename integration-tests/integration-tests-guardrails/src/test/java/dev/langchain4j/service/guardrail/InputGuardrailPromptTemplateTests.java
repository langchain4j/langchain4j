package dev.langchain4j.service.guardrail;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.InputGuardrailValidation;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class InputGuardrailPromptTemplateTests {
    @BeforeEach
    void beforeEach() {
        InputGuardrailValidation.getInstance().reset();
    }

    @ParameterizedTest
    @MethodSource("assistants")
    void shouldWorkNoParameters(String testDescription, Assistant aiService) {
        assertThat(aiService.getJoke()).isEqualTo("Request: Tell me a joke; Response: Hi!");
        assertThat(InputGuardrailValidation.getInstance().spyUserMessageTemplate())
                .isEqualTo("Tell me a joke");
        assertThat(InputGuardrailValidation.getInstance().spyVariables()).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("assistants")
    void shouldWorkWithMemoryId(String testDescription, Assistant aiService) {
        aiService.getAnotherJoke("memory-id-001");
        assertThat(InputGuardrailValidation.getInstance().spyUserMessageTemplate())
                .isEqualTo("Tell me another joke");
        assertThat(InputGuardrailValidation.getInstance().spyVariables())
                .containsExactlyInAnyOrderEntriesOf(Map.of("arg0", "memory-id-001"));
    }

    @ParameterizedTest
    @MethodSource("assistants")
    void shouldWorkWithNoMemoryIdAndOneParameter(String testDescription, Assistant aiService) {
        aiService.sayHiToMyFriendNoMemory("Rambo");
        assertThat(InputGuardrailValidation.getInstance().spyUserMessageTemplate())
                .isEqualTo("Say hi to my friend {{it}}!");
        assertThat(InputGuardrailValidation.getInstance().spyVariables())
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "arg0", "Rambo",
                        "it", "Rambo"));
    }

    @ParameterizedTest
    @MethodSource("assistants")
    void shouldWorkWithMemoryIdAndOneParameter(String testDescription, Assistant aiService) {
        aiService.sayHiToMyFriend("1", "Chuck Norris");
        assertThat(InputGuardrailValidation.getInstance().spyUserMessageTemplate())
                .isEqualTo("Say hi to my friend {{friend}}!");
        assertThat(InputGuardrailValidation.getInstance().spyVariables())
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "friend", "Chuck Norris",
                        "arg0", "1"));
    }

    @ParameterizedTest
    @MethodSource("assistants")
    void shouldWorkWithNoMemoryIdAndThreeParameters(String testDescription, Assistant aiService) {
        aiService.sayHiToMyFriends("Chuck Norris", "Jean-Claude Van Damme", "Silvester Stallone");
        assertThat(InputGuardrailValidation.getInstance().spyUserMessageTemplate())
                .isEqualTo("Tell me something about {{topic1}}, {{topic2}}, {{topic3}}!");
        assertThat(InputGuardrailValidation.getInstance().spyVariables())
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "topic1", "Chuck Norris",
                        "topic2", "Jean-Claude Van Damme",
                        "topic3", "Silvester Stallone"));
    }

    @ParameterizedTest
    @MethodSource("assistants")
    void shouldWorkWithNoMemoryIdAndList(String testDescription, Assistant aiService) {
        aiService.sayHiToMyFriends(List.of("Chuck Norris", "Jean-Claude Van Damme", "Silvester Stallone"));
        assertThat(InputGuardrailValidation.getInstance().spyUserMessageText())
                .isEqualTo("Tell me something about [Chuck Norris, Jean-Claude Van Damme, Silvester Stallone]!");
        assertThat(InputGuardrailValidation.getInstance().spyUserMessageTemplate())
                .isEqualTo("Tell me something about {{it}}!");
        assertThat(InputGuardrailValidation.getInstance().spyVariables())
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "arg0",
                        List.of("Chuck Norris", "Jean-Claude Van Damme", "Silvester Stallone"),
                        "it",
                        "[Chuck Norris, Jean-Claude Van Damme, Silvester Stallone]"));
    }

    @ParameterizedTest
    @MethodSource("assistants")
    void shouldWorkWithMemoryIdAndList(String testDescription, Assistant aiService) {
        aiService.sayHiToMyFriends(
                "memory-id-007", List.of("Chuck Norris", "Jean-Claude Van Damme", "Silvester Stallone"));
        assertThat(InputGuardrailValidation.getInstance().spyUserMessageText())
                .isEqualTo(
                        "Tell me something about [Chuck Norris, Jean-Claude Van Damme, Silvester Stallone]! This is my memory id: memory-id-007");
        assertThat(InputGuardrailValidation.getInstance().spyUserMessageTemplate())
                .isEqualTo("Tell me something about {{topics}}! This is my memory id: {{memoryId}}");
        assertThat(InputGuardrailValidation.getInstance().spyVariables())
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "topics",
                        List.of("Chuck Norris", "Jean-Claude Van Damme", "Silvester Stallone"),
                        "memoryId",
                        "memory-id-007"));
    }

    static Stream<Arguments> assistants() {
        return Stream.of(
                Arguments.of("Assistant with class-level annotation", ClassLevelAssistant.create()),
                Arguments.of("Assistant with method-level annotations", MethodLevelAssistant.create()),
                Arguments.of("Assistant with builder-style guardrail", Assistant.create()));
    }

    @InputGuardrails(InputGuardrailValidation.class)
    interface ClassLevelAssistant extends Assistant {
        static Assistant create() {
            return Assistant.create(ClassLevelAssistant.class);
        }
    }

    interface MethodLevelAssistant extends Assistant {
        @InputGuardrails(InputGuardrailValidation.class)
        @UserMessage("Tell me a joke")
        @Override
        String getJoke();

        @UserMessage("Tell me another joke")
        @InputGuardrails(InputGuardrailValidation.class)
        @Override
        String getAnotherJoke(@MemoryId String memoryId);

        @UserMessage("Say hi to my friend {{it}}!")
        @InputGuardrails(InputGuardrailValidation.class)
        @Override
        String sayHiToMyFriendNoMemory(String friend);

        @UserMessage("Say hi to my friend {{friend}}!")
        @InputGuardrails(InputGuardrailValidation.class)
        @Override
        String sayHiToMyFriend(@MemoryId String mem, @V("friend") String friend);

        @UserMessage("Tell me something about {{topic1}}, {{topic2}}, {{topic3}}!")
        @InputGuardrails(InputGuardrailValidation.class)
        @Override
        String sayHiToMyFriends(@V("topic1") String topic1, @V("topic2") String topic2, @V("topic3") String topic3);

        @UserMessage("Tell me something about {{it}}!")
        @InputGuardrails(InputGuardrailValidation.class)
        @Override
        String sayHiToMyFriends(List<String> topics);

        @UserMessage("Tell me something about {{topics}}! This is my memory id: {{memoryId}}")
        @InputGuardrails(InputGuardrailValidation.class)
        @Override
        String sayHiToMyFriends(@V("memoryId") @MemoryId String memoryId, @V("topics") List<String> topics);

        static Assistant create() {
            return Assistant.create(MethodLevelAssistant.class);
        }
    }

    interface Assistant {
        @UserMessage("Tell me a joke")
        String getJoke();

        @UserMessage("Tell me another joke")
        String getAnotherJoke(@MemoryId String memoryId);

        @UserMessage("Say hi to my friend {{it}}!")
        String sayHiToMyFriendNoMemory(String friend);

        @UserMessage("Say hi to my friend {{friend}}!")
        String sayHiToMyFriend(@MemoryId String mem, @V("friend") String friend);

        @UserMessage("Tell me something about {{topic1}}, {{topic2}}, {{topic3}}!")
        String sayHiToMyFriends(@V("topic1") String topic1, @V("topic2") String topic2, @V("topic3") String topic3);

        @UserMessage("Tell me something about {{it}}!")
        String sayHiToMyFriends(List<String> topics);

        @UserMessage("Tell me something about {{topics}}! This is my memory id: {{memoryId}}")
        String sayHiToMyFriends(@V("memoryId") @MemoryId String memoryId, @V("topics") List<String> topics);

        static <T extends Assistant> T create(Class<T> clazz) {
            return AiServices.builder(clazz)
                    .chatModel(new MyChatModel())
                    .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10))
                    .build();
        }

        static Assistant create() {
            return AiServices.builder(Assistant.class)
                    .chatModel(new MyChatModel())
                    .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10))
                    .inputGuardrails(List.of(InputGuardrailValidation.getInstance()))
                    .build();
        }
    }
}
