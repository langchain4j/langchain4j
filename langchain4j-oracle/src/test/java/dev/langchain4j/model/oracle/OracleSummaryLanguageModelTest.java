package dev.langchain4j.model.oracle;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.output.Response;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import oracle.ucp.jdbc.PoolDataSource;
import oracle.ucp.jdbc.PoolDataSourceFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class OracleSummaryLanguageModelTest {

    Connection conn;

    @BeforeEach
    void setUp() throws SQLException {
        PoolDataSource pds = PoolDataSourceFactory.getPoolDataSource();
        pds.setConnectionFactoryClassName("oracle.jdbc.pool.OracleDataSource");
        pds.setURL(System.getenv("ORACLE_JDBC_URL"));
        pds.setUser(System.getenv("ORACLE_JDBC_USER"));
        pds.setPassword(System.getenv("ORACLE_JDBC_PASSWORD"));
        conn = pds.getConnection();
    }

    @Test
    @DisplayName("summary with provider=database")
    void testSummaryDatabase() throws IOException {
        String pref = "{\"provider\": \"database\", \"gLevel\": \"S\"}";

        OracleSummaryLanguageModel model = new OracleSummaryLanguageModel(conn, pref);

        String filename = System.getenv("DEMO_DS_TEXT_FILE");
        String content = readFile(filename, Charset.forName("UTF-8"));
        Response<String> resp = model.generate(content);
        assertThat(resp.content().length()).isGreaterThan(0);
    }

    @Test
    @DisplayName("summary with provider=OCIGenAI")
    void testSummaryOcigenai() throws IOException {
        String pref = "{\n"
                + "  \"provider\": \"ocigenai\",\n"
                + "  \"credential_name\": \"OCI_CRED\",\n"
                + "  \"url\": \"https://inference.generativeai.us-chicago-1.oci.oraclecloud.com/20231130/actions/chat\",\n"
                + "  \"model\": \"cohere.command-r-08-2024\",\n"
                + "}";
        String proxy = System.getenv("DEMO_PROXY");

        OracleSummaryLanguageModel model = new OracleSummaryLanguageModel(conn, pref, proxy);

        String filename = System.getenv("DEMO_DS_TEXT_FILE");
        String content = readFile(filename, Charset.forName("UTF-8"));
        Response<String> resp = model.generate(content);
        assertThat(resp.content().length()).isGreaterThan(0);
    }

    @AfterEach
    void tearDown() throws SQLException {
        conn.close();
    }

    static String readFile(String path, Charset encoding) throws IOException {
        byte[] bytes = Files.readAllBytes(Paths.get(path));
        return new String(bytes, encoding);
    }
}
