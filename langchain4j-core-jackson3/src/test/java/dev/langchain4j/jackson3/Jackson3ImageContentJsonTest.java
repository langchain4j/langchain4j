package dev.langchain4j.jackson3;

import static dev.langchain4j.data.message.ImageContent.DetailLevel.LOW;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;

/**
 * Chat messages are persisted, for example in a chat memory store: whether the detail level of an image was chosen
 * must not change their JSON form.
 */
class Jackson3ImageContentJsonTest {

    private final Jackson3ChatMessageJsonCodec codec = new Jackson3ChatMessageJsonCodec();

    @Test
    void should_write_the_same_json_whether_the_detail_level_was_chosen_or_not() {
        String notChosen = codec.messageToJson(UserMessage.from(ImageContent.from("https://example.com/a.png")));
        String chosenLow = codec.messageToJson(UserMessage.from(ImageContent.from("https://example.com/a.png", LOW)));

        assertThat(notChosen).isEqualTo(chosenLow).doesNotContain("Specified");
    }

    @Test
    void should_read_an_image_back_as_having_a_chosen_detail_level() {
        String json = codec.messageToJson(UserMessage.from(ImageContent.from("https://example.com/a.png")));

        UserMessage readBack = (UserMessage) codec.messageFromJson(json);

        ImageContent image = (ImageContent) readBack.contents().get(0);
        assertThat(image.detailLevel()).isEqualTo(LOW);
        assertThat(image.detailLevelSpecified()).isTrue();
    }
}
