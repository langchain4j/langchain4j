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
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.security.InvalidParameterException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLSyntaxErrorException;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class OracleDocumentLoaderIT extends OracleContainerTestBase {

    @Test
    @DisplayName("load from file")
    void testFile() throws IOException, SQLException, URISyntaxException {
        try (Connection conn = getConnection()) {
            OracleDocumentLoader loader = new OracleDocumentLoader(conn);

            URL resourceUrl = OracleDocumentLoaderIT.class.getResource("/example-files/story-about-happy-carrot.txt");
            File file = Paths.get(resourceUrl.toURI()).toFile();
            String absolutePath = file.getAbsolutePath();

            ObjectMapper mapper = new ObjectMapper();
            FilePreference loaderPref = new FilePreference();
            loaderPref.setFilename(absolutePath);
            String pref = mapper.writeValueAsString(loaderPref);

            List<Document> docs = loader.loadDocuments(pref);
            assertThat(docs.size()).isEqualTo(1);
            for (Document doc : docs) {
                assertThat(doc.text().length()).isGreaterThan(0);
            }
        }
    }

    @Test
    @DisplayName("load from dir")
    void testDir() throws IOException, SQLException, URISyntaxException {
        try (Connection conn = getConnection()) {
            OracleDocumentLoader loader = new OracleDocumentLoader(conn);

            URL resourceUrl = OracleDocumentLoaderIT.class.getResource("/example-files");
            File file = Paths.get(resourceUrl.toURI()).toFile();
            String absolutePath = file.getAbsolutePath();

            ObjectMapper mapper = new ObjectMapper();
            DirectoryPreference loaderPref = new DirectoryPreference();
            loaderPref.setDirectory(absolutePath);
            String pref = mapper.writeValueAsString(loaderPref);

            List<Document> docs = loader.loadDocuments(pref);
            assertThat(docs.size()).isGreaterThan(1);
            for (Document doc : docs) {
                assertThat(doc.text().length()).isGreaterThan(0);
            }
        }
    }

    @Test
    @DisplayName("load from table")
    void testTable() throws IOException, SQLException {
        TablePreference loaderPref = new TablePreference();

        if (isContainerRunning()) {
            copyResourceFile("/example-files/story-about-happy-carrot.docx", "/tmp/story-about-happy-carrot.docx");
            copyResourceFile("/example-files/story-about-happy-carrot.pdf", "/tmp/story-about-happy-carrot.pdf");
            copyResourceFile("/example-files/story-about-happy-carrot.txt", "/tmp/story-about-happy-carrot.txt");

            try (Connection conn = getSysConnection()) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.addBatch("grant create any directory to testuser");
                    stmt.executeBatch();
                }
            }

            try (Connection conn = getConnection()) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.addBatch("create or replace directory DATA_DIR as '/tmp'");
                    stmt.addBatch("drop table if exists docs");
                    stmt.addBatch("create table docs(id number primary key, text blob)");
                    stmt.addBatch(
                            "insert into docs values(1, to_blob(bfilename('DATA_DIR', 'story-about-happy-carrot.docx')))");
                    stmt.addBatch(
                            "insert into docs values(2, to_blob(bfilename('DATA_DIR', 'story-about-happy-carrot.pdf')))");
                    stmt.addBatch(
                            "insert into docs values(3, to_blob(bfilename('DATA_DIR', 'story-about-happy-carrot.txt')))");
                    stmt.executeBatch();
                }

                loaderPref.setOwner("testuser");
                loaderPref.setTableName("docs");
                loaderPref.setColumnName("text");
            }
        } else {
            loaderPref.setOwner(System.getenv("DEMO_OWNER"));
            loaderPref.setTableName(System.getenv("DEMO_TABLE"));
            loaderPref.setColumnName(System.getenv("DEMO_COLUMN"));
        }

        try (Connection conn = getConnection()) {
            ObjectMapper mapper = new ObjectMapper();
            String pref = mapper.writeValueAsString(loaderPref);

            OracleDocumentLoader loader = new OracleDocumentLoader(conn);
            List<Document> docs = loader.loadDocuments(pref);
            assertThat(docs.size()).isGreaterThan(1);
            for (Document doc : docs) {
                assertThat(doc.text().length()).isGreaterThan(0);
            }
        }
    }

    @Test
    @DisplayName("load bad file pref")
    void testBadFilePref() throws IOException, SQLException {
        try (Connection conn = getConnection()) {
            OracleDocumentLoader loader = new OracleDocumentLoader(conn);

            ObjectMapper mapper = new ObjectMapper();
            ObjectNode rootNode = mapper.createObjectNode();
            rootNode.put("file", "file.txt");
            rootNode.put("extraProperty", "");
            String pref = mapper.writeValueAsString(rootNode);

            // Invalid file preference: unknown property specified
            Exception thrown = assertThrows(InvalidParameterException.class, () -> loader.loadDocuments(pref));
        }
    }

    @Test
    @DisplayName("load bad table pref")
    void testBadTablePref() throws IOException, SQLException {
        try (Connection conn = getConnection()) {
            OracleDocumentLoader loader = new OracleDocumentLoader(conn);

            ObjectMapper mapper = new ObjectMapper();
            TablePreference loaderPref = new TablePreference();
            loaderPref.setTableName("docs");
            loaderPref.setColumnName("text");
            String pref = mapper.writeValueAsString(loaderPref);

            // Invalid table preference: missing owner, table, or column name
            Exception thrown = assertThrows(InvalidParameterException.class, () -> loader.loadDocuments(pref));
        }
    }

    @Test
    @DisplayName("load missing file")
    void testMissingFile() throws IOException, SQLException {
        try (Connection conn = getConnection()) {
            OracleDocumentLoader loader = new OracleDocumentLoader(conn);

            ObjectMapper mapper = new ObjectMapper();
            FilePreference loaderPref = new FilePreference();
            loaderPref.setFilename("missing.txt");
            String pref = mapper.writeValueAsString(loaderPref);

            // java.nio.file.NoSuchFileException: missing.txt
            Exception thrown = assertThrows(NoSuchFileException.class, () -> loader.loadDocuments(pref));
        }
    }

    @Test
    @DisplayName("load missing table")
    void testMissingTable() throws IOException, SQLException {
        try (Connection conn = getConnection()) {
            OracleDocumentLoader loader = new OracleDocumentLoader(conn);

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
}
