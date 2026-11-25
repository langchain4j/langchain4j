package dev.langchain4j.model.vertexai.gemini;

import com.google.cloud.vertexai.api.Part;
import dev.langchain4j.data.audio.Audio;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.AudioContent;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.VideoContent;
import dev.langchain4j.exception.UnsupportedFeatureException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PartsMapperTest {

    private static final String IMAGE_URL = "https://example.com/photos/test-image.jpg";

    @Test
    void should_detect_mime_type_automatically() {

        // given
        ImageContent imageContent = ImageContent.from(IMAGE_URL);

        // when
        Part part = PartsMapper.map(imageContent);

        // then
        assertThat(part.getFileData().getMimeType()).isEqualTo("image/jpeg");
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
        assertThat(part.getFileData().getMimeType()).isEqualTo(mimeType);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "https://example.com/photos/cat.png",
                "http://example.com/photos/cat.png",
                "gs://bucket/cat.png",
                "HTTPS://example.com/photos/cat.png"
            })
    void should_pass_image_urls_on_to_vertex_ai(String url) {

        // when
        Part part = PartsMapper.map(ImageContent.from(url));

        // then
        assertThat(part.hasInlineData()).isFalse();
        assertThat(part.getFileData().getFileUri()).isEqualTo(url);
        assertThat(part.getFileData().getMimeType()).isEqualTo("image/png");
    }

    @Test
    void should_pass_media_urls_on_to_vertex_ai() {

        // when
        Part video = PartsMapper.map(VideoContent.from("https://example.com/video/clip.mp4"));
        Part pdf = PartsMapper.map(PdfFileContent.from("https://example.com/docs/report.pdf"));

        // then
        assertThat(video.getFileData().getFileUri()).isEqualTo("https://example.com/video/clip.mp4");
        assertThat(video.getFileData().getMimeType()).isEqualTo("video/mp4");
        assertThat(pdf.getFileData().getFileUri()).isEqualTo("https://example.com/docs/report.pdf");
        assertThat(pdf.getFileData().getMimeType()).isEqualTo("application/pdf");
    }

    @ParameterizedTest
    @ValueSource(strings = {"file:///etc/cat.png", "/home/me/cat.png", "cat.png", "ftp://example.com/cat.png"})
    void should_reject_media_given_by_other_urls(String url) {

        // when-then
        assertThatThrownBy(() -> PartsMapper.map(ImageContent.from(url)))
                .isExactlyInstanceOf(UnsupportedFeatureException.class)
                .hasMessage(PartsMapper.URL_NOT_SUPPORTED);
        assertThatThrownBy(() -> PartsMapper.map(AudioContent.from(url)))
                .isExactlyInstanceOf(UnsupportedFeatureException.class)
                .hasMessage(PartsMapper.URL_NOT_SUPPORTED);
        assertThatThrownBy(() -> PartsMapper.map(VideoContent.from(url)))
                .isExactlyInstanceOf(UnsupportedFeatureException.class)
                .hasMessage(PartsMapper.URL_NOT_SUPPORTED);
        assertThatThrownBy(() -> PartsMapper.map(PdfFileContent.from(url)))
                .isExactlyInstanceOf(UnsupportedFeatureException.class)
                .hasMessage(PartsMapper.URL_NOT_SUPPORTED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"file:///tmp/imagen-image-1.png", "https://example.com/photos/cat.png"})
    void should_send_the_content_of_media_that_has_both_content_and_a_url(String url) {

        // given
        Image image = Image.builder()
                .url(url)
                .base64Data(Base64.getEncoder().encodeToString(new byte[] {1, 2, 3}))
                .build();

        // when
        Part part = PartsMapper.map(ImageContent.from(image));

        // then
        assertThat(part.hasFileData()).isFalse();
        assertThat(part.getInlineData().getMimeType()).isEqualTo("image/png");
        assertThat(part.getInlineData().getData().toByteArray()).containsExactly(1, 2, 3);
    }

    @Test
    void should_send_images_read_from_local_files(@TempDir Path tempDir) throws IOException {

        // given
        Path imagePath = Files.write(tempDir.resolve("test-image.jpg"), new byte[]{1, 2, 3});

        // when
        Part part = PartsMapper.map(ImageContent.from(imagePath, "image/jpeg"));

        // then
        assertThat(part.getInlineData().getMimeType()).isEqualTo("image/jpeg");
        assertThat(part.getInlineData().getData().toByteArray()).containsExactly(1, 2, 3);
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
        assertThat(mp3urlPart.hasInlineData()).isFalse();
        assertThat(mp3urlPart.getFileData().getFileUri()).isEqualTo(mp3url.toString());
        assertThat(mp3urlPart.getFileData().getMimeType()).isEqualTo("audio/mp3");
    }
}
