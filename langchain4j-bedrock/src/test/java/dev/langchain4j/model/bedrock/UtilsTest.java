package dev.langchain4j.model.bedrock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.exception.UnsupportedFeatureException;
import java.net.URI;
import java.net.URISyntaxException;
import org.junit.jupiter.api.Test;

class UtilsTest {

    @Test
    void should_extract_extension_SimpleUriPaths() throws URISyntaxException {
        // Test basic file URIs with simple extensions
        assertThat(Utils.extractExtension(new URI("file:///path/document.txt"))).isEqualTo("txt");
        assertThat(Utils.extractExtension(new URI("file:///document.pdf"))).isEqualTo("pdf");
        assertThat(Utils.extractExtension(new URI("file:///images/photo.jpg"))).isEqualTo("jpg");
    }

    @Test
    void should_extract_extension_HttpUris() throws URISyntaxException {
        // Test HTTP URIs with various extensions
        assertThat(Utils.extractExtension(new URI("http://example.com/page.html")))
                .isEqualTo("html");
        assertThat(Utils.extractExtension(new URI("https://example.com/script.php")))
                .isEqualTo("php");
        assertThat(Utils.extractExtension(new URI("http://example.com/js/code.js")))
                .isEqualTo("js");
    }

    @Test
    void should_extract_extension_UriWithQueryParams() throws URISyntaxException {
        // Test URIs with query parameters
        assertThat(Utils.extractExtension(new URI("http://example.com/doc.pdf?version=1")))
                .isEqualTo("pdf");
        assertThat(Utils.extractExtension(new URI("file:///image.jpg?size=large")))
                .isEqualTo("jpg");
        assertThat(Utils.extractExtension(new URI("https://example.com/script.php?id=123&type=main")))
                .isEqualTo("php");
    }

    @Test
    void should_extract_extension_UriWithFragment() throws URISyntaxException {
        // Test URIs with fragments
        assertThat(Utils.extractExtension(new URI("http://example.com/page.html#section1")))
                .isEqualTo("html");
        assertThat(Utils.extractExtension(new URI("file:///doc.pdf#page=1"))).isEqualTo("pdf");
        assertThat(Utils.extractExtension(new URI("https://example.com/script.js#L100")))
                .isEqualTo("js");
    }

    @Test
    void should_extract_extension_UriWithQueryAndFragment() throws URISyntaxException {
        // Test URIs with both query parameters and fragments
        assertThat(Utils.extractExtension(new URI("http://example.com/page.php?id=1#top")))
                .isEqualTo("php");
        assertThat(Utils.extractExtension(new URI("https://example.com/doc.html?v=2#section")))
                .isEqualTo("html");
    }

    @Test
    void should_extract_extension_MultipleDots() throws URISyntaxException {
        // Test files with multiple dots
        assertThat(Utils.extractExtension(new URI("file:///archive.tar.gz"))).isEqualTo("gz");
        assertThat(Utils.extractExtension(new URI("http://example.com/file.name.with.dots.zip")))
                .isEqualTo("zip");
    }

    @Test
    void should_extract_extension_SpecialCharacters() throws URISyntaxException {
        // Test URIs with encoded special characters
        assertThat(Utils.extractExtension(new URI("http://example.com/my%20document.pdf")))
                .isEqualTo("pdf");
        assertThat(Utils.extractExtension(new URI("file:///path/my%2Bfile.doc")))
                .isEqualTo("doc");
    }

    @Test
    void should_extract_extension_NoExtension() throws URISyntaxException {
        // Test URIs without extensions
        assertThat(Utils.extractExtension(new URI("file:///path/README"))).isEmpty();
        assertThat(Utils.extractExtension(new URI("http://example.com/about"))).isEmpty();
        assertThat(Utils.extractExtension(new URI("https://example.com/path/"))).isEmpty();
    }

    @Test
    void should_extract_extension_HiddenFiles() throws URISyntaxException {
        // Test hidden files (starting with dot)
        assertThat(Utils.extractExtension(new URI("file:///.hidden"))).isEmpty();
        assertThat(Utils.extractExtension(new URI("file:///.hidden.cfg"))).isEqualTo("cfg");
    }

    @Test
    void should_extract_extension_EmptyAndInvalidPaths() throws URISyntaxException {
        // Test empty and invalid paths
        assertThat(Utils.extractExtension(new URI("file:///"))).isEmpty();
        assertThat(Utils.extractExtension(new URI("file:///."))).isEmpty();
        assertThat(Utils.extractExtension(new URI("file:///.."))).isEmpty();
    }

    @Test
    void should_extract_extension_RelativePaths() throws URISyntaxException {
        // Test relative paths in URIs
        assertThat(Utils.extractExtension(new URI("./document.txt"))).isEqualTo("txt");
        assertThat(Utils.extractExtension(new URI("../docs/file.pdf"))).isEqualTo("pdf");
    }

