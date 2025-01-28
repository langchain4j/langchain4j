package dev.langchain4j.model.bedrock.internal;

import static org.junit.jupiter.api.Assertions.*;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.exception.UnsupportedFeatureException;
import java.net.URI;
import java.net.URISyntaxException;
import org.junit.jupiter.api.Test;

class UtilsTest {

    @Test
    void should_extract_extension_SimpleUriPaths() throws URISyntaxException {
        // Test basic file URIs with simple extensions
        assertEquals("txt", Utils.extractExtension(new URI("file:///path/document.txt")));
        assertEquals("pdf", Utils.extractExtension(new URI("file:///document.pdf")));
        assertEquals("jpg", Utils.extractExtension(new URI("file:///images/photo.jpg")));
    }

    @Test
    void should_extract_extension_HttpUris() throws URISyntaxException {
        // Test HTTP URIs with various extensions
        assertEquals("html", Utils.extractExtension(new URI("http://example.com/page.html")));
        assertEquals("php", Utils.extractExtension(new URI("https://example.com/script.php")));
        assertEquals("js", Utils.extractExtension(new URI("http://example.com/js/code.js")));
    }

    @Test
    void should_extract_extension_UriWithQueryParams() throws URISyntaxException {
        // Test URIs with query parameters
        assertEquals("pdf", Utils.extractExtension(new URI("http://example.com/doc.pdf?version=1")));
        assertEquals("jpg", Utils.extractExtension(new URI("file:///image.jpg?size=large")));
        assertEquals("php", Utils.extractExtension(new URI("https://example.com/script.php?id=123&type=main")));
    }

    @Test
    void should_extract_extension_UriWithFragment() throws URISyntaxException {
        // Test URIs with fragments
        assertEquals("html", Utils.extractExtension(new URI("http://example.com/page.html#section1")));
        assertEquals("pdf", Utils.extractExtension(new URI("file:///doc.pdf#page=1")));
        assertEquals("js", Utils.extractExtension(new URI("https://example.com/script.js#L100")));
    }

    @Test
    void should_extract_extension_UriWithQueryAndFragment() throws URISyntaxException {
        // Test URIs with both query parameters and fragments
        assertEquals("php", Utils.extractExtension(new URI("http://example.com/page.php?id=1#top")));
        assertEquals("html", Utils.extractExtension(new URI("https://example.com/doc.html?v=2#section")));
    }

    @Test
    void should_extract_extension_MultipleDots() throws URISyntaxException {
        // Test files with multiple dots
        assertEquals("gz", Utils.extractExtension(new URI("file:///archive.tar.gz")));
        assertEquals("zip", Utils.extractExtension(new URI("http://example.com/file.name.with.dots.zip")));
    }

    @Test
    void should_extract_extension_SpecialCharacters() throws URISyntaxException {
        // Test URIs with encoded special characters
        assertEquals("pdf", Utils.extractExtension(new URI("http://example.com/my%20document.pdf")));
        assertEquals("doc", Utils.extractExtension(new URI("file:///path/my%2Bfile.doc")));
    }

    @Test
    void should_extract_extension_NoExtension() throws URISyntaxException {
        // Test URIs without extensions
        assertEquals("", Utils.extractExtension(new URI("file:///path/README")));
        assertEquals("", Utils.extractExtension(new URI("http://example.com/about")));
        assertEquals("", Utils.extractExtension(new URI("https://example.com/path/")));
    }

    @Test
    void should_extract_extension_HiddenFiles() throws URISyntaxException {
        // Test hidden files (starting with dot)
        assertEquals("", Utils.extractExtension(new URI("file:///.hidden")));
        assertEquals("cfg", Utils.extractExtension(new URI("file:///.hidden.cfg")));
    }

    @Test
    void should_extract_extension_EmptyAndInvalidPaths() throws URISyntaxException {
        // Test empty and invalid paths
        assertEquals("", Utils.extractExtension(new URI("file:///")));
        assertEquals("", Utils.extractExtension(new URI("file:///.")));
        assertEquals("", Utils.extractExtension(new URI("file:///..")));
    }

    @Test
    void should_extract_extension_RelativePaths() throws URISyntaxException {
        // Test relative paths in URIs
        assertEquals("txt", Utils.extractExtension(new URI("./document.txt")));
        assertEquals("pdf", Utils.extractExtension(new URI("../docs/file.pdf")));
    }

    @Test
    void should_extract_format_from_mime_type() throws Exception {
        // Test all supported mime types
        assertEquals(
                "png",
                Utils.extractAndValidateFormat(Image.builder()
                        .mimeType("image/png")
                        .url(new URI("file:///image"))
                        .build()));
        assertEquals(
                "jpeg",
                Utils.extractAndValidateFormat(Image.builder()
                        .mimeType("image/jpeg")
                        .url(new URI("file:///image"))
                        .build()));
        assertEquals(
                "jpeg",
                Utils.extractAndValidateFormat(Image.builder()
                        .mimeType("image/jpg")
                        .url(new URI("file:///image"))
                        .build()));
        assertEquals(
                "gif",
                Utils.extractAndValidateFormat(Image.builder()
                        .mimeType("image/gif")
                        .url(new URI("file:///image"))
                        .build()));
        assertEquals(
                "webp",
                Utils.extractAndValidateFormat(Image.builder()
                        .mimeType("image/webp")
                        .url(new URI("file:///image"))
                        .build()));
    }

