package dev.langchain4j.http.spring.restclient;

import dev.langchain4j.http.HttpClientBuilder;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.web.client.RestClient;

import java.time.Duration;

public class SpringRestClientHttpClientBuilder implements HttpClientBuilder {

    private RestClient.Builder restClientBuilder;
    private AsyncTaskExecutor streamingRequestExecutor;
    private Boolean createDefaultStreamingRequestExecutor = true;
    private Duration connectTimeout;
    private Duration readTimeout;

    public RestClient.Builder restClientBuilder() {
        return restClientBuilder;
    }

    public SpringRestClientHttpClientBuilder restClientBuilder(RestClient.Builder restClientBuilder) {
        this.restClientBuilder = restClientBuilder;
        return this;
    }

    /**
     * TODO
     *
     * @return
     */
    public AsyncTaskExecutor streamingRequestExecutor() {
        return streamingRequestExecutor;
    }

    /**
     * TODO
     *
     * @param streamingRequestExecutor
     * @return
     */
    public SpringRestClientHttpClientBuilder streamingRequestExecutor(AsyncTaskExecutor streamingRequestExecutor) {
        this.streamingRequestExecutor = streamingRequestExecutor;
        return this;
    }

    /**
     * TODO
     *
     * @return
     */
    public Boolean createDefaultStreamingRequestExecutor() {
        return createDefaultStreamingRequestExecutor;
    }

    /**
     * TODO
     *
     * @param createDefaultStreamingRequestExecutor
     * @return
     */
    public SpringRestClientHttpClientBuilder createDefaultStreamingRequestExecutor(Boolean createDefaultStreamingRequestExecutor) {
        this.createDefaultStreamingRequestExecutor = createDefaultStreamingRequestExecutor;
        return this;
    }

    @Override
    public Duration connectTimeout() {
        return connectTimeout;
    }

    @Override
    public SpringRestClientHttpClientBuilder connectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
        return this;
    }

    @Override
    public Duration readTimeout() {
        return readTimeout;
    }

    @Override
    public SpringRestClientHttpClientBuilder readTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
        return this;
    }

    @Override
    public SpringRestClientHttpClient build() {
        return new SpringRestClientHttpClient(this);
    }
}
