package dev.langchain4j.data.document;

import org.junit.jupiter.api.Test;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentLoaderTest {

    @Test
    void should_load_text_document_from_file_system() {
        DocumentLoader loader = DocumentLoader.from(Paths.get("src/test/resources/test-file-utf8.txt"));

        Document document = loader.load();

        assertThat(document.text()).isEqualTo("test\ncontent");
        Metadata metadata = document.metadata();
        assertThat(metadata.get("file_name")).isEqualTo("test-file-utf8.txt");
        assertThat(Paths.get(metadata.get("absolute_directory_path"))).isAbsolute();
    }

    @Test
    void should_load_text_document_from_url() throws MalformedURLException {
        String url = "https://raw.githubusercontent.com/langchain4j/langchain4j/main/langchain4j/src/test/resources/test-file-utf8.txt";
        DocumentLoader loader = DocumentLoader.from(new URL(url));

        Document document = loader.load();

        assertThat(document.text()).isEqualTo("test\ncontent");
        Metadata metadata = document.metadata();
        assertThat(metadata.get("url")).isEqualTo(url);
    }

    @Test
    void should_load_pdf_document_from_file_system() {
        DocumentLoader loader = DocumentLoader.from(Paths.get("src/test/resources/test-file.pdf"));

        Document document = loader.load();

        assertThat(document.text()).isEqualToIgnoringWhitespace("test\ncontent");
        Metadata metadata = document.metadata();
        assertThat(metadata.get("file_name")).isEqualTo("test-file.pdf");
        assertThat(Paths.get(metadata.get("absolute_directory_path"))).isAbsolute();
    }
}