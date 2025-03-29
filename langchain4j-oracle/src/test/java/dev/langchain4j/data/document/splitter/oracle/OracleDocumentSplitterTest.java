package dev.langchain4j.data.document.splitter.oracle;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import oracle.ucp.jdbc.PoolDataSource;
import oracle.ucp.jdbc.PoolDataSourceFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class OracleDocumentSplitterTest {

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
    @DisplayName("split string input by chars")
    void testByChars() throws IOException, SQLException {
        String pref = "{\"by\": \"chars\", \"max\": 50}";
        String filename = System.getenv("DEMO_DS_TEXT_FILE");

        OracleDocumentSplitter splitter = new OracleDocumentSplitter(conn, pref);

        String content = readFile(filename, Charset.forName("UTF-8"));
        String[] chunks = splitter.split(content);
        assertThat(chunks.length).isGreaterThan(1);
    }

    @Test
    @DisplayName("split string input by words")
    void testByWords() throws IOException, SQLException {
        String pref = "{\"by\": \"words\", \"max\": 50}";
        String filename = System.getenv("DEMO_DS_TEXT_FILE");
        ;

        OracleDocumentSplitter splitter = new OracleDocumentSplitter(conn, pref);

        String content = readFile(filename, Charset.forName("UTF-8"));
        String[] chunks = splitter.split(content);
        assertThat(chunks.length).isGreaterThan(1);
    }

    @Test
    @DisplayName("split Doc input by chars")
    void testDocByChars() throws IOException {
        String pref = "{\"by\": \"chars\", \"max\": 50}";
        String filename = System.getenv("DEMO_DS_TEXT_FILE");

        OracleDocumentSplitter splitter = new OracleDocumentSplitter(conn, pref);

        String content = readFile(filename, Charset.forName("UTF-8"));

        // Create a document with some metadata
        Metadata metadata = new Metadata();
        metadata.put("a", 1);
        metadata.put("b", 2);
        Document document = Document.from(content, metadata);

        List<TextSegment> chunks = splitter.split(document);
        assertThat(chunks.size()).isGreaterThan(1);

        // Check that the metadata was passed
        TextSegment chunk = chunks.get(0);
        int a = chunk.metadata().getInteger("a");
        assertThat(a).isEqualTo(1);
    }

    @AfterEach
    void tearDown() throws SQLException {
        conn.close();
    }

    static String readFile(String path, Charset encoding) throws IOException {
        byte[] bytes = Files.readAllBytes(Paths.get(path));
        return new String(bytes, encoding);
    }
}
