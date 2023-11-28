package dev.langchain4j.model.workerai.client;

import dev.langchain4j.model.workerai.WorkerAiChatModel;
import dev.langchain4j.model.workerai.WorkerAiEmbeddingModel;
import dev.langchain4j.model.workerai.WorkerAiImageModel;
import dev.langchain4j.model.workerai.WorkerAiLanguageModel;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ResponseBody;

import java.io.IOException;

/**
 * Abstract class for WorkerAI models as they are all initialized the same way.
 * <a href="https://developers.cloudflare.com/api/operations/workers-ai-post-run-model">...</a>
 */
@Slf4j
public abstract class AbstractWorkerAIModel {

    /**
     * Account identifier, provided by the WorkerAI platform.
     */
    protected String accountIdentifier;

    /**
     * ModelName, preferred as enum for extensibility.
     *
     * @see dev.langchain4j.model.workerai.WorkerAiModelName
     */
    protected String modelName;

    /**
     * OkHttpClient for the WorkerAI API.
     *
     * @see dev.langchain4j.model.workerai.WorkerAiModelName
     */
    protected WorkerAiApi workerAiClient;

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
    public AbstractWorkerAIModel(String accountIdentifier, String modelName, String token) {
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
        this.workerAiClient = WorkerAiClient.createService(token);
    }

    /**
     * Constructor with Builder.
     *
     * @param builder
     *      common builder.
     */
    public AbstractWorkerAIModel(WorkerAiModelBuilder builder) {
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
    public static WorkerAiModelBuilder builder() {
        return new WorkerAiModelBuilder();
    }

    /**
     * Internal Builder.
     */
    public static class WorkerAiModelBuilder {

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
        public WorkerAiModelBuilder() {
        }

        /**
         * Simple constructor.
         *
         * @param accountIdentifier
         *      account identifier.
         * @return
         *      self reference
         */
        public WorkerAiModelBuilder accountIdentifier(String accountIdentifier) {
            this.accountIdentifier = accountIdentifier;
            return this;
        }

        /**
         * Sets the token for the Worker AI model builder.
         *
         * @param token The token to set.
         * @return The current instance of {@link WorkerAiModelBuilder}.
         */
        public WorkerAiModelBuilder token(String token) {
            this.token = token;
            return this;
        }

        /**
         * Sets the model name for the Worker AI model builder.
         *
         * @param modelName The name of the model to set.
         * @return The current instance of {@link WorkerAiModelBuilder}.
         */
        public WorkerAiModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        /**
         * Builds a new instance of Worker AI Chat Model.
         *
         * @return A new instance of {@link WorkerAiChatModel}.
         */
        public WorkerAiChatModel buildChatModel() {
            return new WorkerAiChatModel(this);
        }

        /**
         * Builds a new instance of Worker AI Language Model.
         *
         * @return A new instance of {@link WorkerAiLanguageModel}.
         */
        public WorkerAiLanguageModel buildLanguageModel() {
            return new WorkerAiLanguageModel(this);
        }

        /**
         * Builds a new instance of Worker AI Embedding Model.
         *
         * @return A new instance of {@link WorkerAiEmbeddingModel}.
         */
        public WorkerAiEmbeddingModel buildEmbeddingModel() {
            return new WorkerAiEmbeddingModel(this);
        }

        /**
         * Builds a new instance of Worker AI Image Model.
         *
         * @return A new instance of {@link WorkerAiImageModel}.
         */
        public WorkerAiImageModel buildImageModel() {
            return new WorkerAiImageModel(this);
        }

    }
}
