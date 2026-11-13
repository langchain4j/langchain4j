package dev.langchain4j.model.google.genai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.genai.Client;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

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
        GoogleCredentials credentials =
                GoogleCredentials.create(new AccessToken("test-token", new Date(System.currentTimeMillis() + 3600000)));
        Client client = GoogleGenAiClientFactory.createClient(
                null, credentials, "test-project-id", "us-central1", null, null, null);

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

    @ParameterizedTest
    @MethodSource("timeoutsOutOfRange")
    void should_fail_when_timeout_is_out_of_range(Duration timeout) {
        assertThatThrownBy(() ->
                        GoogleGenAiClientFactory.createClient("test-api-key", null, null, null, timeout, null, null))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("timeout must be between 1 millisecond and 24 days, but was " + timeout);
    }

    static Stream<Duration> timeoutsOutOfRange() {
        return Stream.of(
                Duration.ZERO,
                Duration.ofMillis(-1),
                Duration.ofNanos(999_999),
                Duration.ofDays(24).plusNanos(1),
                Duration.ofDays(30),
                ChronoUnit.FOREVER.getDuration());
    }

    @ParameterizedTest
    @MethodSource("timeoutsInRange")
    void should_accept_timeout_in_range(Duration timeout) {
        Client client = GoogleGenAiClientFactory.createClient("test-api-key", null, null, null, timeout, null, null);

        assertThat(client).isNotNull();
    }

    static Stream<Duration> timeoutsInRange() {
        return Stream.of(Duration.ofMillis(1), Duration.ofDays(24));
    }
}
