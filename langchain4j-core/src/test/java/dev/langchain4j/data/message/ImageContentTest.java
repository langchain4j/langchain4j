package dev.langchain4j.data.message;

import static dev.langchain4j.data.message.ChatMessageDeserializer.messageFromJson;
import static dev.langchain4j.data.message.ChatMessageSerializer.messageToJson;
import static dev.langchain4j.data.message.ImageContent.DetailLevel.AUTO;
import static dev.langchain4j.data.message.ImageContent.DetailLevel.LOW;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.internal.Json;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImageContentTest implements WithAssertions {
    @Test
    void methods() {
        Image urlImage =
                Image.builder().url(URI.create("https://example.com/image.png")).build();
        ImageContent imageContent = new ImageContent(urlImage, ImageContent.DetailLevel.HIGH);

        assertThat(imageContent.image()).isEqualTo(urlImage);
        assertThat(imageContent.detailLevel()).isEqualTo(ImageContent.DetailLevel.HIGH);
        assertThat(imageContent.type()).isEqualTo(ContentType.IMAGE);

        assertThat(imageContent)
                .hasToString("ImageContent { " + "image = Image { "
                        + "url = \"https://example.com/image.png\", "
                        + "base64Data = null, mimeType = null, revisedPrompt = null } "
                        + "detailLevel = HIGH }");
    }

    @Test
    void equals_hash_code() {
        ImageContent i1 = ImageContent.from("https://example.com/image.png");
        ImageContent i2 = ImageContent.from("https://example.com/image.png");

        ImageContent i3 = ImageContent.from("https://example.com/image.jpg");
        ImageContent i4 = ImageContent.from("https://example.com/image.jpg");

        assertThat(i1)
                .isEqualTo(i1)
                .isNotEqualTo(null)
                .isNotEqualTo(new Object())
                .isEqualTo(i2)
                .hasSameHashCodeAs(i2)
                .isNotEqualTo(i3)
                .isNotEqualTo(i4);

        assertThat(i3).isEqualTo(i3).isEqualTo(i4).hasSameHashCodeAs(i4);
    }

    @Test
    void builders() {
        Image urlImage =
                Image.builder().url(URI.create("https://example.com/image.png")).build();
        assertThat(new ImageContent(urlImage, ImageContent.DetailLevel.LOW))
                .isEqualTo(new ImageContent(urlImage))
                .isEqualTo(ImageContent.from(urlImage, ImageContent.DetailLevel.LOW))
                .isEqualTo(ImageContent.from(urlImage))
                .isEqualTo(new ImageContent(urlImage.url()))
                .isEqualTo(new ImageContent(urlImage.url().toString()))
                .isEqualTo(new ImageContent(urlImage.url(), ImageContent.DetailLevel.LOW))
                .isEqualTo(new ImageContent(urlImage.url().toString(), ImageContent.DetailLevel.LOW))
                .isEqualTo(ImageContent.from(urlImage.url()))
                .isEqualTo(ImageContent.from(urlImage.url().toString()))
                .isEqualTo(ImageContent.from(urlImage.url(), ImageContent.DetailLevel.LOW))
                .isEqualTo(ImageContent.from(urlImage.url().toString(), ImageContent.DetailLevel.LOW));

        Image base64Image =
                Image.builder().base64Data("ff==").mimeType("mimeType").build();
        assertThat(new ImageContent(base64Image, ImageContent.DetailLevel.LOW))
                .isEqualTo(new ImageContent(base64Image))
                .isEqualTo(ImageContent.from(base64Image, ImageContent.DetailLevel.LOW))
                .isEqualTo(ImageContent.from(base64Image))
                .isEqualTo(new ImageContent(base64Image.base64Data(), base64Image.mimeType()))
                .isEqualTo(new ImageContent(
                        base64Image.base64Data(), base64Image.mimeType(), ImageContent.DetailLevel.LOW))
                .isEqualTo(ImageContent.from(base64Image.base64Data(), base64Image.mimeType()))
                .isEqualTo(ImageContent.from(
                        base64Image.base64Data(), base64Image.mimeType(), ImageContent.DetailLevel.LOW));
    }

    @Test
    void should_record_whether_the_detail_level_was_given() {
        assertThat(ImageContent.from("http://image.url").detailLevelSpecified()).isFalse();
        assertThat(ImageContent.from(URI.create("http://image.url")).detailLevelSpecified()).isFalse();
        assertThat(ImageContent.from("aGVsbG8=", "image/png").detailLevelSpecified()).isFalse();
        assertThat(ImageContent.from(Image.builder().url("http://image.url").build()).detailLevelSpecified())
                .isFalse();
        assertThat(new ImageContent("http://image.url").detailLevelSpecified()).isFalse();

        assertThat(ImageContent.from("http://image.url", LOW).detailLevelSpecified()).isTrue();
        assertThat(ImageContent.from(URI.create("http://image.url"), AUTO).detailLevelSpecified()).isTrue();
        assertThat(ImageContent.from("aGVsbG8=", "image/png", LOW).detailLevelSpecified()).isTrue();
        assertThat(ImageContent.from(Image.builder().url("http://image.url").build(), LOW).detailLevelSpecified())
                .isTrue();
    }

    @Test
    void should_record_whether_the_detail_level_was_given_by_every_constructor(@TempDir Path directory)
            throws Exception {
        Path imageFile = Files.write(directory.resolve("image.png"), new byte[] {1, 2, 3});

        assertThat(new ImageContent(URI.create("http://image.url")).detailLevelSpecified()).isFalse();
        assertThat(new ImageContent("aGVsbG8=", "image/png").detailLevelSpecified()).isFalse();
        assertThat(new ImageContent(Image.builder().url("http://image.url").build()).detailLevelSpecified())
                .isFalse();
        assertThat(ImageContent.from(imageFile, "image/png").detailLevelSpecified()).isFalse();

        assertThat(new ImageContent(URI.create("http://image.url"), LOW).detailLevelSpecified()).isTrue();
        assertThat(new ImageContent("http://image.url", LOW).detailLevelSpecified()).isTrue();
        assertThat(new ImageContent("aGVsbG8=", "image/png", AUTO).detailLevelSpecified()).isTrue();
        assertThat(new ImageContent(Image.builder().url("http://image.url").build(), LOW).detailLevelSpecified())
                .isTrue();
        assertThat(ImageContent.from(imageFile, "image/png", LOW).detailLevelSpecified()).isTrue();
    }

    @Test
    void should_keep_low_detail_level_equals_and_to_string_when_the_detail_level_is_not_given() {
        ImageContent notGiven = ImageContent.from("http://image.url");
        ImageContent givenLow = ImageContent.from("http://image.url", LOW);

        assertThat(notGiven.detailLevel()).isEqualTo(LOW);
        assertThat(notGiven).isEqualTo(givenLow).hasSameHashCodeAs(givenLow);
        assertThat(notGiven).hasToString(givenLow.toString());
        assertThat(notGiven.toString()).doesNotContain("Specified");
    }

    @Test
    void should_keep_the_json_form_and_read_it_back_as_given() {
        UserMessage notGiven = UserMessage.from(ImageContent.from("http://image.url"));
        UserMessage givenLow = UserMessage.from(ImageContent.from("http://image.url", LOW));

        String json = messageToJson(notGiven);

        assertThat(json).isEqualTo(messageToJson(givenLow)).doesNotContain("Specified");
        UserMessage readBack = (UserMessage) messageFromJson(json);
        assertThat(readBack).isEqualTo(notGiven);
        assertThat(((ImageContent) readBack.contents().get(0)).detailLevelSpecified()).isTrue();
    }

    @Test
    void should_not_write_the_detail_level_flag_with_other_json_mappers() throws Exception {
        ImageContent notGiven = ImageContent.from("http://image.url");
        ImageContent givenLow = ImageContent.from("http://image.url", LOW);

        String lc4jJson = Json.toJson(notGiven);
        assertThat(lc4jJson).isEqualTo(Json.toJson(givenLow)).doesNotContain("Specified");

        ObjectMapper beanMapper = new ObjectMapper().disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        assertThat(beanMapper.writeValueAsString(notGiven)).isEqualTo("{}");

        ObjectMapper fieldsMapper = new ObjectMapper()
                .setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE)
                .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        assertThat(fieldsMapper.writeValueAsString(notGiven))
                .isEqualTo(fieldsMapper.writeValueAsString(givenLow))
                .contains("detailLevel")
                .doesNotContain("Specified");
    }
}
