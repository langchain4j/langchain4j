package dev.langchain4j.store.embedding.azure.cosmos.nosql;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.embedding.Embedding;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class AzureCosmosDbNoSqlDocumentTest {

    @Test
    void testDocumentCreation() {
        String id = "test-id-123";
        String text = "This is a test document";
        Embedding embedding = Embedding.from(Arrays.asList(0.1f, 0.2f, 0.3f));
        Map<String, String> metadata = new HashMap<>();
        metadata.put("category", "test");

        AzureCosmosDbNoSqlDocument document =
                new AzureCosmosDbNoSqlDocument(id, embedding.vectorAsList(), text, metadata);

        assertThat(document.getId()).isEqualTo(id);
        assertThat(document.getText()).isEqualTo(text);
        assertThat(document.getEmbedding()).isEqualTo(embedding.vectorAsList());
        assertThat(document.getMetadata()).isEqualTo(metadata);
    }

    @Test
    void testMatchedDocumentCreation() {
        String id = "matched-test-id";
        String text = "This is a matched document";
        Embedding embedding = Embedding.from(Arrays.asList(0.4f, 0.5f, 0.6f));
        Map<String, String> metadata = new HashMap<>();
        metadata.put("type", "matched");
        double score = 0.95;

        AzureCosmosDbNoSqlMatchedDocument matchedDocument =
                new AzureCosmosDbNoSqlMatchedDocument(id, embedding.vectorAsList(), text, metadata, score);

        assertThat(matchedDocument.getId()).isEqualTo(id);
        assertThat(matchedDocument.getText()).isEqualTo(text);
        assertThat(matchedDocument.getEmbedding()).isEqualTo(embedding.vectorAsList());
        assertThat(matchedDocument.getMetadata()).isEqualTo(metadata);
        assertThat(matchedDocument.getScore()).isEqualTo(score);
    }

    @Test
    void testDocumentWithNullMetadata() {
        String id = "test-null-metadata";
        String text = "Document without metadata";
        Embedding embedding = Embedding.from(Arrays.asList(0.7f, 0.8f, 0.9f));

        AzureCosmosDbNoSqlDocument document = new AzureCosmosDbNoSqlDocument(id, embedding.vectorAsList(), text, null);

        assertThat(document.getId()).isEqualTo(id);
        assertThat(document.getText()).isEqualTo(text);
        assertThat(document.getEmbedding()).isEqualTo(embedding.vectorAsList());
        assertThat(document.getMetadata()).isNull();
    }

    @Test
    void testDocumentEquality() {
        String id = "equality-test";
        String text = "Equality test document";
        Embedding embedding = Embedding.from(Arrays.asList(1.0f, 2.0f, 3.0f));
        Map<String, String> metadata = new HashMap<>();
        metadata.put("test", "equality");

        AzureCosmosDbNoSqlDocument document1 =
                new AzureCosmosDbNoSqlDocument(id, embedding.vectorAsList(), text, metadata);
        AzureCosmosDbNoSqlDocument document2 =
                new AzureCosmosDbNoSqlDocument(id, embedding.vectorAsList(), text, metadata);

        assertThat(document1).isEqualTo(document2);
        assertThat(document1.hashCode()).isEqualTo(document2.hashCode());
    }
}
