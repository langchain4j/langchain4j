package dev.langchain4j.model.oracle;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import io.github.cdimascio.dotenv.Dotenv;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

public class OracleEmbeddingModelTest {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(OracleEmbeddingModelTest.class);

    Dotenv dotenv;
    Connection conn;

    @BeforeEach
    void setUp() throws SQLException {
        dotenv = Dotenv.configure().load();

        conn = DriverManager.getConnection(
                dotenv.get("ORACLE_JDBC_URL"), dotenv.get("ORACLE_JDBC_USER"), dotenv.get("ORACLE_JDBC_PASSWORD"));
    }

    @Test
    @DisplayName("embed with provider=database")
    void testEmbedONNX() {
        try {
            String pref = "{\"provider\": \"database\", \"model\": \"" + dotenv.get("DEMO_ONNX_MODEL") + "\"}";

            OracleEmbeddingModel embedder = new OracleEmbeddingModel(conn, pref);

            boolean result = OracleEmbeddingModel.loadOnnxModel(
                    conn, dotenv.get("DEMO_ONNX_DIR"), dotenv.get("DEMO_ONNX_FILE"), dotenv.get("DEMO_ONNX_MODEL"));
            assertThat(result).isEqualTo(true);

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
        } catch (SQLException ex) {
            String message = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
            log.error(message);
        }
    }

    @Test
    @DisplayName("embed with provider=ocigenai")
    void testEmbedOcigenai() {
        String pref = "{\n"
                + "  \"provider\": \"ocigenai\",\n"
                + "  \"credential_name\": \"OCI_CRED\",\n"
                + "  \"url\": \"https://inference.generativeai.us-chicago-1.oci.oraclecloud.com/20231130/actions/embedText\",\n"
                + "  \"model\": \"cohere.embed-english-light-v3.0\"\n"
                + "}";
        String proxy = dotenv.get("DEMO_PROXY");

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
    }
}
