package dev.langchain4j.model.dashscope;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.*;
import org.junit.jupiter.params.provider.Arguments;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Stream;

public class QwenTestHelper {
    public static Stream<Arguments> languageModelNameProvider() {
        return Stream.of(
                Arguments.of(QwenModelName.QWEN_TURBO),
                Arguments.of(QwenModelName.QWEN_PLUS),
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

    public static Stream<Arguments> multiModalChatModelNameProvider() {
        return Stream.of(
                Arguments.of(QwenModelName.QWEN_VL_PLUS),
                Arguments.of(QwenModelName.QWEN_VL_MAX)
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

    public static List<ChatMessage> multiModalChatMessages() {
        Image image = Image.builder()
                .url("https://dashscope.oss-cn-beijing.aliyuncs.com/images/dog_and_girl.jpeg")
                .build();
        ImageContent imageContent = ImageContent.from(image);
        TextContent textContent = TextContent.from("What animal is in the picture?");
        return Collections.singletonList(UserMessage.from(imageContent, textContent));
    }
}
