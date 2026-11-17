package dev.langchain4j.agentic.a2a;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class A2AAgentCardExceptionTest {

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "https://user:secret@remote-host/agents?token=secret#part | https://remote-host/agents",
                "http://user:secret@remote-host:8080/my agents?token=secret | http://remote-host:8080/my agents",
                "https://user:secret#part@remote-host/agents | https://remote-host/agents",
                "user:secret@remote-host:8080 | remote-host:8080",
                "::not-a-url::?token=secret | ::not-a-url::",
            })
    void message_should_show_url_without_credentials(String url, String urlInMessage) {
        A2AAgentCardException exception = new A2AAgentCardException("fixer", url, new RuntimeException());

        assertThat(exception).hasMessageContaining(urlInMessage).hasMessageNotContaining("secret");
        assertThat(exception.a2aServerUrl()).isEqualTo(url);
    }
}
