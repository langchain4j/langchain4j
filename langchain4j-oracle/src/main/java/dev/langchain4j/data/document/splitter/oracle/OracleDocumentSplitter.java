package dev.langchain4j.data.document.splitter.oracle;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Split documents
 *
 * Use dbms_vector_chain.utl_to_chunks to split documents.
 * You can specify how to split the content such as by words, characters,
 * or vocabulary to match a tokenizer in the preference.
 *
 * Some example preferences
 *
 * To split by words:
 * {"by": "words", "max": 100}
 * To split by characters:
 * {"by": "characters", "max": 100}
 */
public class OracleDocumentSplitter implements DocumentSplitter {

    private static final String INDEX = "index";

    private final Connection conn;
    private final String pref;

    /**
     * Create a document splitter
     */
    public OracleDocumentSplitter(Connection conn, String pref) {
        this.conn = conn;
        this.pref = pref;
    }

    /**
     * Split a single document
     */
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
        } catch (SQLException | JsonProcessingException ex) {
            throw new RuntimeException("cannot split document", ex);
        }

        return segments;
    }

    /**
     * Split a list of documents
     */
    @Override
    public List<TextSegment> splitAll(List<Document> list) {
        return DocumentSplitter.super.splitAll(list);
    }

    /**
     * Split the provided text into parts
     */
    public String[] split(String content) throws SQLException, JsonProcessingException {

        List<String> strArr = new ArrayList<>();

        String query = "select t.column_value as data from dbms_vector_chain.utl_to_chunks(?, json(?)) t";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            Clob clob = conn.createClob();
            clob.setString(1, content);

            stmt.setObject(1, clob);
            stmt.setObject(2, pref);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String text = rs.getString("data");

                    ObjectMapper mapper = new ObjectMapper();
                    Chunk chunk = mapper.readValue(text, Chunk.class);
                    strArr.add(chunk.getData());
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
