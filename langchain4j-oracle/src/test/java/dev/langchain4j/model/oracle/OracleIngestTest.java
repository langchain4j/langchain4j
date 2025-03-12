package dev.langchain4j.model.oracle;

import static dev.langchain4j.store.embedding.oracle.CreateOption.CREATE_OR_REPLACE;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.oracle.OracleDocumentLoader;
import dev.langchain4j.data.document.splitter.oracle.OracleDocumentSplitter;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.oracle.EmbeddingTable;
import dev.langchain4j.store.embedding.oracle.OracleEmbeddingStore;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import oracle.ucp.jdbc.PoolDataSource;
import oracle.ucp.jdbc.PoolDataSourceFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

public class OracleIngestTest {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(OracleEmbeddingModelTest.class);

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
    @DisplayName("ingest")
    void testIngest() {
        try {
            String embedderPref =
                    "{\"provider\": \"database\", \"model\": \"" + System.getenv("DEMO_ONNX_MODEL") + "\"}";
            String splitterPref = "{\"by\": \"chars\", \"max\": 50}";

            OracleDocumentLoader loader = new OracleDocumentLoader(conn);
            OracleEmbeddingModel embedder = new OracleEmbeddingModel(conn, embedderPref);
            OracleDocumentSplitter splitter = new OracleDocumentSplitter(conn, splitterPref);

            oracle.jdbc.datasource.OracleDataSource ods = new oracle.jdbc.datasource.impl.OracleDataSource();
            ods.setURL(System.getenv("ORACLE_JDBC_URL"));
            ods.setUser(System.getenv("ORACLE_JDBC_USER"));
            ods.setPassword(System.getenv("ORACLE_JDBC_PASSWORD"));

            // output table
            String tableName = "TEST";
            String idColumn = "ID";
            String embeddingColumn = "EMBEDDING";
            String textColumn = "TEXT";
            String metadataColumn = "METADATA";

            // The call to build() should create a table with the configured names
            OracleEmbeddingStore embeddingStore = OracleEmbeddingStore.builder()
                    .dataSource(ods)
                    .embeddingTable(EmbeddingTable.builder()
                            .createOption(CREATE_OR_REPLACE)
                            .name(tableName)
                            .idColumn(idColumn)
                            .embeddingColumn(embeddingColumn)
                            .textColumn(textColumn)
                            .metadataColumn(metadataColumn)
                            .build())
                    .build();

            boolean result = OracleEmbeddingModel.loadOnnxModel(
                    conn,
                    System.getenv("DEMO_ONNX_DIR"),
                    System.getenv("DEMO_ONNX_FILE"),
                    System.getenv("DEMO_ONNX_MODEL"));

            String loaderPref = "{\"file\": \"" + System.getenv("DEMO_DS_PDF_FILE") + "\"}";
            List<Document> docs = loader.loadDocuments(loaderPref);

            EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                    .documentSplitter(splitter)
                    .embeddingModel(embedder)
                    .embeddingStore(embeddingStore)
                    .build();
            ingestor.ingest(docs);

            /*
            // for debugging. ingest output should match
            System.out.println("# docs=" + docs.size());
            List<TextSegment> splits = splitter.splitAll(docs);
            System.out.println("# split=" + splits.size());
            Response<List<dev.langchain4j.data.embedding.Embedding>> embeddings = embedder.embedAll(splits);
            System.out.println("# embedded=" + embeddings.content().size());
            */

            int count = getCount(tableName);
            assertThat(count).isGreaterThan(0);
        } catch (SQLException | IOException ex) {
            String message = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
            log.error(message);
        }
    }

    int getCount(String tableName) throws SQLException {
        int count = 0;
        String query = "select count(*) from " + tableName;
        PreparedStatement stmt = conn.prepareStatement(query);
        try (ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                count = rs.getInt(1);
            }
        }
        return count;
    }

    @AfterEach
    void tearDown() throws SQLException {
        conn.close();
    }
}
