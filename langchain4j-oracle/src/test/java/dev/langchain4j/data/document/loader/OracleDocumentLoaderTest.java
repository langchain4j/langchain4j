package dev.langchain4j.data.document.loader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.oracle.DirectoryPreference;
import dev.langchain4j.data.document.loader.oracle.FilePreference;
import dev.langchain4j.data.document.loader.oracle.OracleDocumentLoader;
import dev.langchain4j.data.document.loader.oracle.TablePreference;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.security.InvalidParameterException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLSyntaxErrorException;
import java.util.List;
import oracle.ucp.jdbc.PoolDataSource;
import oracle.ucp.jdbc.PoolDataSourceFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class OracleDocumentLoaderTest {

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
    void testFile() throws IOException, SQLException {
        ObjectMapper mapper = new ObjectMapper();
        FilePreference loaderPref = new FilePreference();
        loaderPref.setFilename(System.getenv("DEMO_DS_PDF_FILE"));
        String pref = mapper.writeValueAsString(loaderPref);

        List<Document> docs = loader.loadDocuments(pref);
        assertThat(docs.size()).isEqualTo(1);
        for (Document doc : docs) {
            assertThat(doc.text().length()).isGreaterThan(0);
        }
    }

    @Test
    @DisplayName("load from dir")
    void testDir() throws IOException, SQLException {
        ObjectMapper mapper = new ObjectMapper();
        DirectoryPreference loaderPref = new DirectoryPreference();
        loaderPref.setDirectory(System.getenv("DEMO_DS_DIR"));
        String pref = mapper.writeValueAsString(loaderPref);

        List<Document> docs = loader.loadDocuments(pref);
        assertThat(docs.size()).isGreaterThan(1);
        for (Document doc : docs) {
            assertThat(doc.text().length()).isGreaterThan(0);
        }
    }

    @Test
    @DisplayName("load from table")
    void testTable() throws IOException, SQLException {
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
    }

    @Test
    @DisplayName("load bad file pref")
    void testBadFilePref() throws IOException, SQLException {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode rootNode = mapper.createObjectNode();
        rootNode.put("file", System.getenv("DEMO_DS_PDF_FILE"));
        rootNode.put("extraProperty", "");
        String pref = mapper.writeValueAsString(rootNode);

        // Invalid file preference: unknown property specified
        Exception thrown = assertThrows(InvalidParameterException.class, () -> loader.loadDocuments(pref));
    }

    @Test
    @DisplayName("load bad table pref")
    void testBadTablePref() throws IOException, SQLException {
        ObjectMapper mapper = new ObjectMapper();
        TablePreference loaderPref = new TablePreference();
        loaderPref.setTableName("docs");
        loaderPref.setColumnName("text");
        String pref = mapper.writeValueAsString(loaderPref);

        // Invalid table preference: missing owner, table, or column name
        Exception thrown = assertThrows(InvalidParameterException.class, () -> loader.loadDocuments(pref));
    }

    @Test
    @DisplayName("load missing file")
    void testMissingFile() throws IOException, SQLException {
        ObjectMapper mapper = new ObjectMapper();
        FilePreference loaderPref = new FilePreference();
        loaderPref.setFilename("missing.txt");
        String pref = mapper.writeValueAsString(loaderPref);

        // java.nio.file.NoSuchFileException: missing.txt
        Exception thrown = assertThrows(NoSuchFileException.class, () -> loader.loadDocuments(pref));
    }

    @Test
    @DisplayName("load missing table")
    void testMissingTable() throws IOException, SQLException {
        ObjectMapper mapper = new ObjectMapper();
        TablePreference loaderPref = new TablePreference();
        loaderPref.setOwner("scott");
        loaderPref.setTableName("missing");
        loaderPref.setColumnName("text");
        String pref = mapper.writeValueAsString(loaderPref);

        // java.sql.SQLSyntaxErrorException: ORA-00942: table or view does not exist
        Exception thrown = assertThrows(SQLSyntaxErrorException.class, () -> loader.loadDocuments(pref));
    }
}
