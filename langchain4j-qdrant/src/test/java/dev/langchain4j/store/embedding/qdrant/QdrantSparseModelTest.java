package dev.langchain4j.store.embedding.qdrant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.qdrant.client.grpc.Points;
import java.util.Map;
import org.junit.jupiter.api.Test;

class QdrantSparseModelTest {

    @Test
    void should_build_bm25_document_with_default_options() {
        Points.Document document = QdrantSparseModel.bm25().toDocument("hello world");

        assertThat(document.getText()).isEqualTo("hello world");
        assertThat(document.getModel()).isEqualTo(QdrantSparseModel.BM25_MODEL);
        assertThat(document.getOptionsMap()).isEmpty();
    }

    @Test
    void should_map_bm25_options_to_qdrant_keys() {
        QdrantSparseModel model = QdrantSparseModel.bm25Builder()
                .language("german")
                .k(1.5)
                .b(0.8)
                .avgLen(42)
                .lowercase(false)
                .asciiFolding(true)
                .tokenizer("multilingual")
                .minTokenLen(2)
                .maxTokenLen(20)
                .option("stopwords", new String[] {"foo", "bar"})
                .build();

        Points.Document document = model.toDocument("text");

        assertThat(document.getOptionsMap().get("language").getStringValue()).isEqualTo("german");
        assertThat(document.getOptionsMap().get("k").getDoubleValue()).isEqualTo(1.5);
        assertThat(document.getOptionsMap().get("b").getDoubleValue()).isEqualTo(0.8);
        assertThat(document.getOptionsMap().get("avg_len").getDoubleValue()).isEqualTo(42.0);
        assertThat(document.getOptionsMap().get("lowercase").getBoolValue()).isFalse();
        assertThat(document.getOptionsMap().get("ascii_folding").getBoolValue()).isTrue();
        assertThat(document.getOptionsMap().get("tokenizer").getStringValue()).isEqualTo("multilingual");
        assertThat(document.getOptionsMap().get("min_token_len").getIntegerValue())
                .isEqualTo(2);
        assertThat(document.getOptionsMap().get("max_token_len").getIntegerValue())
                .isEqualTo(20);
        assertThat(document.getOptionsMap().get("stopwords").getListValue().getValuesCount())
                .isEqualTo(2);
    }

    @Test
    void should_build_custom_model() {
        QdrantSparseModel model = QdrantSparseModel.of("prithivida/splade_pp_en_v1", Map.of("foo", "bar"));

        Points.Document document = model.toDocument("text");

        assertThat(document.getModel()).isEqualTo("prithivida/splade_pp_en_v1");
        assertThat(document.getOptionsMap().get("foo").getStringValue()).isEqualTo("bar");
    }

    @Test
    void should_reject_blank_model() {
        assertThatThrownBy(() -> QdrantSparseModel.of(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("model");
    }
}
