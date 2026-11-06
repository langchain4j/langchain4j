package dev.langchain4j.data.message;

import static dev.langchain4j.data.message.AiMessage.GENERATED_AUDIO_KEY;
import static dev.langchain4j.data.message.AiMessage.GENERATED_IMAGES_KEY;
import static dev.langchain4j.data.message.ChatMessageDeserializer.messageFromJson;
import static dev.langchain4j.data.message.ChatMessageSerializer.messageToJson;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.audio.Audio;
import dev.langchain4j.data.image.Image;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * An {@link AiMessage} keeps the content a model generated in its attributes, and an attribute map
 * holds plain values. A message read back has to give the keys LangChain4j reserves the type those
 * keys are documented to hold: otherwise {@link AiMessage#images()} and {@link AiMessage#audios()}
 * fail at the call site with a {@link ClassCastException}, and the message read back is not equal to
 * the one that was written.
 */
class AiMessageGeneratedContentRoundTripTest {

    private static final Image IMAGE = Image.builder()
            .url("https://example.com/a.png")
            .mimeType("image/png")
            .build();

    private static final Audio AUDIO =
            Audio.builder().base64Data("AAEC").mimeType("audio/wav").build();

    private static final String JSON_WITH_GENERATED_CONTENT = "{\"text\":\"hi\",\"toolExecutionRequests\":[],"
            + "\"attributes\":{\"generated_images\":[{\"url\":\"https://example.com/a.png\",\"mimeType\":\"image/png\"}],"
            + "\"generated_audio\":[{\"base64Data\":\"AAEC\",\"mimeType\":\"audio/wav\"}]},\"type\":\"AI\"}";

    @Test
    void should_keep_generated_content_across_a_round_trip() {
        AiMessage original = AiMessage.builder()
                .text("hi")
                .attributes(Map.of(GENERATED_IMAGES_KEY, List.of(IMAGE), GENERATED_AUDIO_KEY, List.of(AUDIO)))
                .build();

        AiMessage restored = (AiMessage) messageFromJson(messageToJson(original));

        assertThat(restored).isEqualTo(original);
        assertThat(restored.images()).containsExactly(IMAGE);
        assertThat(restored.audios()).containsExactly(AUDIO);
        assertThat(restored.attribute(GENERATED_IMAGES_KEY, List.class)).containsExactly(IMAGE);
    }

    @Test
    void should_keep_an_attribute_that_holds_a_list_of_plain_values() {
        AiMessage original = AiMessage.builder()
                .text("hi")
                .attributes(Map.of("name", "Klaus", "age", 42, "extra", List.of("one", "two")))
                .build();

        AiMessage restored = (AiMessage) messageFromJson(messageToJson(original));

        assertThat(restored).isEqualTo(original);
        assertThat(restored.attributes())
                .containsEntry("name", "Klaus")
                .containsEntry("age", 42)
                .containsEntry("extra", List.of("one", "two"));
    }

    @Test
    void should_write_generated_content_as_plain_json() {
        AiMessage message = AiMessage.builder()
                .text("hi")
                .attributes(Map.of(GENERATED_IMAGES_KEY, List.of(IMAGE)))
                .build();

        assertThat(messageToJson(message))
                .isEqualToIgnoringWhitespace("{\"text\":\"hi\",\"toolExecutionRequests\":[],\"attributes\":{"
                        + "\"generated_images\":[{\"url\":\"https://example.com/a.png\",\"mimeType\":\"image/png\"}]},"
                        + "\"type\":\"AI\"}");
    }

    @Test
    void should_read_generated_content_written_without_any_type_information() {
        AiMessage restored = (AiMessage) messageFromJson(JSON_WITH_GENERATED_CONTENT);

        assertThat(restored.images()).containsExactly(IMAGE);
        assertThat(restored.audios()).containsExactly(AUDIO);
    }

    @Test
    void should_leave_a_value_that_is_not_a_list_of_generated_content_alone() {
        AiMessage restored = (AiMessage) messageFromJson("{\"text\":\"hi\",\"toolExecutionRequests\":[],"
                + "\"attributes\":{\"generated_images\":\"not an array\"},\"type\":\"AI\"}");

        assertThat(restored.attributes()).containsEntry(GENERATED_IMAGES_KEY, "not an array");
    }
}
