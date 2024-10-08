package dev.langchain4j.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class CustomMimeTypesFileTypeDetectorTest {
    @Test
    void should_return_a_mime_type_from_default_mapping_from_path() {
        // given
        CustomMimeTypesFileTypeDetector detector = new CustomMimeTypesFileTypeDetector();

        // when
        Path path = Paths.get("/foo/bar/index.html");
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
    @ValueSource(strings = {
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
}
