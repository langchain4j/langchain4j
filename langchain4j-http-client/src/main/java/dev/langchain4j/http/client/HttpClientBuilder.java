package dev.langchain4j.http.client;

import java.time.Duration;

public interface HttpClientBuilder {

    Duration connectTimeout();

    HttpClientBuilder connectTimeout(Duration timeout);

    Duration readTimeout();

    /**
     * Sets the read timeout of the HTTP client.
     *
     * @param timeout the read timeout. {@link Duration#ZERO} means no read timeout.
     *                {@code null} means the default of the HTTP client, which is not always "no read timeout".
     * @return this builder
     */
    HttpClientBuilder readTimeout(Duration timeout);

    HttpClient build();
}
