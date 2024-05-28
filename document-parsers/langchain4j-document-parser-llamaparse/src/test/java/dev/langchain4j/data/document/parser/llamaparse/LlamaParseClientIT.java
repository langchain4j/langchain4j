package dev.langchain4j.data.document.parser.llamaparse;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.File;
import java.nio.file.Paths;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
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
        String parsingInstructions = "The provided document is a PDF sample that contain Lorem Ipsum text.";

        LlamaParseResponse responseBody = client.upload(file, parsingInstructions);
        JOB_ID =  responseBody.id;
        String status = responseBody.status;

        assertThat(JOB_ID).isNotBlank();
        assertThat(status).isEqualTo(LlmaParseApi.JOB_STATUS.PENDING.value());
    }

    @Test
    @Order(2)
    void shouldGetJobStatus() throws InterruptedException {

        LlamaParseResponse responseBody = client.jobStatus(JOB_ID);
        final int MAX_ATTEMPTS = 10;
        int attempts = 0;
        Duration interval = Duration.ofSeconds(5);
        while (responseBody.status.equalsIgnoreCase(LlmaParseApi.JOB_STATUS.SUCCESS.value()) && attempts++ < MAX_ATTEMPTS) {
            responseBody = client.jobStatus(JOB_ID);
            wait(interval.toMillis());
        }

        assertThat(responseBody).isNotNull();
        assertThat(responseBody.status).isEqualTo(LlmaParseApi.JOB_STATUS.SUCCESS.value());

    }

    @Test
    @Order(3)
    void shouldGetMarkdown()  {

        LlamaParseMarkdownResponse response = client.markdownResult(JOB_ID);
        String markdown = response.markdown;
        assertThat(markdown.length()).isGreaterThan(0);
    }

}
