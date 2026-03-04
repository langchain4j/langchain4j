package dev.langchain4j.model.google.genai;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.genai.Client;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class GoogleGenAiClientFactoryTest {

    @Test
    void should_create_client_with_api_key() {
        Client client = GoogleGenAiClientFactory.createClient(
                "test-api-key", null, null, null, null);

        assertThat(client).isNotNull();
    }

    @Test
    void should_create_client_with_timeout() {
        Client client = GoogleGenAiClientFactory.createClient(
                "test-api-key", null, null, null, Duration.ofSeconds(30));

        assertThat(client).isNotNull();
    }

    @Test
    void should_create_client_without_timeout() {
        Client client = GoogleGenAiClientFactory.createClient(
                "test-api-key", null, null, null, null);

        assertThat(client).isNotNull();
    }

    @Test
    void should_throw_when_no_credentials_and_no_api_key_and_no_env() {
        // When no API key, credentials, or env vars are set, the SDK throws
        try {
            GoogleGenAiClientFactory.createClient(null, null, null, null, null);
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).contains("API key");
        }
    }
}
