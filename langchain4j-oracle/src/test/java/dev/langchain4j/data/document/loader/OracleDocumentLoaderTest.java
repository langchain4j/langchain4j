package dev.langchain4j.data.document.loader;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.oracle.DirectoryPreference;
import dev.langchain4j.data.document.loader.oracle.FilePreference;
import dev.langchain4j.data.document.loader.oracle.OracleDocumentLoader;
import dev.langchain4j.data.document.loader.oracle.TablePreference;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import oracle.ucp.jdbc.PoolDataSource;
import oracle.ucp.jdbc.PoolDataSourceFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

public class OracleDocumentLoaderTest {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(OracleDocumentLoaderTest.class);

    OracleDocumentLoader loader;

    @BeforeEach
    void setUp() throws SQLException {
        PoolDataSource pds = PoolDataSourceFactory.getPoolDataSource();
        pds.setConnectionFactoryClassName("oracle.jdbc.pool.OracleDataSource");
        pds.setURL(System.getenv("ORACLE_JDBC_URL"));
        pds.setUser(System.getenv("ORACLE_JDBC_USER"));
        pds.setPassword(System.getenv("ORACLE_JDBC_PASSWORD"));
        Connection conn = pds.getConnection();

        loader = new OracleDocumentLoader(conn);
    }

    @Test
    @DisplayName("load from file")
    void testFile() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            FilePreference loaderPref = new FilePreference();
            loaderPref.setFile(System.getenv("DEMO_DS_PDF_FILE"));
            String pref = mapper.writeValueAsString(loaderPref);

            List<Document> docs = loader.loadDocuments(pref);
            assertThat(docs.size()).isEqualTo(1);
            for (Document doc : docs) {
                assertThat(doc.text().length()).isGreaterThan(0);
            }
        } catch (IOException | SQLException ex) {
            String message = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
            log.error(message);
        }
    }

    @Test
    @DisplayName("load from dir")
    void testDir() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            DirectoryPreference loaderPref = new DirectoryPreference();
            loaderPref.setDirectory(System.getenv("DEMO_DS_DIR"));
            String pref = mapper.writeValueAsString(loaderPref);

            List<Document> docs = loader.loadDocuments(pref);
            assertThat(docs.size()).isGreaterThan(1);
            for (Document doc : docs) {
                assertThat(doc.text().length()).isGreaterThan(0);
            }
        } catch (IOException | SQLException ex) {
            String message = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
            log.error(message);
        }
    }

    @Test
    @DisplayName("load from table")
    void testTable() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            TablePreference loaderPref = new TablePreference();
            loaderPref.setOwner(System.getenv("DEMO_DS_OWNER"));
            loaderPref.setTableName(System.getenv("DEMO_DS_TABLE"));
            loaderPref.setColumnName(System.getenv("DEMO_DS_COLUMN"));
            String pref = mapper.writeValueAsString(loaderPref);

            List<Document> docs = loader.loadDocuments(pref);
            assertThat(docs.size()).isGreaterThan(1);
            for (Document doc : docs) {
                assertThat(doc.text().length()).isGreaterThan(0);
            }
        } catch (IOException | SQLException ex) {
            String message = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
            log.error(message);
        }
    }
}
