package dev.langchain4j.data.document.splitter.oracle;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.loader.OracleContainerTestBase;
import dev.langchain4j.data.segment.TextSegment;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class OracleDocumentSplitterIT extends OracleContainerTestBase {

    String content = "The tower is 324 meters (1,063 ft) tall, "
            + "about the same height as an 81-storey building, and the tallest "
            + "structure in Paris. Its base is square, measuring 125 meters (410 ft) "
            + "on each side. During its construction, the Eiffel Tower surpassed the "
            + "Washington Monument to become the tallest man-made structure in the world, "
            + "a title it held for 41 years until the Chrysler Building in New York City "
            + "was finished in 1930. It was the first structure to reach a height of "
            + "300 meters. Due to the addition of a broadcasting aerial at the top "
            + "of the tower in 1957, it is now taller than the Chrysler Building "
            + "by 5.2 meters (17 ft). Excluding transmitters, the Eiffel Tower is "
            + "the second tallest free-standing structure in France after the "
            + "Millau Viaduct.";

    @Test
    @DisplayName("split string input by chars")
    void testByChars() throws IOException, SQLException {
        try (Connection conn = getConnection()) {
            String pref = "{\"by\": \"chars\", \"max\": 50}";
            OracleDocumentSplitter splitter = new OracleDocumentSplitter(conn, pref);

            String[] chunks = splitter.split(content);
            assertThat(chunks.length).isGreaterThan(1);
        }
    }

    @Test
    @DisplayName("split string input by words")
    void testByWords() throws IOException, SQLException {
        try (Connection conn = getConnection()) {
            String pref = "{\"by\": \"words\", \"max\": 50}";
            OracleDocumentSplitter splitter = new OracleDocumentSplitter(conn, pref);

            String[] chunks = splitter.split(content);
            assertThat(chunks.length).isGreaterThan(1);
        }
    }

    @Test
    @DisplayName("split doc input by chars")
    void testDocByChars() throws IOException, SQLException {
        try (Connection conn = getConnection()) {
            String pref = "{\"by\": \"chars\", \"max\": 50}";
            OracleDocumentSplitter splitter = new OracleDocumentSplitter(conn, pref);

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
    }
}
