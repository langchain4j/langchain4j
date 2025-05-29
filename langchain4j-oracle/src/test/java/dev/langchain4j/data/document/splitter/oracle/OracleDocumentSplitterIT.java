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

    String content = "Equipment rental in North America is predicted to “normalize” going into 2024,\n" + "\n"
            + "according to Josh Nickell, vice president of equipment rental for the American Rental\n"
            + "\n"
            + "Association (ARA).\n"
            + "\n"
            + "“Rental is going back to ‘normal,’ but normal means that strategy matters again -\n"
            + "\n"
            + "geography matters, fleet mix matters, customer type matters,” Nickell said. “In\n"
            + "\n"
            + "late 2020 to 2022, you just showed up with equipment and you made money.\n"
            + "\n"
            + "“Everybody was breaking records, from the national rental chains to the smallest\n"
            + "\n"
            + "rental companies; everybody was having record years, and everybody was raising\n"
            + "\n"
            + "prices. The conversation was, ‘How much are you up?’ And now, the conversation\n"
            + "\n"
            + "is changing to ‘What’s my market like?’”\n"
            + "\n"
            + "Nickell stressed this shouldn’t be taken as a pessimistic viewpoint. It’s simply\n"
            + "\n"
            + "coming back down to Earth from unprecedented circumstances during the time of Covid.\n"
            + "\n"
            + "Rental companies are still seeing growth, but at a more moderate level.";

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
