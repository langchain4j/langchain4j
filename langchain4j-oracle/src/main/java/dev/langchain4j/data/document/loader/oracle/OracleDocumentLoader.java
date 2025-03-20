package dev.langchain4j.data.document.loader.oracle;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

public class OracleDocumentLoader {

    private final Connection conn;

    public OracleDocumentLoader(Connection conn) {
        this.conn = conn;
    }

    public List<Document> loadDocuments(String pref) throws JsonProcessingException, IOException, SQLException {
        List<Document> documents = new ArrayList<>();

        ObjectMapper mapper = new ObjectMapper();
        LoaderPreference loaderPref = mapper.readValue(pref, LoaderPreference.class);

        if (loaderPref.getFile() != null && !loaderPref.getFile().equals("null")) {
            String filename = loaderPref.getFile();
            Document doc = loadDocument(filename, pref);
            if (doc != null) {
                documents.add(doc);
            }
        } else if (loaderPref.getDir() != null && !loaderPref.getDir().equals("null")) {
            String dir = loaderPref.getDir();
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
        } else if (loaderPref.getColumnName() != null
                && !loaderPref.getColumnName().equals("null")) {
            String column = loaderPref.getColumnName();

            String table = loaderPref.getTableName();
            String owner = loaderPref.getOwner();
            if (table == null || table.equals("null")) {
                throw new InvalidParameterException("Missing table in preference");
            }
            if (owner == null || owner.equals("null")) {
                throw new InvalidParameterException("Missing owner in preference");
            }

            documents.addAll(loadDocuments(owner, table, column, pref));
        } else {
            throw new InvalidParameterException("Missing file, dir, or table in preference");
        }

        return documents;
    }

    private Document loadDocument(String filename, String pref) throws IOException, SQLException {
        Document document = null;

        byte[] bytes = Files.readAllBytes(Paths.get(filename));

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
                    String text = rs.getString("text");
                    String html = rs.getString("metadata");

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

    private List<Document> loadDocuments(String owner, String table, String column, String pref) throws SQLException {
        List<Document> documents = new ArrayList<>();

        // run utl_to_text twice to get both the plain text and HTML output
        // the HTML Output is needed for the metadata
        String query = String.format(
                "select dbms_vector_chain.utl_to_text(t.%s, json(?)) text, "
                        + "dbms_vector_chain.utl_to_text(t.%s, json('{\"plaintext\": \"false\"}')) metadata "
                        + "from %s.%s t",
                column, column, owner, table);
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setObject(1, pref);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String text = rs.getString("text");
                    String html = rs.getString("metadata");

                    Metadata metadata = getMetadata(html);
                    Document doc = Document.from(text, metadata);
                    documents.add(doc);
                }
            }
        }

        return documents;
    }

    private static Metadata getMetadata(String html) {
        Metadata metadata = new Metadata();

        org.jsoup.nodes.Document doc = Jsoup.parse(html);
        Elements metaTags = doc.getElementsByTag("meta");
        for (Element metaTag : metaTags) {
            String name = metaTag.attr("name");
            if (name.isEmpty()) {
                continue;
            }
            String content = metaTag.attr("content");
            metadata.put(name, content);
        }

        return metadata;
    }
}
