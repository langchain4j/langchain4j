package dev.langchain4j.jackson3;

import static dev.langchain4j.data.message.AiMessage.GENERATED_AUDIO_KEY;
import static dev.langchain4j.data.message.AiMessage.GENERATED_IMAGES_KEY;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.audio.Audio;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.AiMessage;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The Jackson 3 codec reads the generated content of an {@link AiMessage} back with the type its
 * attribute keys are documented to hold, the same way the Jackson 2 codec does.
 */
class Jackson3AiMessageGeneratedContentTest {

    private static final Image IMAGE = Image.builder()
            .url("https://example.com/a.png")
            .mimeType("image/png")
            .build();

    private static final Audio AUDIO =
            Audio.builder().base64Data("AAEC").mimeType("audio/wav").build();

    private static final Jackson3ChatMessageJsonCodec CODEC = new Jackson3ChatMessageJsonCodec();

    @Test
    void should_keep_generated_content_across_a_round_trip() {
        AiMessage original = AiMessage.builder()
                .text("hi")
                .attributes(Map.of(GENERATED_IMAGES_KEY, List.of(IMAGE), GENERATED_AUDIO_KEY, List.of(AUDIO)))
                .build();

        AiMessage restored = (AiMessage) CODEC.messageFromJson(CODEC.messageToJson(original));

        assertThat(restored).isEqualTo(original);
        assertThat(restored.images()).containsExactly(IMAGE);
        assertThat(restored.audios()).containsExactly(AUDIO);
    }

    @Test
    void should_read_generated_content_written_without_any_type_information() {
        String json = "{\"text\":\"hi\",\"toolExecutionRequests\":[],\"attributes\":{"
                + "\"generated_images\":[{\"url\":\"https://example.com/a.png\",\"mimeType\":\"image/png\"}],"
                + "\"generated_audio\":[{\"base64Data\":\"AAEC\",\"mimeType\":\"audio/wav\"}]},\"type\":\"AI\"}";

        AiMessage restored = (AiMessage) CODEC.messageFromJson(json);

        assertThat(restored.images()).containsExactly(IMAGE);
        assertThat(restored.audios()).containsExactly(AUDIO);
    }

    @Test
    void should_write_generated_content_as_plain_json() {
        AiMessage message = AiMessage.builder()
                .text("hi")
                .attributes(Map.of(GENERATED_IMAGES_KEY, List.of(IMAGE)))
                .build();

        assertThat(CODEC.messageToJson(message))
                .isEqualToIgnoringWhitespace("{\"text\":\"hi\",\"toolExecutionRequests\":[],\"attributes\":{"
                        + "\"generated_images\":[{\"url\":\"https://example.com/a.png\",\"mimeType\":\"image/png\"}]},"
                        + "\"type\":\"AI\"}");
    }
}
