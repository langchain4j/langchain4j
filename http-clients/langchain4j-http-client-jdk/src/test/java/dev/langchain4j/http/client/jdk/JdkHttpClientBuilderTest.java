package dev.langchain4j.http.client.jdk;

import static dev.langchain4j.http.client.HttpMethod.GET;
import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.langchain4j.http.client.HttpRequest;
import java.net.http.HttpClient;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class JdkHttpClientBuilderTest {

    @Test
    void should_use_supplied_client_and_ignore_client_builder_and_connect_timeout() {
        HttpClient suppliedClient = mock(HttpClient.class);
        HttpClient.Builder ignoredBuilder = mock(HttpClient.Builder.class);

        JdkHttpClient client = JdkHttpClient.builder()
                .httpClientBuilder(ignoredBuilder)
                .connectTimeout(ofSeconds(42))
                .httpClient(suppliedClient)
                .build();

        assertThat(client).extracting("delegate").isSameAs(suppliedClient);
        verifyNoInteractions(ignoredBuilder);
    }

    @Test
    void should_treat_null_client_as_not_configured_and_preserve_existing_builder_behavior() {
        HttpClient builtClient = mock(HttpClient.class);
        HttpClient.Builder clientBuilder = mock(HttpClient.Builder.class);
        Duration connectTimeout = ofSeconds(3);
        when(clientBuilder.connectTimeout(connectTimeout)).thenReturn(clientBuilder);
        when(clientBuilder.build()).thenReturn(builtClient);

        JdkHttpClient client = JdkHttpClient.builder()
                .httpClient(null)
                .httpClientBuilder(clientBuilder)
                .connectTimeout(connectTimeout)
                .build();

        assertThat(client).extracting("delegate").isSameAs(builtClient);
        verify(clientBuilder).connectTimeout(connectTimeout);
        verify(clientBuilder).build();
    }

    @Test
    void wrappers_sharing_a_client_should_keep_read_timeout_and_streaming_buffer_size_independent() {
        HttpClient sharedClient = mock(HttpClient.class);
        Duration firstReadTimeout = ofSeconds(1);
        Duration secondReadTimeout = ofSeconds(2);

        JdkHttpClient first = JdkHttpClient.builder()
                .httpClient(sharedClient)
                .readTimeout(firstReadTimeout)
                .streamingBufferSize(1)
                .build();
        JdkHttpClient second = JdkHttpClient.builder()
                .httpClient(sharedClient)
                .readTimeout(secondReadTimeout)
                .streamingBufferSize(8)
                .build();

        assertThat(first).extracting("delegate").isSameAs(sharedClient);
        assertThat(second).extracting("delegate").isSameAs(sharedClient);
        assertThat(first).extracting("streamingBufferSize").isEqualTo(1);
        assertThat(second).extracting("streamingBufferSize").isEqualTo(8);

        HttpRequest request =
                HttpRequest.builder().method(GET).url("http://localhost/test").build();
        assertThat(first.toJdkRequest(request).timeout()).contains(firstReadTimeout);
        assertThat(second.toJdkRequest(request).timeout()).contains(secondReadTimeout);
    }

    @Test
    void should_still_validate_wrapper_configuration_when_client_is_supplied() {
        assertThatThrownBy(() -> JdkHttpClient.builder()
                        .httpClient(mock(HttpClient.class))
                        .streamingBufferSize(0)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("streamingBufferSize");
    }
}
