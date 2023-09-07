package dev.langchain4j.data.document;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebUrlDocumentLoaderTest {

    @Test
    void should_load_web_url_document() throws IOException {
        String url = "https://example.com/";

        Document document = WebUrlDocumentLoader.load(url);
        String cleanedTestWebUrlDocument = clean(new String(Files.readAllBytes(toPath("test-web-url.html"))));

        assertThat(clean(document.text())).isEqualTo(cleanedTestWebUrlDocument);
        Metadata metadata = document.metadata();
        assertThat(metadata.get("url")).isEqualTo(url);
    }

    @Test
    void should_not_load_malformed_web_url() {
        String url = "example.com/";

        assertThatThrownBy(() -> WebUrlDocumentLoader.load(url))
                .isInstanceOf(RuntimeException.class);
    }

    private Path toPath(String fileName) {
        try {
            return Paths.get(getClass().getClassLoader().getResource(fileName).toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private String clean(String str) {
        return str.replaceAll("\\s+", "");
    }
}