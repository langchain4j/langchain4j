package dev.langchain4j.model.oracle;

import static dev.langchain4j.store.embedding.oracle.CreateOption.CREATE_OR_REPLACE;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.OracleContainerTestBase;
import dev.langchain4j.data.document.loader.OracleDocumentLoaderIT;
import dev.langchain4j.data.document.loader.oracle.FilePreference;
import dev.langchain4j.data.document.loader.oracle.OracleDocumentLoader;
import dev.langchain4j.data.document.splitter.oracle.OracleDocumentSplitter;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.oracle.EmbeddingTable;
import dev.langchain4j.store.embedding.oracle.OracleEmbeddingStore;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class OracleIngestIT extends OracleContainerTestBase {

    // NB: Test requires ALL-MINILM-L6-V2.onnx to be downloaded
    // and copied into the resource dir /models
    @Disabled("Run test manually")
    @Test
    @DisplayName("ingest")
    void testIngest() throws IOException, SQLException, URISyntaxException {
        String embedderPref;

        if (isContainerRunning()) {
            embedderPref = "{\"provider\": \"database\", \"model\": \"allmini\"}";

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
            }
        } else {
            embedderPref = "{\"provider\": \"database\", \"model\": \"" + System.getenv("DEMO_ONNX_MODEL") + "\"}";

            try (Connection conn = getConnection()) {
                boolean result = OracleEmbeddingModel.loadOnnxModel(
                        conn,
                        System.getenv("DEMO_ONNX_DIR"),
                        System.getenv("DEMO_ONNX_FILE"),
                        System.getenv("DEMO_ONNX_MODEL"));
            }
        }

        URL resourceUrl = OracleDocumentLoaderIT.class.getResource("/example-files/story-about-happy-carrot.txt");
        File file = Paths.get(resourceUrl.toURI()).toFile();
        String absolutePath = file.getAbsolutePath();

        try (Connection conn = getConnection()) {
            ObjectMapper mapper = new ObjectMapper();
            FilePreference filePref = new FilePreference();
            filePref.setFilename(absolutePath);
            String loaderPref = mapper.writeValueAsString(filePref);

            String splitterPref = "{\"by\": \"chars\", \"max\": 50}";

            OracleDocumentLoader loader = new OracleDocumentLoader(conn);
            OracleEmbeddingModel embedder = new OracleEmbeddingModel(conn, embedderPref);
            OracleDocumentSplitter splitter = new OracleDocumentSplitter(conn, splitterPref);

            // output table
            String tableName = "TEST";
            String idColumn = "ID";
            String embeddingColumn = "EMBEDDING";
            String textColumn = "TEXT";
            String metadataColumn = "METADATA";

            // The call to build() should create a table with the configured names
            OracleEmbeddingStore embeddingStore = OracleEmbeddingStore.builder()
                    .dataSource(getDataSource())
                    .embeddingTable(EmbeddingTable.builder()
                            .createOption(CREATE_OR_REPLACE)
                            .name(tableName)
                            .idColumn(idColumn)
                            .embeddingColumn(embeddingColumn)
                            .textColumn(textColumn)
                            .metadataColumn(metadataColumn)
                            .build())
                    .build();

            List<Document> docs = loader.loadDocuments(loaderPref);

            EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                    .documentSplitter(splitter)
                    .embeddingModel(embedder)
                    .embeddingStore(embeddingStore)
                    .build();
            ingestor.ingest(docs);

            int count = getCount(conn, tableName);
            assertThat(count).isGreaterThan(0);
        }
    }

    int getCount(Connection conn, String tableName) throws SQLException {
        int count = 0;
        String query = "select count(*) from " + tableName;
        try (PreparedStatement stmt = conn.prepareStatement(query);
                ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                count = rs.getInt(1);
            }
        }
        return count;
    }
}
