package dev.langchain4j.data.document.parser.llamaparse;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.with;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@EnabledIfEnvironmentVariable(named = "LLAMA_PARSE_API_KEY", matches = ".+")
@Slf4j
public class LlamaParseClientIT {

    @Test
    void shouldParseUploadAndGetMarkdown() {
        String API_KEY = System.getenv("LLAMA_PARSE_API_KEY");

        assertThat(API_KEY).isNotNull().isNotBlank();

        LlamaParseClient client = LlamaParseClient.builder()
                .apiKey(API_KEY)
                .build();

        Path path = toPath("files/sample.pdf");
        String parsingInstructions = "The provided document is a PDF sample containing Lorem Ipsum text.";

        log.debug("Uploading the file...");
        LlamaParseResponse responseBody = client.upload(path, parsingInstructions);
        String JOB_ID =  responseBody.id;
        String status = responseBody.status;

        assertThat(JOB_ID).isNotBlank();
        assertThat(status).isEqualTo("SUCCESS");

        log.debug("Waiting for parsing...");
        with()
                .pollInterval(Duration.ofSeconds(3))
                .await("check success status")
                .atMost(Duration.ofSeconds(60))
                .untilAsserted(() -> assertThat(client.jobStatus(JOB_ID).status)
                        .isEqualTo("SUCCESS"));

        log.debug("Getting markdown result...");
        LlamaParseMarkdownResponse response = client.markdownResult(JOB_ID);
        String markdown = response.markdown;
        assertThat(markdown.length()).isGreaterThan(0);

        log.debug("Test completed...");
    }

    private Path toPath(String fileName) {
        try {
            return Paths.get(getClass().getClassLoader().getResource(fileName).toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}
