package dev.langchain4j.data.document;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DirectoryLoaderTest {

    @Test
    void should_load_all_documents() {
        DirectoryLoader loader = DirectoryLoader.from("src/test/resources");

        List<Document> documents = loader.load();

        assertThat(documents).hasSize(3);
        assertThat(documents.get(0).text()).contains("test");
        assertThat(documents.get(1).text()).contains("test");
        assertThat(documents.get(2).text()).contains("test");
    }
}