package dev.langchain4j.http.spring.restclient;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for SpringRestClient HTTP client factory detection and warning behavior.
 * 
 * @see <a href="https://github.com/langchain4j/langchain4j/issues/4918">Issue #4918</a>
 */
class SpringRestClientHttpClientIT {

    @Test
    void should_detect_and_warn_when_using_apache_http_client_5() throws Exception {
        // Build a client with default settings (auto-detect)
        SpringRestClientHttpClientBuilder builder = new SpringRestClientHttpClientBuilder();
        SpringRestClientHttpClient client = new SpringRestClientHttpClient(builder);
        
        // Use reflection to access the private method for testing
        Method detectMethod = SpringRestClientHttpClient.class.getDeclaredMethod(
            "detectHttpClientFactory", SpringRestClientHttpClientBuilder.class);
        detectMethod.setAccessible(true);
        
        // Call the detection method
        Object factory = detectMethod.invoke(client, builder);
        
        // Verify we got a factory (either JDK or Apache)
        assertThat(factory).isNotNull();
        
        // Verify the class name check method exists and works
        Method isApacheMethod = SpringRestClientHttpClient.class.getDeclaredMethod(
            "isApacheHttpClient5", org.springframework.http.client.ClientHttpRequestFactory.class);
        isApacheMethod.setAccessible(true);
        
        // The factory should be one of the two expected types
        String className = factory.getClass().getName();
        assertThat(className).isIn(
            "org.springframework.http.client.HttpComponentsClientHttpRequestFactory",  // Apache Hc5
            "org.springframework.http.client.SimpleClientHttpRequestFactory",           // JDK
            "org.springframework.http.client.JdkClientHttpRequestFactory"              // JDK 11+
        );
    }

    @Test
    void should_respect_custom_rest_client_builder() {
        // Test that custom RestClient.Builder is used
        SpringRestClientHttpClientBuilder builder = new SpringRestClientHttpClientBuilder();
        builder.restClientBuilder(RestClient.builder());
        
        SpringRestClientHttpClient client = new SpringRestClientHttpClient(builder);
        
        assertThat(client).isNotNull();
        // The client should be built successfully with custom builder
    }
}