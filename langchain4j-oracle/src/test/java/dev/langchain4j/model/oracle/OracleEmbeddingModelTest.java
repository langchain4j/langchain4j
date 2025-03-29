package dev.langchain4j.model.oracle;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import oracle.ucp.jdbc.PoolDataSource;
import oracle.ucp.jdbc.PoolDataSourceFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class OracleEmbeddingModelTest {

    Connection conn;

    @BeforeEach
    void setUp() throws SQLException {
        PoolDataSource pds = PoolDataSourceFactory.getPoolDataSource();
        pds.setConnectionFactoryClassName("oracle.jdbc.pool.OracleDataSource");
        pds.setURL(System.getenv("ORACLE_JDBC_URL"));
        pds.setUser(System.getenv("ORACLE_JDBC_USER"));
        pds.setPassword(System.getenv("ORACLE_JDBC_PASSWORD"));
        conn = pds.getConnection();
    }

    @Test
    @DisplayName("embed with provider=database")
    void testEmbedONNX() throws SQLException {
        String pref = "{\"provider\": \"database\", \"model\": \"" + System.getenv("DEMO_ONNX_MODEL") + "\"}";

        OracleEmbeddingModel embedder = new OracleEmbeddingModel(conn, pref);

        boolean result = OracleEmbeddingModel.loadOnnxModel(
                conn,
                System.getenv("DEMO_ONNX_DIR"),
                System.getenv("DEMO_ONNX_FILE"),
                System.getenv("DEMO_ONNX_MODEL"));
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
        String proxy = System.getenv("DEMO_PROXY");

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

    @AfterEach
    void tearDown() throws SQLException {
        conn.close();
    }
}