    @Test
    void should_extract_format_from_mime_type() throws Exception {
        // Test all supported mime types
        assertThat(Utils.extractAndValidateFormat(Image.builder()
                        .mimeType("image/png")
                        .url(new URI("file:///image"))
                        .build()))
                .isEqualTo("png");
        assertThat(Utils.extractAndValidateFormat(Image.builder()
                        .mimeType("image/jpeg")
                        .url(new URI("file:///image"))
                        .build()))
                .isEqualTo("jpeg");
        assertThat(Utils.extractAndValidateFormat(Image.builder()
                        .mimeType("image/jpg")
                        .url(new URI("file:///image"))
                        .build()))
                .isEqualTo("jpeg");
        assertThat(Utils.extractAndValidateFormat(Image.builder()
                        .mimeType("image/gif")
                        .url(new URI("file:///image"))
                        .build()))
                .isEqualTo("gif");
        assertThat(Utils.extractAndValidateFormat(Image.builder()
                        .mimeType("image/webp")
                        .url(new URI("file:///image"))
                        .build()))
                .isEqualTo("webp");
    }

    @Test
    void should_extract_format_from_uri_when_mime_type_is_null() throws Exception {
        // Test all supported extensions
        assertThat(Utils.extractAndValidateFormat(
                        Image.builder().url(new URI("file:///image.png")).build()))
                .isEqualTo("png");
        assertThat(Utils.extractAndValidateFormat(
                        Image.builder().url(new URI("file:///image.jpg")).build()))
                .isEqualTo("jpeg");
        assertThat(Utils.extractAndValidateFormat(
                        Image.builder().url(new URI("file:///image.jpeg")).build()))
                .isEqualTo("jpeg");
        assertThat(Utils.extractAndValidateFormat(
                        Image.builder().url(new URI("file:///image.gif")).build()))
                .isEqualTo("gif");
        assertThat(Utils.extractAndValidateFormat(
                        Image.builder().url(new URI("file:///image.webp")).build()))
                .isEqualTo("webp");
    }

    @Test
    void should_extract_format_from_uri_when_mime_type_is_invalid() throws Exception {
        assertThat(Utils.extractAndValidateFormat(Image.builder()
                        .mimeType("invalid/mime")
                        .url(new URI("file:///image.png"))
                        .build()))
                .isEqualTo("png");
    }

    @Test
    void should_prioritize_mime_type_over_extension() throws Exception {
        // When mime type and extension conflict, mime type should win
        assertThat(Utils.extractAndValidateFormat(Image.builder()
                        .mimeType("image/jpeg")
                        .url(new URI("file:///image.png"))
                        .build()))
                .isEqualTo("jpeg");
    }

    @Test
    void should_normalize_jpg_to_jpeg() throws Exception {
        // Both jpg and jpeg should return JPEG format
        assertThat(Utils.extractAndValidateFormat(
                        Image.builder().url(new URI("file:///image.jpg")).build()))
                .isEqualTo("jpeg");
        assertThat(Utils.extractAndValidateFormat(Image.builder()
                        .mimeType("image/jpg")
                        .url(new URI("file:///image"))
                        .build()))
                .isEqualTo("jpeg");
    }

    @Test
    void should_throw_exception_for_unsupported_format() {
        // Test with unsupported mime type and extension
        assertThatExceptionOfType(UnsupportedFeatureException.class)
                .isThrownBy(() -> Utils.extractAndValidateFormat(Image.builder()
                        .mimeType("image/bmp")
                        .url(new URI("file:///image.bmp"))
                        .build()));
    }

    @Test
    void should_throw_exception_for_null_image() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> Utils.extractAndValidateFormat(null));
    }

    @Test
    void should_extract_clean_filename_from_basic_uri() throws Exception {
        // Test basic filenames
        assertThat(Utils.extractCleanFileName(new URI("file:///path/document.txt")))
                .isEqualTo("document");
        assertThat(Utils.extractCleanFileName(new URI("http://example.com/photo.jpg")))
                .isEqualTo("photo");
    }

    @Test
    void should_extract_clean_filename_when_special_characters_present() throws Exception {
        // Test special characters replacement
        assertThat(Utils.extractCleanFileName(new URI("file:///path/My@Document#.txt")))
                .isEqualTo("My-Document");
        assertThat(Utils.extractCleanFileName(new URI("file:///path/Hello_World.pdf")))
                .isEqualTo("Hello-World");
    }

    @Test
    void should_extract_clean_filename_with_allowed_special_characters() throws Exception {
        // Test all allowed special characters
        assertThat(Utils.extractCleanFileName(new URI("file:///path/file-name.doc")))
                .isEqualTo("file-name");
        assertThat(Utils.extractCleanFileName(new URI("file:///path/file(1).pdf")))
                .isEqualTo("file(1)");
    }

    @Test
    void should_extract_clean_filename_with_multiple_dots() throws Exception {
        // Test filenames with multiple dots
        assertThat(Utils.extractCleanFileName(new URI("file:///path/archive.tar.gz")))
                .isEqualTo("archive-tar");
        assertThat(Utils.extractCleanFileName(new URI("file:///path/my.file.v1.2.txt")))
                .isEqualTo("my-file-v1-2");
    }

    @Test
    void should_extract_clean_filename_for_edge_cases() throws Exception {
        // Test edge cases
        assertThat(Utils.extractCleanFileName(new URI("file:///"))).isEmpty();
        assertThat(Utils.extractCleanFileName(new URI("file:///.."))).isEmpty();
        assertThat(Utils.extractCleanFileName(null)).isEmpty();
        // Hidden files
        assertThat(Utils.extractCleanFileName(new URI("file:///.hidden"))).isEqualTo("-hidden");
        assertThat(Utils.extractCleanFileName(new URI("file:///.hidden.txt"))).isEqualTo("-hidden");
    }
}
