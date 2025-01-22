package dev.langchain4j.http.client.spring.restclient;

import dev.langchain4j.http.client.HttpClientBuilder;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.web.client.RestClient;

import java.time.Duration;

public class SpringRestClientBuilder implements HttpClientBuilder {

    private RestClient.Builder restClientBuilder;
    private AsyncTaskExecutor streamingRequestExecutor;
    private Boolean createDefaultStreamingRequestExecutor = true;
    private Duration connectTimeout;
    private Duration readTimeout;

    public RestClient.Builder restClientBuilder() {
        return restClientBuilder;
    }

    public SpringRestClientBuilder restClientBuilder(RestClient.Builder restClientBuilder) {
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
    public SpringRestClientBuilder streamingRequestExecutor(AsyncTaskExecutor streamingRequestExecutor) {
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
    public SpringRestClientBuilder createDefaultStreamingRequestExecutor(Boolean createDefaultStreamingRequestExecutor) {
        this.createDefaultStreamingRequestExecutor = createDefaultStreamingRequestExecutor;
        return this;
    }

    @Override
    public Duration connectTimeout() {
        return connectTimeout;
    }

    @Override
    public SpringRestClientBuilder connectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
        return this;
    }

    @Override
    public Duration readTimeout() {
        return readTimeout;
    }

    @Override
    public SpringRestClientBuilder readTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
        return this;
    }

    @Override
    public SpringRestClient build() {
        return new SpringRestClient(this);
    }
}
