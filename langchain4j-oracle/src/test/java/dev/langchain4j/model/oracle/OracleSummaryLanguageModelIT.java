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
    @DisplayName("summary with provider=database")
    void testSummaryDatabase() throws IOException, SQLException {
        try (Connection conn = getConnection()) {
            String pref = "{\"provider\": \"database\", \"gLevel\": \"S\"}";

            OracleSummaryLanguageModel model = new OracleSummaryLanguageModel(conn, pref);
            Response<String> resp = model.generate(content);
            assertThat(resp.content().length()).isGreaterThan(0);
        }
    }

    /*
    // disable for now
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
    */
}
