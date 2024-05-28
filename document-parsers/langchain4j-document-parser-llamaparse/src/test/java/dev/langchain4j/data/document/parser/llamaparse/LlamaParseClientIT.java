package dev.langchain4j.data.document.parser.llamaparse;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.File;
import java.nio.file.Paths;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.with;
import static org.hamcrest.Matchers.equalTo;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@EnabledIfEnvironmentVariable(named = "LLAMA_PARSE_API_KEY", matches = ".+")
public class LlamaParseClientIT {

    private static String API_KEY;
    private static LlamaParseClient client;
    private static String JOB_ID;

    @BeforeAll
    static void prepareTest() {
        API_KEY = System.getenv("LLAMA_PARSE_API_KEY");
        client = LlamaParseClient.builder()
                .apiKey(API_KEY)
                .baseUrl(LlmaParseApi.baseUrl)
                .timeout(Duration.ofSeconds(5))
                .build();
    }

    @Test
    @Order(1)
    void shouldParseFile() {
        File file = Paths.get(this.getClass().getResource("/files/sample.pdf").getPath()).toFile();
        String parsingInstructions = "The provided document is a PDF sample containing Lorem Ipsum text.";

        LlamaParseResponse responseBody = client.upload(file, parsingInstructions);
        JOB_ID =  responseBody.id;
        String status = responseBody.status;

        assertThat(JOB_ID).isNotBlank();
        assertThat(status).isEqualTo(LlmaParseApi.JOB_STATUS.PENDING.value());
    }

    @Test
    @Order(2)
    void shouldGetJobStatus() throws InterruptedException {
        with()
                .pollInterval(Duration.ofSeconds(10))
                .await("check status not pending")
                .atMost(Duration.ofSeconds(60))
                .untilAsserted(() -> assertThat(client.jobStatus(JOB_ID).status)
                        .isEqualTo(LlmaParseApi.JOB_STATUS.SUCCESS.value()));
    }

    @Test
    @Order(3)
    void shouldGetMarkdown()  {

        LlamaParseMarkdownResponse response = client.markdownResult(JOB_ID);
        String markdown = response.markdown;
        assertThat(markdown.length()).isGreaterThan(0);
    }

}
