package dev.langchain4j.model.vertexai.gemini;

import com.google.cloud.vertexai.api.Part;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.audio.Audio;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.media.DefaultMediaFetcher;
import dev.langchain4j.data.media.FetchedMedia;
import dev.langchain4j.data.media.MediaFetcher;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.AudioContent;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.VideoContent;
import dev.langchain4j.exception.InvalidRequestException;
import org.junit.jupiter.api.BeforeAll;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PartsMapperTest {

    private static final MediaFetcher NO_DOWNLOADS = request -> {
        throw new AssertionError("Unexpected download of " + request.url());
    };

    private static final String IMAGE_URL = "https://example.com/test-image.jpg";

    private static final MediaFetcher MEDIA_FETCHER = request -> FetchedMedia.from(new byte[]{1, 2, 3}, "image/webp");

    @TempDir
    static Path tempDir;

    private static Path imagePath;

    @BeforeAll
    static void setUp() throws IOException {
        imagePath = tempDir.resolve("test-image.jpg");
        Files.write(imagePath, new byte[]{1, 2, 3});
    }

    @Test
    void should_detect_mime_type_automatically() {

        // given
        ImageContent imageContent = ImageContent.from(IMAGE_URL);

        // when
        Part part = PartsMapper.map(imageContent, MEDIA_FETCHER);

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
        Part part = PartsMapper.map(imageContent, MEDIA_FETCHER);

        // then
        assertThat(part.getInlineData().getMimeType()).isEqualTo(mimeType);
    }

    @Test
    void should_take_mime_type_from_download_when_url_has_no_extension() {

        // given
        ImageContent imageContent = ImageContent.from("https://example.com/images?id=42");

        // when
        Part part = PartsMapper.map(imageContent, MEDIA_FETCHER);

        // then
        assertThat(part.getInlineData().getMimeType()).isEqualTo("image/webp");
    }

    @Test
    void should_fail_when_mime_type_is_unknown_after_download() {

        // given
        ImageContent imageContent = ImageContent.from("https://example.com/images?id=42");
        MediaFetcher mediaFetcher = request -> FetchedMedia.from(new byte[]{1, 2, 3});

        // when-then
        assertThatThrownBy(() -> PartsMapper.map(imageContent, mediaFetcher))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unable to detect the MIME type");
    }

    @Test
    void should_map_image_read_from_local_file() {

        // given
        ImageContent imageContent = ImageContent.from(imagePath, "image/jpeg");

        // when
        Part part = PartsMapper.map(imageContent, NO_DOWNLOADS);

        // then
        assertThat(part.getInlineData().getMimeType()).isEqualTo("image/jpeg");
        assertThat(part.getInlineData().getData().toByteArray()).containsExactly(1, 2, 3);
    }

    @Test
    void should_not_read_local_files_given_as_urls_with_default_media_fetcher() {

        // given
        MediaFetcher defaultMediaFetcher = DefaultMediaFetcher.builder().build();

        // when-then
        for (Runnable mapping : List.<Runnable>of(
                () -> PartsMapper.map(ImageContent.from(imagePath.toUri()), defaultMediaFetcher),
                () -> PartsMapper.map(ImageContent.from(imagePath.toString()), defaultMediaFetcher),
                () -> PartsMapper.map(PdfFileContent.from("file:///etc/passwd.pdf"), defaultMediaFetcher),
                () -> PartsMapper.map(AudioContent.from("file:///etc/passwd.mp3"), defaultMediaFetcher),
                () -> PartsMapper.map(VideoContent.from("file:///etc/passwd.mp4"), defaultMediaFetcher))) {
            assertThatThrownBy(mapping::run)
                    .isExactlyInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("Only http and https URLs can be fetched")
                    .hasMessageContaining("ImageContent.from(Path, String)");
        }
    }

    @Test
    void should_pass_cloud_storage_urls_on_without_downloading() {

        // given
        AudioContent audioContent = AudioContent.from("gs://cloud-samples-data/generative-ai/audio/pixel.mp3");

        // when
        Part part = PartsMapper.map(audioContent, NO_DOWNLOADS);

        // then
        assertThat(part.getFileData().getFileUri()).isEqualTo("gs://cloud-samples-data/generative-ai/audio/pixel.mp3");
        assertThat(part.getFileData().getMimeType()).isEqualTo("audio/mp3");
    }

    @Test
    void should_return_urls_of_media_that_are_downloaded() {

        // given
        List<ChatMessage> messages = List.of(
                SystemMessage.from("system"),
                UserMessage.from(
                        TextContent.from("describe"),
                        ImageContent.from("https://example.com/cat.png"),
                        ImageContent.from("AQID", "image/png"),
                        ImageContent.from("gs://bucket/image.png"),
                        ImageContent.from("file:///etc/passwd"),
                        ImageContent.from("/etc/passwd"),
                        AudioContent.from("https://example.com/audio.mp3"),
                        VideoContent.from("s3://bucket/video.mp4"),
                        PdfFileContent.from("https://example.com/document.pdf")),
                AiMessage.from("answer"));

        // when
        List<URI> urls = PartsMapper.mediaUrls(messages);

        // then
        assertThat(urls).containsExactly(
                URI.create("https://example.com/cat.png"),
                URI.create("file:///etc/passwd"),
                URI.create("/etc/passwd"),
                URI.create("https://example.com/audio.mp3"),
                URI.create("s3://bucket/video.mp4"),
                URI.create("https://example.com/document.pdf"));
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

                Arguments.of("http://example.org/cat.m4a", "audio/m4a"),
                Arguments.of("http://example.org/cat.M4A", "audio/m4a"),
                Arguments.of("http://example.org/cat.m4a?query=dog.png", "audio/m4a"),

                Arguments.of("http://example.org/cat.mp4", "video/mp4"),
                Arguments.of("http://example.org/cat.MP4", "video/mp4"),
                Arguments.of("http://example.org/cat.mp4?query=dog.png", "video/mp4"),

                Arguments.of("http://example.org/cat.wmv", "video/wmv"),
                Arguments.of("http://example.org/cat.WMV", "video/wmv"),
                Arguments.of("http://example.org/cat.wmv?query=dog.png", "video/wmv"),

                Arguments.of("https://storage.googleapis.com/cloud-samples-data/generative-ai/audio/pixel.mp3", "audio/mp3"),
                Arguments.of("gs://cloud-samples-data/generative-ai/audio/pixel.mp3", "audio/mp3"),

                Arguments.of("https://storage.googleapis.com/cloud-samples-data/video/animals.mp4", "video/mp4"),
                Arguments.of("gs://cloud-samples-data/video/animals.mp4", "video/mp4")
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://example.org/cat.banana",
            "http://example.org/cat.mmv",
            "http://example.org/cat.mpa",
            "http://example.org/cat"
    })
    void should_fail_to_detect_mime_type_of_unsupported_extension(String url) {

        // given
        URI uri = URI.create(url);

        // when-then
        assertThatThrownBy(() -> PartsMapper.detectMimeType(uri))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unable to detect the MIME type");
    }

    @Test
    void should_create_multimedia_part_from_url() {
        // given
        URI mp3url = URI.create("https://storage.googleapis.com/cloud-samples-data/generative-ai/audio/pixel.mp3");
        Audio mp3UrlAudio = Audio.builder().url(mp3url).mimeType("audio/mp3").build();
        List<URI> downloaded = new ArrayList<>();
        MediaFetcher mediaFetcher = request -> {
            downloaded.add(request.url());
            return FetchedMedia.from(new byte[]{1, 2, 3}, "audio/mpeg");
        };

        // when
        Part mp3urlPart = PartsMapper.map(AudioContent.from(mp3UrlAudio), mediaFetcher);

        // then
        assertThat(downloaded).containsExactly(mp3url);
        assertThat(mp3urlPart.getInlineData().getMimeType()).isEqualTo("audio/mp3");
        assertThat(mp3urlPart.getInlineData().getData().toByteArray()).containsExactly(1, 2, 3);
    }

    static Stream<Arguments> contentsWithMimeTypeOfDownloadThatDoesNotFit() {
        return Stream.of(
                Arguments.of(VideoContent.from("https://www.youtube.com/watch?v=abc123"), "text/html"),
                Arguments.of(VideoContent.from("https://www.youtube.com/watch?v=abc123"), "application/octet-stream"),
                Arguments.of(ImageContent.from("https://www.youtube.com/watch?v=abc123"), "text/html"),
                Arguments.of(ImageContent.from("https://www.youtube.com/watch?v=abc123"), "application/octet-stream"),
                Arguments.of(AudioContent.from("https://www.youtube.com/watch?v=abc123"), "video/mp4"));
    }

    @ParameterizedTest
    @MethodSource("contentsWithMimeTypeOfDownloadThatDoesNotFit")
    void should_not_use_mime_type_of_download_that_does_not_fit_the_content(Content content, String reportedMimeType) {

        // given
        MediaFetcher mediaFetcher = request -> FetchedMedia.from("<html></html>".getBytes(), reportedMimeType);

        // when-then
        assertThatThrownBy(() -> PartsMapper.map(UserMessage.from(content), mediaFetcher))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unable to detect the MIME type of 'https://www.youtube.com/...'")
                .hasMessageContaining("the server answered with " + reportedMimeType)
                .hasMessageNotContaining("watch")
                .hasMessageNotContaining("abc123");
    }

    @Test
    void should_use_mime_type_of_download_that_fits_the_content() {

        // given
        MediaFetcher mediaFetcher = request -> FetchedMedia.from(new byte[]{1, 2, 3}, switch (request.url().getPath()) {
            case "/audio" -> "audio/ogg";
            case "/video" -> "video/webm";
            case "/document" -> "application/pdf";
            default -> "image/avif";
        });
        UserMessage message = UserMessage.from(
                ImageContent.from("https://example.com/image"),
                AudioContent.from("https://example.com/audio"),
                VideoContent.from("https://example.com/video"),
                PdfFileContent.from("https://example.com/document"));

        // when
        List<Part> parts = PartsMapper.map(message, mediaFetcher);

        // then
        assertThat(parts)
                .extracting(part -> part.getInlineData().getMimeType())
                .containsExactly("image/avif", "audio/ogg", "video/webm", "application/pdf");
    }

    @Test
    void should_show_only_scheme_and_host_of_cloud_storage_url_when_mime_type_is_unknown() {

        // when-then
        assertThatThrownBy(() -> PartsMapper.map(ImageContent.from("gs://bucket/private/object"), NO_DOWNLOADS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unable to detect the MIME type of 'gs://bucket/...'. Please provide it explicitly.");
    }

    @Test
    void should_download_exactly_the_media_urls_that_are_prefetched() {

        // given
        String base64 = Base64.getEncoder().encodeToString(new byte[]{1, 2, 3});
        List<ChatMessage> messages = List.of(
                SystemMessage.from("system"),
                UserMessage.from(
                        TextContent.from("describe"),
                        ImageContent.from("https://example.com/cat.png"),
                        ImageContent.from("https://example.com/images?id=42"),
                        ImageContent.from(base64, "image/png"),
                        ImageContent.from("data:image/png;base64," + base64),
                        ImageContent.from("gs://bucket/image.png"),
                        ImageContent.from("file:///home/me/cat.png"),
                        ImageContent.from("cat.png"),
                        AudioContent.from("https://example.com/audio.mp3"),
                        AudioContent.from(base64, "audio/mp3"),
                        VideoContent.from("https://example.com/video.mp4"),
                        VideoContent.from("gs://bucket/video.mp4"),
                        VideoContent.from(base64, "video/mp4"),
                        PdfFileContent.from("https://example.com/document.pdf"),
                        PdfFileContent.from("file:///home/me/report.pdf"),
                        PdfFileContent.from(base64, "application/pdf")),
                AiMessage.from("answer"),
                UserMessage.from(
                        ImageContent.from("https://example.com/cat.png"), ImageContent.from("https://example.com/dog.jpg")),
                AiMessage.from(ToolExecutionRequest.builder()
                        .id("call-1")
                        .name("weather")
                        .arguments("{}")
                        .build()),
                ToolExecutionResultMessage.from("call-1", "weather", "sunny"));
        List<URI> downloaded = new ArrayList<>();
        MediaFetcher mediaFetcher = request -> {
            downloaded.add(request.url());
            return FetchedMedia.from(new byte[]{1, 2, 3}, "image/png");
        };

        // when
        ContentsMapper.splitInstructionAndContent(messages, mediaFetcher);

        // then
        assertThat(Set.copyOf(PartsMapper.mediaUrls(messages)))
                .hasSize(10)
                .isEqualTo(Set.copyOf(downloaded));
    }
}
