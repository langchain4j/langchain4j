package dev.langchain4j.model.workersai.client;

import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;

import dev.langchain4j.http.client.HttpClientBuilder;

/**
 * Abstract class for WorkerAI models as they are all initialized the same way.
 * <a href="https://developers.cloudflare.com/api/operations/workers-ai-post-run-model">...</a>
 */
public abstract class AbstractWorkersAIModel {

    /**
     * Account identifier, provided by the WorkerAI platform.
     */
    protected String accountId;

    /**
     * ModelName, preferred as enum for extensibility.
     */
    protected String modelName;

    /**
     * Client for the WorkerAI API.
     */
    protected WorkersAiClient client;

    /**
     * Simple constructor.
     *
     * @param accountId account identifier.
     * @param modelName model name.
     * @param apiToken  api apiToken from .
     */
    public AbstractWorkersAIModel(String accountId, String modelName, String apiToken) {
        this(accountId, modelName, apiToken, null);
    }

    /**
     * Constructor allowing to customize the underlying HTTP client.
     *
     * @param accountId         account identifier.
     * @param modelName         model name.
     * @param apiToken          api token.
     * @param httpClientBuilder the HTTP client builder to use, may be {@code null} to use the default one.
     */
    public AbstractWorkersAIModel(
            String accountId, String modelName, String apiToken, HttpClientBuilder httpClientBuilder) {
        ensureNotEmpty(accountId, "%s", "Account identifier should not be null or empty");
        this.accountId = accountId;
        ensureNotEmpty(modelName, "%s", "Model name should not be null or empty");
        this.modelName = modelName;
        ensureNotEmpty(apiToken, "%s", "Token should not be null or empty");
        this.client = WorkersAiClient.builder()
                .apiToken(apiToken)
                .httpClientBuilder(httpClientBuilder)
                .build();
    }
}
