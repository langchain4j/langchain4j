package dev.langchain4j.model.dashscope;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.params.provider.Arguments;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Stream;

public class QwenTestHelper {
    public static Stream<Arguments> languageModelNameProvider() {
        return Stream.of(
                Arguments.of(QwenModelName.QWEN_TURBO),
                Arguments.of(QwenModelName.QWEN_PLUS),
                Arguments.of(QwenModelName.QWEN_SPARK_V1),
                Arguments.of(QwenModelName.QWEN_SPARK_V2),
                Arguments.of(QwenModelName.QWEN_7B_CHAT),
                Arguments.of(QwenModelName.QWEN_14B_CHAT)
        );
    }

    public static Stream<Arguments> chatModelNameProvider() {
        return Stream.of(
                Arguments.of(QwenModelName.QWEN_TURBO),
                Arguments.of(QwenModelName.QWEN_PLUS),
                Arguments.of(QwenModelName.QWEN_7B_CHAT),
                Arguments.of(QwenModelName.QWEN_14B_CHAT)
        );
    }

    public static Stream<Arguments> embeddingModelNameProvider() {
        return Stream.of(
                Arguments.of(QwenModelName.TEXT_EMBEDDING_V1)
        );
    }

    public static String apiKey() {
        return System.getenv("DASHSCOPE_API_KEY");
    }

    public static List<ChatMessage> chatMessages() {
        List<ChatMessage> messages = new LinkedList<>();
        messages.add(SystemMessage.from("Your name is Jack." +
                " You like to answer other people's questions briefly." +
                " It's rainy today."));
        messages.add(UserMessage.from("Hello. What's your name?"));
        messages.add(AiMessage.from("Jack."));
        messages.add(UserMessage.from("How about the weather today?"));
        return messages;
    }
}
