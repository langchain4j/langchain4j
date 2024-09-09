package dev.langchain4j.model.dashscope;

import dev.langchain4j.data.audio.Audio;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.*;
import org.junit.jupiter.params.provider.Arguments;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class QwenTestHelper {
    public static Stream<Arguments> languageModelNameProvider() {
        return Stream.of(
                Arguments.of(QwenModelName.QWEN_TURBO),
                Arguments.of(QwenModelName.QWEN_PLUS),
                Arguments.of(QwenModelName.QWEN_MAX),
                Arguments.of(QwenModelName.QWEN_MAX_LONGCONTEXT),
                Arguments.of(QwenModelName.QWEN_7B_CHAT),
                Arguments.of(QwenModelName.QWEN_14B_CHAT),
                Arguments.of(QwenModelName.QWEN_72B_CHAT),
                Arguments.of(QwenModelName.QWEN1_5_7B_CHAT),
                Arguments.of(QwenModelName.QWEN1_5_14B_CHAT),
                Arguments.of(QwenModelName.QWEN1_5_32B_CHAT),
                Arguments.of(QwenModelName.QWEN1_5_72B_CHAT),
                Arguments.of(QwenModelName.QWEN2_0_5B_INSTRUCT),
                Arguments.of(QwenModelName.QWEN2_1_5B_INSTRUCT),
                Arguments.of(QwenModelName.QWEN2_7B_INSTRUCT),
                Arguments.of(QwenModelName.QWEN2_72B_INSTRUCT),
                Arguments.of(QwenModelName.QWEN2_57B_A14B_INSTRUCT)
        );
    }

    public static Stream<Arguments> nonMultimodalChatModelNameProvider() {
        return Stream.of(
                Arguments.of(QwenModelName.QWEN_TURBO),
                Arguments.of(QwenModelName.QWEN_PLUS),
                Arguments.of(QwenModelName.QWEN_MAX),
                Arguments.of(QwenModelName.QWEN_MAX_LONGCONTEXT),
                Arguments.of(QwenModelName.QWEN_7B_CHAT),
                Arguments.of(QwenModelName.QWEN_14B_CHAT),
                Arguments.of(QwenModelName.QWEN_72B_CHAT),
                Arguments.of(QwenModelName.QWEN1_5_7B_CHAT),
                Arguments.of(QwenModelName.QWEN1_5_14B_CHAT),
                Arguments.of(QwenModelName.QWEN1_5_32B_CHAT),
                Arguments.of(QwenModelName.QWEN1_5_72B_CHAT),
                Arguments.of(QwenModelName.QWEN2_0_5B_INSTRUCT),
                Arguments.of(QwenModelName.QWEN2_1_5B_INSTRUCT),
                Arguments.of(QwenModelName.QWEN2_7B_INSTRUCT),
                Arguments.of(QwenModelName.QWEN2_72B_INSTRUCT),
                Arguments.of(QwenModelName.QWEN2_57B_A14B_INSTRUCT)
        );
    }

    public static Stream<Arguments> functionCallChatModelNameProvider() {
        return Stream.of(
                Arguments.of(QwenModelName.QWEN_MAX)
        );
    }

    public static Stream<Arguments> vlChatModelNameProvider() {
        return Stream.of(
                Arguments.of(QwenModelName.QWEN_VL_PLUS),
                Arguments.of(QwenModelName.QWEN_VL_MAX)
        );
    }

    public static Stream<Arguments> audioChatModelNameProvider() {
        return Stream.of(
                Arguments.of(QwenModelName.QWEN_AUDIO_CHAT),
                Arguments.of(QwenModelName.QWEN2_AUDIO_INSTRUCT)
        );
    }

    public static Stream<Arguments> embeddingModelNameProvider() {
        return Stream.of(
                Arguments.of(QwenModelName.TEXT_EMBEDDING_V1),
                Arguments.of(QwenModelName.TEXT_EMBEDDING_V2)
        );
    }

    public static Stream<Arguments> listenableModelNameProvider() {
        return Stream.of(
                Arguments.of(QwenModelName.QWEN_MAX, true),  // non-multimodal, support tools
                Arguments.of(QwenModelName.QWEN_VL_MAX, false)  // multimodal, don't support tools yet
        );
    }

    public static String apiKey() {
        return System.getenv("DASHSCOPE_API_KEY");
    }

    public static List<ChatMessage> chatMessages() {
        List<ChatMessage> messages = new LinkedList<>();
        messages.add(SystemMessage.from("Your name is Jack." +
                " You like to answer other people's questions briefly." +
                " It's rainy today." +
                " Your reply should end with \"That's all!\"."));
        messages.add(UserMessage.from("Hello. What's your name?"));
        messages.add(AiMessage.from("Jack. That's all!"));
        messages.add(UserMessage.from("How about the weather today?"));
        return messages;
    }

    public static List<ChatMessage> multimodalChatMessagesWithImageUrl() {
        Image image = Image.builder()
                .url("https://dashscope.oss-cn-beijing.aliyuncs.com/images/dog_and_girl.jpeg")
                .build();
        ImageContent imageContent = ImageContent.from(image);
        TextContent textContent = TextContent.from("What animal is in the picture?");
        return Collections.singletonList(UserMessage.from(imageContent, textContent));
    }

    public static List<ChatMessage> multimodalChatMessagesWithImageData() {
        Image image = Image.builder()
                .base64Data(multimodalImageData())
                .mimeType("image/jpeg")
                .build();
        ImageContent imageContent = ImageContent.from(image);
        TextContent textContent = TextContent.from("What animal is in the picture?");
        return Collections.singletonList(UserMessage.from(imageContent, textContent));
    }

    public static String multimodalImageData() {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (InputStream in = QwenTestHelper.class.getResourceAsStream("/parrot.jpg")) {
            assertThat(in).isNotNull();
            byte[] data = new byte[512];
            int n;
            while ((n = in.read(data)) != -1) {
                buffer.write(data, 0, n);
            }
        } catch (IOException e) {
            fail("", e.getMessage());
        }

        return Base64.getEncoder().encodeToString(buffer.toByteArray());
    }

    public static List<ChatMessage> multimodalChatMessagesWithAudioUrl() {
        Audio audio = Audio.builder()
                .url("https://dashscope.oss-cn-beijing.aliyuncs.com/audios/welcome.mp3")
                .build();
        AudioContent audioContent = AudioContent.from(audio);
        TextContent textContent = TextContent.from("What is this audio saying? Please note that the audio language is Chinese.");
        return Collections.singletonList(UserMessage.from(audioContent, textContent));
    }

    public static List<ChatMessage> multimodalChatMessagesWithAudioData() {
        Audio audio = Audio.builder()
                .base64Data(multimodalAudioData())
                .mimeType("audio/mp3")
                .build();
        AudioContent audioContent = AudioContent.from(audio);
        TextContent textContent = TextContent.from("What is this audio saying? Please note that the audio language is Chinese.");
        return Collections.singletonList(UserMessage.from(audioContent, textContent));
    }

    public static String multimodalAudioData() {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (InputStream in = QwenTestHelper.class.getResourceAsStream("/welcome.mp3")) {
            assertThat(in).isNotNull();
            byte[] data = new byte[512];
            int n;
            while ((n = in.read(data)) != -1) {
                buffer.write(data, 0, n);
            }
        } catch (IOException e) {
            fail("", e.getMessage());
        }

        return Base64.getEncoder().encodeToString(buffer.toByteArray());
    }
}
