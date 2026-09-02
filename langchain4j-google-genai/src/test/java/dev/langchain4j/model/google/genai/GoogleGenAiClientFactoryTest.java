package dev.langchain4j.model.google.genai;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.genai.Client;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class GoogleGenAiClientFactoryTest {

    @Test
    void should_create_client_with_api_key() {
        Client client = GoogleGenAiClientFactory.createClient("test-api-key", null, null, null, null, null, null);

        assertThat(client).isNotNull();
    }

    @Test
    void should_create_client_with_timeout() {
        Client client = GoogleGenAiClientFactory.createClient(
                "test-api-key", null, null, null, Duration.ofSeconds(30), null, null);

        assertThat(client).isNotNull();
    }

    @Test
    void should_create_client_without_timeout() {
        Client client = GoogleGenAiClientFactory.createClient("test-api-key", null, null, null, null, null, null);

        assertThat(client).isNotNull();
    }

    @Test
    void should_create_client_with_vertex_project_and_location() {
        Client client =
                GoogleGenAiClientFactory.createClient(null, null, "test-project-id", "us-central1", null, null, null);

        assertThat(client).isNotNull();
        assertThat(client.vertexAI()).isTrue();
        assertThat(client.project()).isEqualTo("test-project-id");
        assertThat(client.location()).isEqualTo("us-central1");
    }

    @Test
    void should_create_client_with_vertex_and_api_key() {
        Client client = GoogleGenAiClientFactory.createClient(
                "test-api-key", null, "test-project-id", "us-central1", null, null, null);

        assertThat(client).isNotNull();
        assertThat(client.vertexAI()).isTrue();
        assertThat(client.project()).isEqualTo("test-project-id");
        assertThat(client.location()).isEqualTo("us-central1");
        assertThat(client.apiKey()).isEqualTo("test-api-key");
    }
}
