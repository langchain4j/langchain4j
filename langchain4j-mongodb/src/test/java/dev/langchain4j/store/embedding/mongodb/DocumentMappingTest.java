package dev.langchain4j.store.embedding.mongodb;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.assertj.core.util.Maps;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DocumentMappingTest {
    DocumentMapping documentMapping = new DocumentMapping();

    @Test
    void generateDocument() {
        String id = "id";
        Embedding embedding = new Embedding(new float[]{1.0f, 2.0f});
        TextSegment textSegment = new TextSegment("test", Metadata.from("key", "value"));

        EmbeddingDocument embeddingDocument = documentMapping.generateDocument(id, embedding, textSegment);

        assertThat(embeddingDocument.getId()).isEqualTo(id);
        assertThat(embeddingDocument.getEmbedding()).isEqualTo(Arrays.asList(1.0D, 2.0D));
        assertThat(embeddingDocument.getText()).isEqualTo("test");
        assertThat(embeddingDocument.getMetadata()).containsEntry("key", "value");
    }

    @Test
    void generateDocumentWithoutTextSegment() {
        String id = "id";
        Embedding embedding = new Embedding(new float[]{1.0f, 2.0f});

        EmbeddingDocument embeddingDocument = documentMapping.generateDocument(id, embedding, null);

        assertThat(embeddingDocument.getId()).isEqualTo(id);
        assertThat(embeddingDocument.getEmbedding()).isEqualTo(Arrays.asList(1.0D, 2.0D));
        assertThat(embeddingDocument.getText()).isNull();
        assertThat(embeddingDocument.getMetadata()).isNull();
    }

    @Test
    void generateDocumentFloatPrecisionIssue() {
        String id = "id";
        Embedding embedding = new Embedding(new float[]{1 / 3f});
        TextSegment textSegment = new TextSegment("test", Metadata.from("key", "value"));

        EmbeddingDocument embeddingDocument = documentMapping.generateDocument(id, embedding, textSegment);

        assertThat(embeddingDocument.getEmbedding().get(0)).isEqualTo(1 / 3f, within(0.00000001f));
    }

    @Test
    void asDoublesListRequiredEmbedding() {
        assertThrows(NullPointerException.class, () -> documentMapping.generateDocument(null, null, new TextSegment("test", Metadata.from("key", "value"))));
    }

    @Test
    void asDoublesListRequiredTextSegment() {
        assertThrows(NullPointerException.class, () -> documentMapping.generateDocument(null, new Embedding(new float[]{1 / 3f}), null));
    }

    @Test
    void asTextSegmentEmbeddingMatch() {
        Double score = 1 / 3D;
        List<Float> embedding = Arrays.asList(1.0F, 2.0F, 1 / 3F);
        EmbeddingMatchDocument matchDocument = new EmbeddingMatchDocument("id", embedding, "text", Maps.newHashMap("key", "value"), score);
        EmbeddingMatch<TextSegment> match = documentMapping.asTextSegmentEmbeddingMatch(matchDocument);

        assertThat(match).isEqualTo(new EmbeddingMatch<>(
                score, "id", new Embedding(new float[]{1.0f, 2.0f, 1 / 3f}),
                new TextSegment("text", Metadata.from("key", "value"))));
    }

    @Test
    void asTextSegmentEmbeddingMatchRequiredInput() {
        assertThrows(NullPointerException.class, () -> {
            documentMapping.asTextSegmentEmbeddingMatch(null);
        });
    }

}