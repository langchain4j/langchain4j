package dev.langchain4j.http.client.apache;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientIT;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class ApacheHttpClientIT extends HttpClientIT {

    @Override
    protected List<HttpClient> clients() {
        return List.of(
                ApacheHttpClient.builder().build()
        );
    }
}
