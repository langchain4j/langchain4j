package dev.langchain4j.service.guardrail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.GuardrailException;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.AiServices;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AiServiceGuardrailTests {
    @Test
    void noGuardrails() {
        var noGuardrails = Assistant.create();

        assertThat(noGuardrails.chat("Hello!")).isEqualTo("Request: Hello!; Response: Hi!");
        assertThat(noGuardrails.chat2("Hello!")).isEqualTo("Request: Hello!; Response: Hi!");
    }

    @ParameterizedTest
    @MethodSource("classLevelAssistants")
    void classLevelAssistants(String testDescription, Assistant assistant) {
        assertThatExceptionOfType(GuardrailException.class)
                .isThrownBy(() -> assistant.chat("Hello!"))
                .withMessageContaining(
                        "The guardrail %s failed with this message: Request: Hello! from %s; Response: Hi! failure from %s",
                        OutputGuardrailFail.class.getName(),
                        InputGuardrailSuccess.class.getSimpleName(),
                        OutputGuardrailFail.class.getSimpleName());
        assertThatExceptionOfType(GuardrailException.class)
                .isThrownBy(() -> assistant.chat2("Hello!"))
                .withMessageContaining(
                        "The guardrail %s failed with this message: Request: Hello! from %s; Response: Hi! failure from %s",
                        OutputGuardrailFail.class.getName(),
                        InputGuardrailSuccess.class.getSimpleName(),
                        OutputGuardrailFail.class.getSimpleName());
    }

    @Test
    void methodLevelAssistant() {
        var assistant = MethodLevelAssistant.create();

        assertThat(assistant.chat("Hello!"))
                .isEqualTo(
                        "Request: Hello! from %s; Response: Hi! from %s",
                        InputGuardrailSuccess.class.getSimpleName(), OutputGuardrailSuccess.class.getSimpleName());
        assertThatExceptionOfType(GuardrailException.class)
                .isThrownBy(() -> assistant.chat2("Hello!"))
                .withMessage(
                        "The guardrail %s failed with this message: Hello! failure from %s",
                        InputGuardrailFail.class.getName(), InputGuardrailFail.class.getSimpleName());
    }

    @Test
    void anotherMethodLevelAssistant() {
        var assistant = MethodLevelAssistant1.create();

        assertThatExceptionOfType(GuardrailException.class)
                .isThrownBy(() -> assistant.chat("Hello!"))
                .withMessage(
                        "The guardrail %s failed with this message: Hello! from %s failure from %s",
                        InputGuardrailFail.class.getName(),
                        InputGuardrailSuccess.class.getSimpleName(),
                        InputGuardrailFail.class.getSimpleName());

        assertThat(assistant.chat2("Hello!")).isEqualTo("Request: Hello!; Response: Hi!");
    }

    @Test
    void classAndMethodLevelAssistant() {
        var assistant = ClassAndMethodLevelAssistant.create();

        assertThatExceptionOfType(GuardrailException.class)
                .isThrownBy(() -> assistant.chat("Hello!"))
                .withMessage(
                        "The guardrail %s failed with this message: Hello! from %s failure from %s",
                        InputGuardrailFail.class.getName(),
                        InputGuardrailSuccess.class.getSimpleName(),
                        InputGuardrailFail.class.getSimpleName());

        assertThat(assistant.chat2("Hello!"))
                .isEqualTo(
                        "Request: Hello! from %s; Response: Hi! from %s",
                        InputGuardrailSuccess.class.getSimpleName(), OutputGuardrailSuccess.class.getSimpleName());
    }

    static Stream<Arguments> classLevelAssistants() {
        return Stream.of(
                Arguments.of("assistant with class-level annotations", ClassLevelAssistant.create()),
                Arguments.of("assistant with method-level annotations", SameMethodLevelAssistant.create()),
                Arguments.of(
                        "assistant with guardrail classes defined (class-level)",
                        ClassLevelAssistant.createUsingClassNames()),
                Arguments.of(
                        "assistant with guardrail instances defined (class-level)",
                        ClassLevelAssistant.createUsingClassInstances()),
                Arguments.of(
                        "assistant with guardrail classes defined (method-level)",
                        SameMethodLevelAssistant.createUsingClassNames()),
                Arguments.of(
                        "assistant with guardrail instances defined (method-level)",
                        SameMethodLevelAssistant.createUsingClassInstances()));
    }

    interface Assistant {
        String chat(String message);

        String chat2(String message);

        static <T extends Assistant> T create(Class<T> clazz) {
            return AiServices.create(clazz, new MyChatModel());
        }

        static Assistant create() {
            return create(Assistant.class);
        }
    }

    @InputGuardrails(InputGuardrailSuccess.class)
    @OutputGuardrails(OutputGuardrailFail.class)
    interface ClassLevelAssistant extends Assistant {
        static Assistant create() {
            return AiServices.create(ClassLevelAssistant.class, new MyChatModel());
        }

        static Assistant createUsingClassNames() {
            return AiServices.builder(ClassLevelAssistant.class)
                    .chatModel(new MyChatModel())
                    .inputGuardrailClasses(InputGuardrailSuccess.class)
                    .outputGuardrailClasses(OutputGuardrailFail.class)
                    .build();
        }

        static Assistant createUsingClassInstances() {
            return AiServices.builder(ClassLevelAssistant.class)
                    .chatModel(new MyChatModel())
                    .inputGuardrails(new InputGuardrailSuccess())
                    .outputGuardrails(new OutputGuardrailFail())
                    .build();
        }
    }

    interface SameMethodLevelAssistant extends Assistant {
        @InputGuardrails(InputGuardrailSuccess.class)
        @OutputGuardrails(OutputGuardrailFail.class)
        @Override
        String chat(String message);

        @InputGuardrails(InputGuardrailSuccess.class)
        @OutputGuardrails(OutputGuardrailFail.class)
        @Override
        String chat2(String message);

        static Assistant create() {
            return Assistant.create(SameMethodLevelAssistant.class);
        }

        static Assistant createUsingClassNames() {
            return AiServices.builder(SameMethodLevelAssistant.class)
                    .chatModel(new MyChatModel())
                    .inputGuardrailClasses(InputGuardrailSuccess.class)
                    .outputGuardrailClasses(OutputGuardrailFail.class)
                    .build();
        }

        static Assistant createUsingClassInstances() {
            return AiServices.builder(SameMethodLevelAssistant.class)
                    .chatModel(new MyChatModel())
                    .inputGuardrails(new InputGuardrailSuccess())
                    .outputGuardrails(new OutputGuardrailFail())
                    .build();
        }
    }

    interface MethodLevelAssistant extends Assistant {
        @InputGuardrails(InputGuardrailSuccess.class)
        @OutputGuardrails(OutputGuardrailSuccess.class)
        @Override
        String chat(String message);

        @InputGuardrails(InputGuardrailFail.class)
        @OutputGuardrails(OutputGuardrailFail.class)
        @Override
        String chat2(String message);

        static Assistant create() {
            return Assistant.create(MethodLevelAssistant.class);
        }
    }

    interface MethodLevelAssistant1 extends Assistant {
        @InputGuardrails({InputGuardrailSuccess.class, InputGuardrailFail.class})
        @OutputGuardrails(
                value = {OutputGuardrailSuccess.class, OutputGuardrailFail.class},
                maxRetries = 10)
        @Override
        String chat(String message);

        static Assistant create() {
            return Assistant.create(MethodLevelAssistant1.class);
        }
    }

    @InputGuardrails(InputGuardrailSuccess.class)
    @OutputGuardrails(OutputGuardrailSuccess.class)
    interface ClassAndMethodLevelAssistant extends Assistant {
        @InputGuardrails({InputGuardrailSuccess.class, InputGuardrailFail.class})
        @OutputGuardrails(
                value = {OutputGuardrailSuccess.class, OutputGuardrailFail.class},
                maxRetries = 10)
        @Override
        String chat(String message);

        static Assistant create() {
            return Assistant.create(ClassAndMethodLevelAssistant.class);
        }
    }

    public static class InputGuardrailSuccess implements InputGuardrail {
        @Override
        public InputGuardrailResult validate(UserMessage userMessage) {
            return successWith(userMessage.singleText() + " from " + getClass().getSimpleName());
        }
    }

    public static class InputGuardrailFail implements InputGuardrail {
        @Override
        public InputGuardrailResult validate(UserMessage userMessage) {
            return failure(
                    userMessage.singleText() + " failure from " + getClass().getSimpleName());
        }
    }

    public static class OutputGuardrailSuccess implements OutputGuardrail {
        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            return successWith(responseFromLLM.text() + " from " + getClass().getSimpleName());
        }
    }

    public static class OutputGuardrailFail implements OutputGuardrail {
        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            return failure(
                    responseFromLLM.text() + " failure from " + getClass().getSimpleName());
        }
    }

    public static class MyChatModel implements ChatModel {
        private static String getUserMessage(ChatRequest chatRequest) {
            return chatRequest.messages().stream()
                    .filter(message -> message.type() == ChatMessageType.USER)
                    .findFirst()
                    .map(chatMessage -> ((UserMessage) chatMessage).singleText())
                    .orElseThrow(() -> new IllegalArgumentException("No user message found"));
        }

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from("Request: %s; Response: Hi!".formatted(getUserMessage(chatRequest))))
                    .build();
        }
    }
}
