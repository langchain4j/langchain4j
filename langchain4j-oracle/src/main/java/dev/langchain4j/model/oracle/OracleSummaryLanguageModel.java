package dev.langchain4j.model.oracle;

import dev.langchain4j.model.language.LanguageModel;
import dev.langchain4j.model.output.Response;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Summarize documents
 *
 * Use dbms_vector_chain.utl_to_summary to summarize documents.
 * You can specify which provider to use such as database
 * for Oracle Text or a third-party provider via a REST call.
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
 *   "credential_name" : "OCI_CRED",
 *   "url": "https://inference.generativeai.us-chicago-1.oci.oraclecloud.com/20231130/actions/chat",
 *   "model" : "cohere.command-r-16k",
 *   "chatRequest": {
 *     "maxTokens": 256
 *   }
 * }
 */
public class OracleSummaryLanguageModel implements LanguageModel {

    private final Connection conn;
    private final String pref;
    private final String proxy;

    /**
     * Create a summary language model
     */
    public OracleSummaryLanguageModel(Connection conn, String pref) {
        this.conn = conn;
        this.pref = pref;
        this.proxy = "";
    }

    /**
     * Create a summary language model with a proxy
     */
    public OracleSummaryLanguageModel(Connection conn, String pref, String proxy) {
        this.conn = conn;
        this.pref = pref;
        this.proxy = proxy;
    }

    /**
     * generate summary
     *
     * @param input    text to summarize
     */
    @Override
    public Response<String> generate(String input) {

        String text = "";

        try {
            if (proxy != null && !proxy.isEmpty()) {
                String query = "begin utl_http.set_proxy(?); end;";
                try (PreparedStatement stmt = conn.prepareStatement(query)) {
                    stmt.setObject(1, proxy);
                    stmt.execute();
                }
            }

            String query = "select dbms_vector_chain.utl_to_summary(?, json(?)) data from dual";

            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                Clob clob = conn.createClob();
                clob.setString(1, input);

                stmt.setObject(1, clob);
                stmt.setObject(2, pref);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        text = rs.getString("data");
                    }
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("cannot get summary", ex);
        }

        return Response.from(text);
    }
}
