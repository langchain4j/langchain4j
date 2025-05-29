package dev.langchain4j.model.oracle;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.document.loader.OracleContainerTestBase;
import dev.langchain4j.model.output.Response;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class OracleSummaryLanguageModelIT extends OracleContainerTestBase {

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
    @DisplayName("summary with provider=database")
    void testSummaryDatabase() throws IOException, SQLException {
        try (Connection conn = getConnection()) {
            String pref = "{\"provider\": \"database\", \"gLevel\": \"S\"}";

            OracleSummaryLanguageModel model = new OracleSummaryLanguageModel(conn, pref);
            Response<String> resp = model.generate(content);
            assertThat(resp.content().length()).isGreaterThan(0);
        }
    }

    @Test
    @DisplayName("summary with provider=OCIGenAI")
    void testSummaryOcigenai() throws IOException, SQLException {
        if (isContainerRunning()) return;

        try (Connection conn = getConnection()) {
            String pref = "{\n"
                    + "  \"provider\": \"ocigenai\",\n"
                    + "  \"credential_name\": \"OCI_CRED\",\n"
                    + "  \"url\": \"https://inference.generativeai.us-chicago-1.oci.oraclecloud.com/20231130/actions/chat\",\n"
                    + "  \"model\": \"cohere.command-r-08-2024\",\n"
                    + "}";
            String proxy = System.getenv("DEMO_PROXY");

            OracleSummaryLanguageModel model = new OracleSummaryLanguageModel(conn, pref, proxy);
            Response<String> resp = model.generate(content);
            assertThat(resp.content().length()).isGreaterThan(0);
        }
    }
}
