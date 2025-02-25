package dev.langchain4j.data.document.splitter.oracle;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OracleDocumentSplitter implements DocumentSplitter {

    private static final Logger log = LoggerFactory.getLogger(OracleDocumentSplitter.class);

    private static final String INDEX = "index";

    private final Connection conn;
    private final String pref;

    public OracleDocumentSplitter(Connection conn, String pref) {
        this.conn = conn;
        this.pref = pref;
    }

    @Override
    public List<TextSegment> split(Document document) {
        List<TextSegment> segments = new ArrayList<>();
        try {
            String[] parts = split(document.text());
            int index = 0;
            for (String part : parts) {
                segments.add(createSegment(part, document, index));
                index++;
            }
        } catch (SQLException | JsonProcessingException e) {
            String message = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            log.warn("Failed to summarize '{}': {}", pref, message);
        }
        return segments;
    }

    @Override
    public List<TextSegment> splitAll(List<Document> list) {
        return DocumentSplitter.super.splitAll(list);
    }

    /**
     * Splits the provided text into parts. Implementation API.
     *
     * @param content The text to be split.
     * @return An array of parts.
     */
    public String[] split(String content) throws SQLException, JsonProcessingException {

        List<String> strArr = new ArrayList<>();

        String query = "select t.column_value as data from dbms_vector_chain.utl_to_chunks(?, json(?)) t";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setObject(1, content);
            stmt.setObject(2, pref);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String text = rs.getString("data");

                    ObjectMapper mapper = new ObjectMapper();
                    Chunk chunk = mapper.readValue(text, Chunk.class);
                    strArr.add(chunk.chunk_data);
                }
            }
        }

        return strArr.toArray(new String[strArr.size()]);
    }

    /**
     * Creates a new {@link TextSegment} from the provided text and document.
     *
     * <p>
     * The segment inherits all metadata from the document. The segment also
     * includes an "index" metadata key representing the segment position within
     * the document.
     *
     * @param text The text of the segment.
     * @param document The document to which the segment belongs.
     * @param index The index of the segment within the document.
     */
    static TextSegment createSegment(String text, Document document, int index) {
        Metadata metadata = document.metadata().copy().put(INDEX, String.valueOf(index));
        return TextSegment.from(text, metadata);
    }
}
