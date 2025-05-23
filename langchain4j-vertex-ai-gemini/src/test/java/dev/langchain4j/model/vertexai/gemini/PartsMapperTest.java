package dev.langchain4j.model.vertexai.gemini;

import com.google.cloud.vertexai.api.Part;
import dev.langchain4j.data.audio.Audio;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.AudioContent;
import dev.langchain4j.data.message.ImageContent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.URI;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class PartsMapperTest {

    private static final String IMAGE_URL = "https://upload.wikimedia.org/wikipedia/commons/3/3f/JPEG_example_flower.jpg";

    @Test
    void should_detect_mime_type_automatically() {

        // given
        ImageContent imageContent = ImageContent.from(IMAGE_URL);

        // when
        Part part = PartsMapper.map(imageContent);

        // then
        assertThat(part.getInlineData().getMimeType()).isEqualTo("image/jpeg");
    }

    @Test
    void should_take_mime_type_from_input_when_provided() {

        // given
        String mimeType = "image/png";
        Image image = Image.builder()
                .url(IMAGE_URL)
                .mimeType(mimeType)
                .build();
        ImageContent imageContent = ImageContent.from(image);

        // when
        Part part = PartsMapper.map(imageContent);

        // then
        assertThat(part.getInlineData().getMimeType()).isEqualTo(mimeType);
    }

    @ParameterizedTest
    @MethodSource
    void should_detect_correct_mime_type(String url, String expectedMimeType) {

        // given
        URI uri = URI.create(url);

        // when
        String mimeType = PartsMapper.detectMimeType(uri);

        // then
        assertThat(mimeType).isEqualTo(expectedMimeType);
    }

    static Stream<Arguments> should_detect_correct_mime_type() {
        return Stream.of(
                Arguments.of("http://example.org/cat.png", "image/png"),
                Arguments.of("http://example.org/cat.PNG", "image/png"),
                Arguments.of("http://example.org/cat.png?query=dog.jpg", "image/png"),

                Arguments.of("http://example.org/cat.jpeg", "image/jpeg"),
                Arguments.of("http://example.org/cat.JPEG", "image/jpeg"),
                Arguments.of("http://example.org/cat.jpeg?query=dog.png", "image/jpeg"),

                Arguments.of("http://example.org/cat.jpg", "image/jpeg"),
                Arguments.of("http://example.org/cat.JPG", "image/jpeg"),
                Arguments.of("http://example.org/cat.jpg?query=dog.png", "image/jpeg"),

                Arguments.of("http://example.org/cat.mp3", "audio/mp3"),
                Arguments.of("http://example.org/cat.MP3", "audio/mp3"),
                Arguments.of("http://example.org/cat.mp3?query=dog.png", "audio/mp3"),

                Arguments.of("http://example.org/cat.mp4", "video/mp4"),
                Arguments.of("http://example.org/cat.MP4", "video/mp4"),
                Arguments.of("http://example.org/cat.mp4?query=dog.png", "video/mp4"),

                Arguments.of("https://storage.googleapis.com/cloud-samples-data/generative-ai/audio/pixel.mp3", "audio/mp3"),
                Arguments.of("gs://cloud-samples-data/generative-ai/audio/pixel.mp3", "audio/mp3"),

                Arguments.of("https://storage.googleapis.com/cloud-samples-data/video/animals.mp4", "video/mp4"),
                Arguments.of("gs://cloud-samples-data/video/animals.mp4", "video/mp4")
        );
    }

    @Test
    void should_create_multimedia_part_from_url() {
        // given
        URI mp3url = URI.create("https://storage.googleapis.com/cloud-samples-data/generative-ai/audio/pixel.mp3");
        Audio mp3UrlAudio = Audio.builder().url(mp3url).mimeType("audio/mp3").build();

        // when
        Part mp3urlPart = PartsMapper.map(AudioContent.from(mp3UrlAudio));

        // then
        assertThat(mp3urlPart.getInlineData().getMimeType()).isEqualTo("audio/mp3");
    }
}
