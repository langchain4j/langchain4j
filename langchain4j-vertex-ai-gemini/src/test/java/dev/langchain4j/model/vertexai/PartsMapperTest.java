package dev.langchain4j.model.vertexai;

import com.google.cloud.vertexai.api.Part;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.ImageContent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
                Arguments.of("http://example.org/cat.jpg?query=dog.png", "image/jpeg")
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://example.org/cat",
            "http://example.org/cat.banana",
            "http://example.org/some.path/cat",
            "http://example.org/cat?query=dog.png"
    })
    void should_fail_to_detect_mime_type(String url) {

        assertThatThrownBy(() -> PartsMapper.detectMimeType(URI.create(url)))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unable to detect the MIME type of '" + url + "'. Please provide it explicitly.");
    }
}