package dev.langchain4j.model.oracle;

import static java.util.stream.Collectors.toList;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.document.splitter.oracle.Chunk;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.DimensionAwareEmbeddingModel;
import dev.langchain4j.model.output.Response;
import java.sql.Array;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import oracle.jdbc.OracleConnection;

/**
 * Embed documents
 *
 * Use dbms_vector_chain.utl_to_embeddings to get embeddings.
 * You can specify which provider to use such as an ONNX model
 * or a third-party provider via a REST call.
 *
 * Some example preferences
 *
 * To use an ONNX model:
 * {
 *   "provider": "database",
 *   "model": "database"
 * }
 * To use a third-party provider:
 * {
 *   "provider": "ocigenai",
 *   "credential_name": "OCI_CRED",
 *   "url": "https://inference.generativeai.us-chicago-1.oci.oraclecloud.com/20231130/actions/embedText",
 *   "model": "cohere.embed-english-light-v3.0"
 * }
 */
public class OracleEmbeddingModel extends DimensionAwareEmbeddingModel {

    private final Connection conn;
    private final String pref;
    private final String proxy;
    private boolean batching = true;

    /**
     * Create an embedding model
     */
    public OracleEmbeddingModel(Connection conn, String pref) {
        this.conn = conn;
        this.pref = pref;
        this.proxy = "";
    }

    /**
     * Create an embedding model with a proxy
     */
    public OracleEmbeddingModel(Connection conn, String pref, String proxy) {
        this.conn = conn;
        this.pref = pref;
        this.proxy = proxy;
    }

    public void setBatching(boolean batching) {
        this.batching = batching;
    }

    public boolean getBatching() {
        return this.batching;
    }

    /**
     * load an ONNX model located on the server into the database
     *
     * @param conn      connection
     * @param dir       directory alias from create directory
     * @param onnxFile  ONNX file
     * @param modelName model name
     */
    public static boolean loadOnnxModel(Connection conn, String dir, String onnxFile, String modelName)
            throws SQLException {
        boolean result = false;

        String query = "begin\n"
                + "  dbms_data_mining.drop_model(?, force => true);\n"
                + "  dbms_vector.load_onnx_model(?, ?, ?,\n"
                + "  json('{\"function\" : \"embedding\", \"embeddingOutput\" : \"embedding\" , \"input\": {\"input\": [\"DATA\"]}}'));\n"
                + "end;";
        PreparedStatement stmt = conn.prepareStatement(query);
        stmt.setObject(1, modelName);
        stmt.setObject(2, dir);
        stmt.setObject(3, onnxFile);
        stmt.setObject(4, modelName);
        stmt.execute();
        result = true;

        return result;
    }

    /**
     * get embeddings for a list of text segments
     */
    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        List<String> texts = textSegments.stream().map(TextSegment::text).collect(toList());

        try {
            return embedTexts(texts);
        } catch (SQLException | JsonProcessingException ex) {
            throw new RuntimeException("cannot get embedding", ex);
        }
    }

    /**
     * get embeddings for a list of strings
     */
    private Response<List<Embedding>> embedTexts(List<String> inputs) throws SQLException, JsonProcessingException {
        List<Embedding> embeddings = new ArrayList<>();

        if (proxy != null && !proxy.isEmpty()) {
            String query = "begin utl_http.set_proxy(?); end;";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setObject(1, proxy);
                stmt.execute();
            }
        }

        if (!batching) {
            for (String input : inputs) {
                embed(input, pref, embeddings);
            }
        } else {
            // createOracleArray needs to passed a Clob array since vector_array_t is a table of clob
            // if a String array is passed, will get ORA-17059: Failed to convert to internal representation
            List<Object> elements = toClobList(conn, inputs);
            Array arr = ((OracleConnection) conn).createOracleArray("SYS.VECTOR_ARRAY_T", elements.toArray());
            embed(arr, pref, embeddings);
        }

        return Response.from(embeddings);
    }

    /**
     * embed either a string or array
     */
    private void embed(Object obj, String pref, List<Embedding> embeddings)
            throws SQLException, JsonProcessingException {
        String query = "select t.column_value as data from dbms_vector_chain.utl_to_embeddings(?, json(?)) t";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setObject(1, obj);
            stmt.setObject(2, pref);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String text = rs.getString("data");

                    ObjectMapper mapper = new ObjectMapper();
                    dev.langchain4j.model.oracle.Embedding dbmsEmbedding =
                            mapper.readValue(text, dev.langchain4j.model.oracle.Embedding.class);
                    Embedding embedding = new Embedding(toFloatArray(dbmsEmbedding.getVector()));
                    embeddings.add(embedding);
                }
            }
        }
    }

    private List<Object> toClobList(Connection conn, List<String> inputs) throws JsonProcessingException, SQLException {
        ObjectMapper objectMapper = new ObjectMapper();

        List<Object> chunks = new ArrayList<>();
        for (int i = 0; i < inputs.size(); i++) {
            // Create JSON string
            Chunk chunk = new Chunk();
            chunk.setId(i);
            chunk.setData(inputs.get(i));
            String jsonString = objectMapper.writeValueAsString(chunk);

            Clob clob = conn.createClob();
            clob.setString(1, jsonString);
            chunks.add(clob);
        }
        return chunks;
    }

    private float[] toFloatArray(String embedding) {
        String str = embedding.replace("[", "").replace("]", "");
        String[] strArr = str.split(",");
        float[] floatArr = new float[strArr.length];
        for (int i = 0; i < strArr.length; i++) {
            floatArr[i] = Float.parseFloat(strArr[i]);
        }
        return floatArr;
    }
}
