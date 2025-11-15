package dev.langchain4j.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CustomMimeTypesFileTypeDetectorTest {
    @Test
    void should_return_a_mime_type_from_default_mapping_from_path() {
        // given
        CustomMimeTypesFileTypeDetector detector = new CustomMimeTypesFileTypeDetector();

        // when
        Path path = Path.of("/foo/bar/index.html");
        String mimeType = detector.probeContentType(path);

        // then
        assertThat(mimeType).isEqualTo("text/html");
    }

    @Test
    void should_return_a_mime_type_from_default_mapping_from_uri() {
        // given
        CustomMimeTypesFileTypeDetector detector = new CustomMimeTypesFileTypeDetector();

        // when
        URI uri = URI.create("https://foo.com/bar/style.css");
        String mimeType = detector.probeContentType(uri);

        // then
        assertThat(mimeType).isEqualTo("text/css");
    }

    @Test
    void should_return_a_mime_type_from_default_mapping_from_string() {
        // given
        CustomMimeTypesFileTypeDetector detector = new CustomMimeTypesFileTypeDetector();

        // when
        String mimeType = detector.probeContentType("README.md");

        // then
        assertThat(mimeType).isEqualTo("text/x-markdown");
    }

    @Test
    void can_provide_custom_mapping() {
        // given
        Map<String, String> mapping = new HashMap<>();
        mapping.put("abcd", "text/abcd");
        CustomMimeTypesFileTypeDetector detector = new CustomMimeTypesFileTypeDetector(mapping);

        // when
        String mimeType = detector.probeContentType("foo.abcd");

        // then
        assertThat(mimeType).isEqualTo("text/abcd");
    }

    @Test
    void should_return_null_when_no_mapping_exist_from_path() {
        // given
        CustomMimeTypesFileTypeDetector detector = new CustomMimeTypesFileTypeDetector();

        // when
        String mimeType = detector.probeContentType("foo.banana");

        // then
        assertThat(mimeType).isNull();
    }

    @Test
    void should_return_null_when_no_mapping_exist_from_uri() {
        // given
        CustomMimeTypesFileTypeDetector detector = new CustomMimeTypesFileTypeDetector();

        // when
        String mimeType = detector.probeContentType(URI.create("https://foo.bar.com/baz.yui"));

        // then
        assertThat(mimeType).isNull();
    }

    @Test
    void should_return_mime_type_from_website_from_uri() {
        // given
        CustomMimeTypesFileTypeDetector detector = new CustomMimeTypesFileTypeDetector();

        // when
        String mimeType = detector.probeContentType(URI.create("https://docs.langchain4j.dev/logo.svg"));

        // then
        assertThat(mimeType).isEqualTo("image/svg+xml");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "http://example.org/cat",
                "http://example.org/cat.banana",
                "http://example.org/some.path/cat",
                "http://example.org/cat?query=dog.png"
            })
    void should_fail_to_detect_mime_type(String url) throws MalformedURLException, URISyntaxException {
        // given
        CustomMimeTypesFileTypeDetector detector = new CustomMimeTypesFileTypeDetector();

        // when
        String mimeType = detector.probeContentType(new URL(url).toURI());

        // then
        assertThat(mimeType).isNull();
    }

    @Test
    void should_handle_null_path_input() {
        // given
        CustomMimeTypesFileTypeDetector detector = new CustomMimeTypesFileTypeDetector();

        // when/then
        assertThatThrownBy(() -> detector.probeContentType((Path) null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void should_handle_null_uri_input() {
        // given
        CustomMimeTypesFileTypeDetector detector = new CustomMimeTypesFileTypeDetector();

        // when/then
        assertThatThrownBy(() -> detector.probeContentType((URI) null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void should_handle_null_string_input() {
        // given
        CustomMimeTypesFileTypeDetector detector = new CustomMimeTypesFileTypeDetector();

        // when/then
        assertThatThrownBy(() -> detector.probeContentType((String) null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void should_handle_empty_string_input() {
        // given
        CustomMimeTypesFileTypeDetector detector = new CustomMimeTypesFileTypeDetector();

        // when
        String mimeType = detector.probeContentType("");

        // then
        assertThat(mimeType).isNull();
    }

    @Test
    void should_handle_file_without_extension() {
        // given
        CustomMimeTypesFileTypeDetector detector = new CustomMimeTypesFileTypeDetector();

        // when
        String mimeType = detector.probeContentType("README");

        // then
        assertThat(mimeType).isNull();
    }

    @Test
    void should_be_case_insensitive_for_extensions() {
        // given
        CustomMimeTypesFileTypeDetector detector = new CustomMimeTypesFileTypeDetector();

        // when
        String lowerCaseMimeType = detector.probeContentType("document.pdf");
        String upperCaseMimeType = detector.probeContentType("document.PDF");
        String mixedCaseMimeType = detector.probeContentType("document.PdF");

        // then
        assertThat(lowerCaseMimeType).isEqualTo("application/pdf");
        assertThat(upperCaseMimeType).isEqualTo("application/pdf");
        assertThat(mixedCaseMimeType).isEqualTo("application/pdf");
    }

    @Test
    void should_handle_path_with_no_filename() {
        // given
        CustomMimeTypesFileTypeDetector detector = new CustomMimeTypesFileTypeDetector();

        // when
        String mimeType = detector.probeContentType(Path.of("/foo/bar/"));

        // then
        assertThat(mimeType).isNull();
    }

    @Test
    void should_handle_relative_paths() {
        // given
        CustomMimeTypesFileTypeDetector detector = new CustomMimeTypesFileTypeDetector();

        // when
        String mimeType = detector.probeContentType(Path.of("./docs/readme.txt"));

        // then
        assertThat(mimeType).isEqualTo("text/plain");
    }

    @Test
    void should_handle_complex_uri_with_query_parameters() {
        // given
        CustomMimeTypesFileTypeDetector detector = new CustomMimeTypesFileTypeDetector();

        // when
        String mimeType = detector.probeContentType(
                URI.create("https://example.com/api/download/image.jpg?version=1&format=high"));

        // then
        assertThat(mimeType).isEqualTo("image/jpeg");
    }

    @Test
    void should_handle_uri_with_fragment() {
        // given
        CustomMimeTypesFileTypeDetector detector = new CustomMimeTypesFileTypeDetector();

        // when
        String mimeType = detector.probeContentType(URI.create("https://example.com/docs/guide.html#section1"));

        // then
        assertThat(mimeType).isEqualTo("text/html");
    }

    @Test
    void should_override_default_mapping_with_custom() {
        // given
        Map<String, String> customMapping = new HashMap<>();
        customMapping.put("html", "application/custom-html");
        CustomMimeTypesFileTypeDetector detector = new CustomMimeTypesFileTypeDetector(customMapping);

        // when
        String mimeType = detector.probeContentType("index.html");

        // then
        assertThat(mimeType).isEqualTo("application/custom-html");
    }

    @Test
    void should_fallback_to_default_when_custom_mapping_missing() {
        // given
        Map<String, String> customMapping = new HashMap<>();
        customMapping.put("custom", "application/custom");
        CustomMimeTypesFileTypeDetector detector = new CustomMimeTypesFileTypeDetector(customMapping);

        // when
        String mimeType = detector.probeContentType("style.css");

        // then
        assertThat(mimeType).isEqualTo("text/css");
    }

    @Test
    void should_handle_empty_custom_mapping() {
        // given
        Map<String, String> emptyMapping = new HashMap<>();
        CustomMimeTypesFileTypeDetector detector = new CustomMimeTypesFileTypeDetector(emptyMapping);

        // when
        String mimeType = detector.probeContentType("document.pdf");

        // then
        assertThat(mimeType).isEqualTo("application/pdf");
    }

    @Test
    void should_handle_null_custom_mapping() {
        // given/when/then
        assertThatThrownBy(() -> new CustomMimeTypesFileTypeDetector(null)).isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "document.pdf", "spreadsheet.xlsx", "presentation.pptx",
                "document.docx", "archive.zip", "compressed.rar",
                "data.json", "config.xml"
            })
    void should_detect_common_application_formats(String filename) {
        // given
        CustomMimeTypesFileTypeDetector detector = new CustomMimeTypesFileTypeDetector();

        // when
        String mimeType = detector.probeContentType(filename);

        // then
        assertThat(mimeType).startsWith("application/");
    }

    @Test
    void should_handle_file_with_leading_dot() {
        // given
        CustomMimeTypesFileTypeDetector detector = new CustomMimeTypesFileTypeDetector();

        // when
        String mimeType = detector.probeContentType(".hidden.txt");

        // then
        assertThat(mimeType).isEqualTo("text/plain");
    }

    @Test
    void should_handle_file_with_only_extension() {
        // given
        CustomMimeTypesFileTypeDetector detector = new CustomMimeTypesFileTypeDetector();

        // when
        String mimeType = detector.probeContentType(".css");

        // then
        assertThat(mimeType).isEqualTo("text/css");
    }
}
