package dev.langchain4j.store.embedding.azure.search;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.document.Metadata;
import org.junit.jupiter.api.Test;

public class DocumentTest {

    @Test
    void testMetadataConversion() {
        dev.langchain4j.data.document.Document document =
                dev.langchain4j.data.document.Document.document("test", Metadata.metadata("keyTest", "valueTest"));
        Document.Metadata metadata = new Document.Metadata();
        metadata.setAttributes(document.metadata());
        assertThat(metadata.getAttributes().stream().toList().get(0).getKey().contains("keyTest"))
                .isEqualTo(true);
    }
}
