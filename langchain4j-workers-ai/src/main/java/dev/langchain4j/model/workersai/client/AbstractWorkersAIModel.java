package dev.langchain4j.model.workersai.client;

import dev.langchain4j.model.workersai.WorkersAiChatModel;
import dev.langchain4j.model.workersai.WorkersAiEmbeddingModel;
import dev.langchain4j.model.workersai.WorkersAiImageModel;
import dev.langchain4j.model.workersai.WorkersAiLanguageModel;
import dev.langchain4j.model.workersai.WorkersAiModelName;
import dev.langchain4j.model.workersai.spi.WorkersAiChatModelBuilderFactory;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ResponseBody;

import java.io.IOException;

import static dev.langchain4j.spi.ServiceHelper.loadFactories;

/**
 * Abstract class for WorkerAI models as they are all initialized the same way.
 * <a href="https://developers.cloudflare.com/api/operations/workers-ai-post-run-model">...</a>
 */
@Slf4j
public abstract class AbstractWorkersAIModel {

    /**
     * Account identifier, provided by the WorkerAI platform.
     */
    protected String accountIdentifier;

    /**
     * ModelName, preferred as enum for extensibility.
     *
     * @see WorkersAiModelName
     */
    protected String modelName;

    /**
     * OkHttpClient for the WorkerAI API.
     *
     * @see WorkersAiModelName
     */
    protected WorkersAiApi workerAiClient;

    /**
     * Simple constructor.
     *
     * @param accountIdentifier
     *      account identifier.
     * @param modelName
     *      model name.
     * @param token
     *      api token from .
     */
    public AbstractWorkersAIModel(String accountIdentifier, String modelName, String token) {
        if (accountIdentifier == null || accountIdentifier.isEmpty()) {
            throw new IllegalArgumentException("Account identifier should not be null or empty");
        }
        this.accountIdentifier = accountIdentifier;
        if (modelName == null || modelName.isEmpty()) {
            throw new IllegalArgumentException("Model name should not be null or empty");
        }
        this.modelName  = modelName;
        if (token == null || token.isEmpty()) {
            throw new IllegalArgumentException("Token should not be null or empty");
        }
        this.workerAiClient = WorkersAiClient.createService(token);
    }

    /**
     * Constructor with Builder.
     *
     * @param builder
     *      common builder.
     */
    public AbstractWorkersAIModel(Builder builder) {
        this(builder.accountIdentifier, builder.modelName, builder.token);
    }

    /**
     * Process errors from the API.
     * @param res
     *      response
     * @param errors
     *      errors body from retrofit
     * @throws IOException
     *      error occurred during invocation
     */
    protected void processErrors(ApiResponse<?> res, ResponseBody errors)
    throws IOException {
        if (res == null || !res.isSuccess()) {
            StringBuilder errorMessage = new StringBuilder("Failed to generate chat message:");
            if (res == null) {
                errorMessage.append(errors.string());
            } else if (res.getErrors() != null) {
                errorMessage.append(res.getErrors().stream()
                        .map(ApiResponse.Error::getMessage)
                        .reduce((a, b) -> a + "\n" + b)
                        .orElse(""));
            }
            log.error(errorMessage.toString());
            throw new RuntimeException(errorMessage.toString());
        }
    }

    /**
     * Builder access.
     *
     * @return
     *      builder instance
     */
    public static Builder builder() {
        for (WorkersAiChatModelBuilderFactory factory : loadFactories(WorkersAiChatModelBuilderFactory.class)) {
            return factory.get();
        }
        return new Builder();
    }

    /**
     * Internal Builder.
     */
    public static class Builder {

        /**
         * Account identifier, provided by the WorkerAI platform.
         */
        public String accountIdentifier;
        /**
         * ModelName, preferred as enum for extensibility.
         */
        public String token;
        /**
         * ModelName, preferred as enum for extensibility.
         */
        public String modelName;

        /**
         * Simple constructor.
         */
        public Builder() {
        }

        /**
         * Simple constructor.
         *
         * @param accountIdentifier
         *      account identifier.
         * @return
         *      self reference
         */
        public Builder accountIdentifier(String accountIdentifier) {
            this.accountIdentifier = accountIdentifier;
            return this;
        }

        /**
         * Sets the token for the Worker AI model builder.
         *
         * @param token The token to set.
         * @return The current instance of {@link Builder}.
         */
        public Builder token(String token) {
            this.token = token;
            return this;
        }

        /**
         * Sets the model name for the Worker AI model builder.
         *
         * @param modelName The name of the model to set.
         * @return The current instance of {@link Builder}.
         */
        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        /**
         * Builds a new instance of Worker AI Chat Model.
         *
         * @return A new instance of {@link WorkersAiChatModel}.
         */
        public WorkersAiChatModel buildChatModel() {
            return new WorkersAiChatModel(this);
        }

        /**
         * Builds a new instance of Worker AI Language Model.
         *
         * @return A new instance of {@link WorkersAiLanguageModel}.
         */
        public WorkersAiLanguageModel buildLanguageModel() {
            return new WorkersAiLanguageModel(this);
        }

        /**
         * Builds a new instance of Worker AI Embedding Model.
         *
         * @return A new instance of {@link WorkersAiEmbeddingModel}.
         */
        public WorkersAiEmbeddingModel buildEmbeddingModel() {
            return new WorkersAiEmbeddingModel(this);
        }

        /**
         * Builds a new instance of Worker AI Image Model.
         *
         * @return A new instance of {@link WorkersAiImageModel}.
         */
        public WorkersAiImageModel buildImageModel() {
            return new WorkersAiImageModel(this);
        }

    }
}
