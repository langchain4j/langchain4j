package dev.langchain4j.data.document.loader.oracle;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidParameterException;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * Load documents
 *
 * Load documents either from the file system or the database. The documents
 * can be in any format supported by the Oracle Text filter including
 * Word, PDF, HTML, and text files.
 *
 * Use the following preferences
 *
 * To specify a file:
 * {"file": "filename"}
 * To specify a directory:
 * {"dir": "directory name"}
 * To specify a table:
 * {"owner": "owner", "tablename": "table name", "colname": "column name"}
 */
public class OracleDocumentLoader {

    private final Connection conn;

    private final String COLUMN_ROWID = "rowid";
    private final String COLUMN_TEXT = "text";
    private final String COLUMN_METADATA = "metadata";
    private static final String META_TAG = "meta";
    private static final String META_NAME_ATTR = "name";
    private static final String META_CONTENT_ATTR = "content";

    /**
     * create a document loader
     */
    public OracleDocumentLoader(Connection conn) {
        this.conn = conn;
    }

    /**
     * load documents
     *
     * @param pref   JSON Preference specifying the file, directory, or table
     */
    public List<Document> loadDocuments(String pref) throws IOException, SQLException {
        List<Document> documents = new ArrayList<>();

        ObjectMapper mapper = new ObjectMapper();
        JsonNode rootNode = mapper.readTree(pref);

        if (rootNode.has("file")) {
            FilePreference filePref;
            try {
                filePref = mapper.readValue(pref, FilePreference.class);
            } catch (UnrecognizedPropertyException ex) {
                throw new InvalidParameterException("Invalid file preference: unknown property specified");
            }
            String filename = filePref.getFilename();
            Document doc = loadDocument(filename, pref);
            if (doc != null) {
                documents.add(doc);
            }
        } else if (rootNode.has("dir")) {
            DirectoryPreference dirPref;
            try {
                dirPref = mapper.readValue(pref, DirectoryPreference.class);
            } catch (UnrecognizedPropertyException ex) {
                throw new InvalidParameterException("Invalid directory preference: unknown property specified");
            }
            String dir = dirPref.getDirectory();
            Path root = Paths.get(dir);
            Files.walk(root).forEach(path -> {
                if (path.toFile().isFile()) {
                    Document doc = null;
                    try {
                        doc = loadDocument(path.toFile().toString(), pref);
                        if (doc != null) {
                            documents.add(doc);
                        }
                    } catch (IOException | SQLException ex) {
                        throw new RuntimeException("cannot load document", ex);
                    }
                }
            });
        } else if (rootNode.has("tablename")) {
            TablePreference tablePref;
            try {
                tablePref = mapper.readValue(pref, TablePreference.class);
            } catch (UnrecognizedPropertyException ex) {
                throw new InvalidParameterException("Invalid table preference: unknown property specified");
            }
            if (!tablePref.isValid()) {
                throw new InvalidParameterException("Invalid table preference: missing owner, table, or column name");
            }
            String owner = tablePref.getOwner();
            String table = tablePref.getTableName();
            String column = tablePref.getColumnName();
            documents.addAll(loadDocuments(owner, table, column, pref));
        } else {
            throw new InvalidParameterException("Invalid preference: missing filename, directory, or table");
        }

        return documents;
    }

    /**
     * load documents from a file
     */
    private Document loadDocument(String filename, String pref) throws IOException, SQLException {
        Document document = null;

        byte[] bytes = Files.readAllBytes(Paths.get(filename));

        // this uses a blob for the input
        // run utl_to_text twice to get both the plain text and HTML output
        // the HTML Output is needed for the metadata
        String query = "select dbms_vector_chain.utl_to_text(?, json(?)) text, "
                + "dbms_vector_chain.utl_to_text(?, json('{\"plaintext\": \"false\"}')) metadata "
                + "from dual";

        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            Blob blob = conn.createBlob();
            blob.setBytes(1, bytes);

            stmt.setBlob(1, blob);
            stmt.setObject(2, pref);
            stmt.setBlob(3, blob);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String text = rs.getString(COLUMN_TEXT);
                    String html = rs.getString(COLUMN_METADATA);

                    Metadata metadata = getMetadata(html);
                    Path path = Paths.get(filename);
                    metadata.put(Document.FILE_NAME, path.getFileName().toString());
                    metadata.put(
                            Document.ABSOLUTE_DIRECTORY_PATH, path.getParent().toString());
                    document = Document.from(text, metadata);
                }
            }
        }

        return document;
    }

    /**
     * load documents from a table
     */
    private List<Document> loadDocuments(String owner, String table, String column, String pref) throws SQLException {
        List<Document> documents = new ArrayList<>();

        // this uses a table for the input
        // run utl_to_text twice to get both the plain text and HTML output
        // the HTML Output is needed for the metadata
        String query = String.format(
                "select rowid, dbms_vector_chain.utl_to_text(t.%s, json(?)) text, "
                        + "dbms_vector_chain.utl_to_text(t.%s, json('{\"plaintext\": \"false\"}')) metadata "
                        + "from %s.%s t",
                column, column, owner, table);
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setObject(1, pref);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String rowid = rs.getString(COLUMN_ROWID);
                    String text = rs.getString(COLUMN_TEXT);
                    String html = rs.getString(COLUMN_METADATA);

                    Metadata metadata = getMetadata(html);
                    metadata.put("table", table);
                    metadata.put("column", column);
                    metadata.put("rowid", rowid);
                    Document doc = Document.from(text, metadata);
                    documents.add(doc);
                }
            }
        }

        return documents;
    }

    /**
     * get metadata if available
     */
    private static Metadata getMetadata(String html) {
        Metadata metadata = new Metadata();

        org.jsoup.nodes.Document doc = Jsoup.parse(html);
        Elements metaTags = doc.getElementsByTag(META_TAG);
        for (Element metaTag : metaTags) {
            String name = metaTag.attr(META_NAME_ATTR);
            if (name.isEmpty()) {
                continue;
            }
            String content = metaTag.attr(META_CONTENT_ATTR);
            metadata.put(name, content);
        }

        return metadata;
    }
}
