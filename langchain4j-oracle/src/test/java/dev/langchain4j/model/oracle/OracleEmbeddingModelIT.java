package dev.langchain4j.model.oracle;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.document.loader.OracleContainerTestBase;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class OracleEmbeddingModelIT extends OracleContainerTestBase {

    // NB: Test requires ALL-MINILM-L6-V2.onnx to be downloaded
    // and copied into the resource dir /models
    @Disabled("Run test manually")
    @Test
    @DisplayName("embed with provider=database")
    void testEmbedONNX() throws SQLException {
        String pref;

        if (isContainerRunning()) {
            pref = "{\"provider\": \"database\", \"model\": \"allmini\"}";

            copyResourceFile("/models/ALL-MINILM-L6-V2.onnx", "/tmp/ALL-MINILM-L6-V2.onnx");

            try (Connection conn = getSysConnection()) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.addBatch("grant create any directory to testuser");
                    stmt.addBatch("grant create mining model to testuser");
                    stmt.executeBatch();
                }
            }

            try (Connection conn = getConnection()) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.addBatch("create or replace directory MODEL_DIR as '/tmp'");
                    stmt.executeBatch();
                }

                boolean result =
                        OracleEmbeddingModel.loadOnnxModel(conn, "MODEL_DIR", "ALL-MINILM-L6-V2.onnx", "allmini");
                assertThat(result).isEqualTo(true);
            }
        } else {
            pref = "{\"provider\": \"database\", \"model\": \"" + System.getenv("DEMO_ONNX_MODEL") + "\"}";

            try (Connection conn = getConnection()) {
                boolean result = OracleEmbeddingModel.loadOnnxModel(
                        conn,
                        System.getenv("DEMO_ONNX_DIR"),
                        System.getenv("DEMO_ONNX_FILE"),
                        System.getenv("DEMO_ONNX_MODEL"));
                assertThat(result).isEqualTo(true);
            }
        }

        try (Connection conn = getConnection()) {
            OracleEmbeddingModel embedder = new OracleEmbeddingModel(conn, pref);
            embedder = new OracleEmbeddingModel(conn, pref);

            Response<Embedding> resp = embedder.embed("hello world");
            assertThat(resp.content().dimension()).isGreaterThan(1);

            TextSegment segment = TextSegment.from("hello world");
            Response<Embedding> resp2 = embedder.embed(segment);
            assertThat(resp2.content().dimension()).isGreaterThan(1);

            List<TextSegment> textSegments = new ArrayList<>();
            textSegments.add(TextSegment.from("hello world"));
            textSegments.add(TextSegment.from("goodbye world"));
            textSegments.add(TextSegment.from("1,2,3"));
            Response<List<Embedding>> resp3 = embedder.embedAll(textSegments);
            assertThat(resp3.content().size()).isEqualTo(3);

            // default is with batching enabled
            // embed with batching disabled
            embedder.setBatching(false);
            Response<List<Embedding>> resp4 = embedder.embedAll(textSegments);
            assertThat(resp4.content().size()).isEqualTo(3);
        }
    }

    @Test
    @DisplayName("embed with provider=ocigenai")
    void testEmbedOcigenai() throws SQLException {
        if (isContainerRunning()) return;

        String pref = "{\n"
                + "  \"provider\": \"ocigenai\",\n"
                + "  \"credential_name\": \"OCI_CRED\",\n"
                + "  \"url\": \"https://inference.generativeai.us-chicago-1.oci.oraclecloud.com/20231130/actions/embedText\",\n"
                + "  \"model\": \"cohere.embed-english-light-v3.0\"\n"
                + "}";
        String proxy = System.getenv("DEMO_PROXY");

        try (Connection conn = getConnection()) {
            OracleEmbeddingModel embedder = new OracleEmbeddingModel(conn, pref, proxy);

            Response<Embedding> resp = embedder.embed("hello world");
            assertThat(resp.content().dimension()).isGreaterThan(1);

            TextSegment segment = TextSegment.from("hello world");
            Response<Embedding> resp2 = embedder.embed(segment);
            assertThat(resp2.content().dimension()).isGreaterThan(1);

            List<TextSegment> textSegments = new ArrayList<>();
            textSegments.add(TextSegment.from("hello world"));
            textSegments.add(TextSegment.from("goodbye world"));
            textSegments.add(TextSegment.from("1,2,3"));
            Response<List<Embedding>> resp3 = embedder.embedAll(textSegments);
            assertThat(resp3.content().size()).isEqualTo(3);

            // default is with batching enabled
            // embed with batching disabled
            embedder.setBatching(false);
            Response<List<Embedding>> resp4 = embedder.embedAll(textSegments);
            assertThat(resp4.content().size()).isEqualTo(3);
        }
    }
}
