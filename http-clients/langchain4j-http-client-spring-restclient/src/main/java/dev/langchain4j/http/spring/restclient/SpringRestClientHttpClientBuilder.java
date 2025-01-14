package dev.langchain4j.http.spring.restclient;

import dev.langchain4j.http.HttpClientBuilder;
import org.springframework.core.task.TaskExecutor;
import org.springframework.web.client.RestClient;

import java.time.Duration;

public class SpringRestClientHttpClientBuilder implements HttpClientBuilder {

    private RestClient.Builder restClientBuilder;
    private TaskExecutor taskExecutor;
    private boolean createDefaultTaskExecutor = true; // TODO better name allowCreatingDefaultTaskExecutor?
    private Duration connectTimeout;
    private Duration readTimeout;
    private boolean logRequests;
    private boolean logResponses;

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
    public TaskExecutor taskExecutor() {
        return taskExecutor;
    }

    /**
     * TODO
     *
     * @param taskExecutor
     * @return
     */
    public SpringRestClientHttpClientBuilder taskExecutor(TaskExecutor taskExecutor) {
        this.taskExecutor = taskExecutor;
        return this;
    }

    public boolean createDefaultTaskExecutor() {
        return createDefaultTaskExecutor;
    }

    /**
     * TODO
     *
     * @param createDefaultTaskExecutor
     * @return
     */
    public SpringRestClientHttpClientBuilder createDefaultTaskExecutor(boolean createDefaultTaskExecutor) {
        this.createDefaultTaskExecutor = createDefaultTaskExecutor;
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
    public boolean logRequests() {
        return logRequests;
    }

    @Override
    public SpringRestClientHttpClientBuilder logRequests(boolean logRequests) {
        this.logRequests = logRequests;
        return this;
    }

    @Override
    public boolean logResponses() {
        return logResponses;
    }

    @Override
    public SpringRestClientHttpClientBuilder logResponses(boolean logResponses) {
        this.logResponses = logResponses;
        return this;
    }

    @Override
    public SpringRestClientHttpClient build() {
        return new SpringRestClientHttpClient(this);
    }
}
