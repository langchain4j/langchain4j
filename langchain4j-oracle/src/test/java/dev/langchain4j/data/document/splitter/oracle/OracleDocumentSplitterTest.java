package dev.langchain4j.data.document.splitter.oracle;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import io.github.cdimascio.dotenv.Dotenv;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

public class OracleDocumentSplitterTest {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(OracleDocumentSplitterTest.class);

    Dotenv dotenv;
    Connection conn;

    @BeforeEach
    void setUp() throws SQLException {
        dotenv = Dotenv.configure().load();

        conn = DriverManager.getConnection(
                dotenv.get("ORACLE_JDBC_URL"), dotenv.get("ORACLE_JDBC_USER"), dotenv.get("ORACLE_JDBC_PASSWORD"));
    }

    @Test
    @DisplayName("split string input by chars")
    void testByChars() {
        String pref = "{\"by\": \"chars\", \"max\": 50}";
        String filename = dotenv.get("DEMO_DS_TEXT_FILE");

        try {
            OracleDocumentSplitter splitter = new OracleDocumentSplitter(conn, pref);

            String content = readFile(filename, Charset.forName("UTF-8"));
            String[] chunks = splitter.split(content);
            assertThat(chunks.length).isGreaterThan(1);
        } catch (IOException | SQLException ex) {
            String message = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
            log.error(message);
        }
    }

    @Test
    @DisplayName("split string input by words")
    void testByWords() {
        String pref = "{\"by\": \"words\", \"max\": 50}";
        String filename = dotenv.get("DEMO_DS_TEXT_FILE");
        ;

        try {
            OracleDocumentSplitter splitter = new OracleDocumentSplitter(conn, pref);

            String content = readFile(filename, Charset.forName("UTF-8"));
            String[] chunks = splitter.split(content);
            assertThat(chunks.length).isGreaterThan(1);
        } catch (IOException | SQLException ex) {
            String message = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
            log.error(message);
        }
    }

    @Test
    @DisplayName("split Doc input by chars")
    void testDocByChars() {
        String pref = "{\"by\": \"chars\", \"max\": 50}";
        String filename = dotenv.get("DEMO_DS_TEXT_FILE");

        try {
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
        } catch (IOException ex) {
            String message = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
            log.error(message);
        }
    }

    static String readFile(String path, Charset encoding) throws IOException {
        byte[] bytes = Files.readAllBytes(Paths.get(path));
        return new String(bytes, encoding);
    }
}
