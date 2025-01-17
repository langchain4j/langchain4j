package dev.langchain4j.http.spring.restclient;

import dev.langchain4j.http.HttpClientBuilder;
import org.springframework.core.task.TaskExecutor;
import org.springframework.web.client.RestClient;

import java.time.Duration;

public class SpringRestClientHttpClientBuilder implements HttpClientBuilder {

    private RestClient.Builder restClientBuilder;
    private TaskExecutor taskExecutor; // TODO better name: streamingTaskExecutor?
    private Boolean createDefaultTaskExecutor = true; // TODO allowCreatingDefaultStreamingTaskExecutor?
    private Duration connectTimeout;
    private Duration readTimeout;
    private Boolean logRequests;
    private Boolean logResponses;

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

    public Boolean createDefaultTaskExecutor() {
        return createDefaultTaskExecutor;
    }

    /**
     * TODO
     *
     * @param createDefaultTaskExecutor
     * @return
     */
    public SpringRestClientHttpClientBuilder createDefaultTaskExecutor(Boolean createDefaultTaskExecutor) {
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
    public Boolean logRequests() {
        return logRequests;
    }

    @Override
    public SpringRestClientHttpClientBuilder logRequests(Boolean logRequests) {
        this.logRequests = logRequests;
        return this;
    }

    @Override
    public Boolean logResponses() {
        return logResponses;
    }

    @Override
    public SpringRestClientHttpClientBuilder logResponses(Boolean logResponses) {
        this.logResponses = logResponses;
        return this;
    }

    @Override
    public SpringRestClientHttpClient build() {
        return new SpringRestClientHttpClient(this);
    }
}