    @Test
    void should_extract_format_from_uri_when_mime_type_is_null() throws Exception {
        // Test all supported extensions
        assertEquals(
                "png",
                Utils.extractAndValidateFormat(
                        Image.builder().url(new URI("file:///image.png")).build()));
        assertEquals(
                "jpeg",
                Utils.extractAndValidateFormat(
                        Image.builder().url(new URI("file:///image.jpg")).build()));
        assertEquals(
                "jpeg",
                Utils.extractAndValidateFormat(
                        Image.builder().url(new URI("file:///image.jpeg")).build()));
        assertEquals(
                "gif",
                Utils.extractAndValidateFormat(
                        Image.builder().url(new URI("file:///image.gif")).build()));
        assertEquals(
                "webp",
                Utils.extractAndValidateFormat(
                        Image.builder().url(new URI("file:///image.webp")).build()));
    }

    @Test
    void should_extract_format_from_uri_when_mime_type_is_invalid() throws Exception {
        assertEquals(
                "png",
                Utils.extractAndValidateFormat(Image.builder()
                        .mimeType("invalid/mime")
                        .url(new URI("file:///image.png"))
                        .build()));
    }

    @Test
    void should_prioritize_mime_type_over_extension() throws Exception {
        // When mime type and extension conflict, mime type should win
        assertEquals(
                "jpeg",
                Utils.extractAndValidateFormat(Image.builder()
                        .mimeType("image/jpeg")
                        .url(new URI("file:///image.png"))
                        .build()));
    }

    @Test
    void should_normalize_jpg_to_jpeg() throws Exception {
        // Both jpg and jpeg should return JPEG format
        assertEquals(
                "jpeg",
                Utils.extractAndValidateFormat(
                        Image.builder().url(new URI("file:///image.jpg")).build()));
        assertEquals(
                "jpeg",
                Utils.extractAndValidateFormat(Image.builder()
                        .mimeType("image/jpg")
                        .url(new URI("file:///image"))
                        .build()));
    }

    @Test
    void should_throw_exception_for_unsupported_format() {
        // Test with unsupported mime type and extension
        assertThrows(
                UnsupportedFeatureException.class,
                () -> Utils.extractAndValidateFormat(Image.builder()
                        .mimeType("image/bmp")
                        .url(new URI("file:///image.bmp"))
                        .build()));
    }

    @Test
    void should_throw_exception_for_null_image() {
        assertThrows(IllegalArgumentException.class, () -> Utils.extractAndValidateFormat(null));
    }

    @Test
    void should_extract_clean_filename_from_basic_uri() throws Exception {
        // Test basic filenames
        assertEquals("document", Utils.extractCleanFileName(new URI("file:///path/document.txt")));
        assertEquals("photo", Utils.extractCleanFileName(new URI("http://example.com/photo.jpg")));
    }

    @Test
    void should_extract_clean_filename_when_special_characters_present() throws Exception {
        // Test special characters replacement
        assertEquals("My-Document", Utils.extractCleanFileName(new URI("file:///path/My@Document#.txt")));
        assertEquals("Hello-World", Utils.extractCleanFileName(new URI("file:///path/Hello_World.pdf")));
    }

    @Test
    void should_extract_clean_filename_with_allowed_special_characters() throws Exception {
        // Test all allowed special characters
        assertEquals("file-name", Utils.extractCleanFileName(new URI("file:///path/file-name.doc")));
        assertEquals("file(1)", Utils.extractCleanFileName(new URI("file:///path/file(1).pdf")));
    }

    @Test
    void should_extract_clean_filename_with_multiple_dots() throws Exception {
        // Test filenames with multiple dots
        assertEquals("archive-tar", Utils.extractCleanFileName(new URI("file:///path/archive.tar.gz")));
        assertEquals("my-file-v1-2", Utils.extractCleanFileName(new URI("file:///path/my.file.v1.2.txt")));
    }

    @Test
    void should_extract_clean_filename_for_edge_cases() throws Exception {
        // Test edge cases
        assertEquals("", Utils.extractCleanFileName(new URI("file:///")));
        assertEquals("", Utils.extractCleanFileName(new URI("file:///..")));
        assertEquals("", Utils.extractCleanFileName(null));
        // Hidden files
        assertEquals("-hidden", Utils.extractCleanFileName(new URI("file:///.hidden")));
        assertEquals("-hidden", Utils.extractCleanFileName(new URI("file:///.hidden.txt")));
    }
}
