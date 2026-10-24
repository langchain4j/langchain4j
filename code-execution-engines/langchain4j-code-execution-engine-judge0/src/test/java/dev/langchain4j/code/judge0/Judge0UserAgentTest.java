package dev.langchain4j.code.judge0;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.http.client.HttpRequest;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class Judge0UserAgentTest {

    @Test
    void should_set_default_user_agent() {
        Judge0JavaScriptEngine engine = new Judge0JavaScriptEngine("test-api-key", 102, Duration.ofSeconds(10));

        HttpRequest request = engine.buildRequest("{}");

        assertThat(request.headers().get("User-Agent")).containsExactly("LangChain4j");
    }
}
