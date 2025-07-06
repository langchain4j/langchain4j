package dev.langchain4j.service.guardrail;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.OutputGuardrailValidation;
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

class OutputGuardrailPromptTemplateTests extends BaseGuardrailTests {
    @BeforeEach
    void beforeEach() {
        OutputGuardrailValidation.getInstance().reset();
    }

    @ParameterizedTest
    @MethodSource("services")
    void shouldWorkNoParameters(String testDescription, MyAiService aiService) {
        aiService.getJoke();
        assertThat(OutputGuardrailValidation.getInstance().spyUserMessageTemplate())
                .isEqualTo("Tell me a joke");
        assertThat(OutputGuardrailValidation.getInstance().spyVariables()).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("services")
    void shouldWorkWithMemoryId(String testDescription, MyAiService aiService) {
        aiService.getAnotherJoke("memory-id-001");
        assertThat(OutputGuardrailValidation.getInstance().spyUserMessageTemplate())
                .isEqualTo("Tell me another joke");
        assertThat(OutputGuardrailValidation.getInstance().spyVariables())
                .containsExactlyInAnyOrderEntriesOf(Map.of("arg0", "memory-id-001"));
    }

    @ParameterizedTest
    @MethodSource("services")
    void shouldWorkWithNoMemoryIdAndOneParameter(String testDescription, MyAiService aiService) {
        aiService.sayHiToMyFriendNoMemory("Rambo");
        assertThat(OutputGuardrailValidation.getInstance().spyUserMessageTemplate())
                .isEqualTo("Say hi to my friend {{it}}!");
        assertThat(OutputGuardrailValidation.getInstance().spyVariables())
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "arg0", "Rambo",
                        "it", "Rambo"));
    }

    @ParameterizedTest
    @MethodSource("services")
    void shouldWorkWithMemoryIdAndOneParameter(String testDescription, MyAiService aiService) {
        aiService.sayHiToMyFriend("1", "Chuck Norris");
        assertThat(OutputGuardrailValidation.getInstance().spyUserMessageTemplate())
                .isEqualTo("Say hi to my friend {{friend}}!");
        assertThat(OutputGuardrailValidation.getInstance().spyVariables())
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "friend", "Chuck Norris",
                        "arg0", "1"));
    }

    @ParameterizedTest
    @MethodSource("services")
    void shouldWorkWithNoMemoryIdAndThreeParameters(String testDescription, MyAiService aiService) {
        aiService.sayHiToMyFriends("Chuck Norris", "Jean-Claude Van Damme", "Silvester Stallone");
        assertThat(OutputGuardrailValidation.getInstance().spyUserMessageTemplate())
                .isEqualTo("Tell me something about {{topic1}}, {{topic2}}, {{topic3}}!");
        assertThat(OutputGuardrailValidation.getInstance().spyVariables())
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "topic1", "Chuck Norris",
                        "topic2", "Jean-Claude Van Damme",
                        "topic3", "Silvester Stallone"));
    }

    @ParameterizedTest
    @MethodSource("services")
    void shouldWorkWithNoMemoryIdAndList(String testDescription, MyAiService aiService) {
        aiService.sayHiToMyFriends(List.of("Chuck Norris", "Jean-Claude Van Damme", "Silvester Stallone"));

        assertThat(OutputGuardrailValidation.getInstance().spyUserMessageTemplate())
                .isEqualTo("Tell me something about {{it}}!");
        assertThat(OutputGuardrailValidation.getInstance().spyVariables())
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "arg0",
                        List.of("Chuck Norris", "Jean-Claude Van Damme", "Silvester Stallone"),
                        "it",
                        "[Chuck Norris, Jean-Claude Van Damme, Silvester Stallone]"));
    }

    @ParameterizedTest
    @MethodSource("services")
    void shouldWorkWithMemoryIdAndList(String testDescription, MyAiService aiService) {
        aiService.sayHiToMyFriends(
                "memory-id-007", List.of("Chuck Norris", "Jean-Claude Van Damme", "Silvester Stallone"));

        assertThat(OutputGuardrailValidation.getInstance().spyUserMessageTemplate())
                .isEqualTo("Tell me something about {{topics}}! This is my memory id: {{memoryId}}");
        assertThat(OutputGuardrailValidation.getInstance().spyVariables())
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "topics",
                        List.of("Chuck Norris", "Jean-Claude Van Damme", "Silvester Stallone"),
                        "memoryId",
                        "memory-id-007"));
    }

    static Stream<Arguments> services() {
        return Stream.of(
                Arguments.of("AiService with class-level annotation", ClassLevelAiService.create()),
                Arguments.of("AiService with method-level annotations", MethodLevelAiService.create()),
                Arguments.of("AiService with builder-style guardrails", MyAiService.create()));
    }

    @OutputGuardrails(OutputGuardrailValidation.class)
    public interface ClassLevelAiService extends MyAiService {
        static MyAiService create() {
            return createAiService(ClassLevelAiService.class, builder -> builder.chatModel(new MyChatModel()));
        }
    }

    public interface MethodLevelAiService extends MyAiService {
        @Override
        @UserMessage("Tell me a joke")
        @OutputGuardrails(OutputGuardrailValidation.class)
        String getJoke();

        @Override
        @UserMessage("Tell me another joke")
        @OutputGuardrails(OutputGuardrailValidation.class)
        String getAnotherJoke(@MemoryId String memoryId);

        @Override
        @UserMessage("Say hi to my friend {{it}}!")
        @OutputGuardrails(OutputGuardrailValidation.class)
        String sayHiToMyFriendNoMemory(String friend);

        @Override
        @UserMessage("Say hi to my friend {{friend}}!")
        @OutputGuardrails(OutputGuardrailValidation.class)
        String sayHiToMyFriend(@MemoryId String mem, @V("friend") String friend);

        @Override
        @UserMessage("Tell me something about {{topic1}}, {{topic2}}, {{topic3}}!")
        @OutputGuardrails(OutputGuardrailValidation.class)
        String sayHiToMyFriends(@V("topic1") String topic1, @V("topic2") String topic2, @V("topic3") String topic3);

        @Override
        @UserMessage("Tell me something about {{it}}!")
        @OutputGuardrails(OutputGuardrailValidation.class)
        String sayHiToMyFriends(List<String> topics);

        @Override
        @UserMessage("Tell me something about {{topics}}! This is my memory id: {{memoryId}}")
        @OutputGuardrails(OutputGuardrailValidation.class)
        String sayHiToMyFriends(@V("memoryId") @MemoryId String memoryId, @V("topics") List<String> topics);

        static MyAiService create() {
            return createAiService(MethodLevelAiService.class, builder -> builder.chatModel(new MyChatModel()));
        }
    }

    public interface MyAiService {
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

        static MyAiService create() {
            return AiServices.builder(MyAiService.class)
                    .chatModel(new MyChatModel())
                    .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10))
                    .outputGuardrails(OutputGuardrailValidation.getInstance())
                    .build();
        }
    }
}
