package dev.langchain4j.model.workersai;

import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.language.LanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.workersai.client.AbstractWorkersAIModel;
import dev.langchain4j.model.workersai.client.WorkersAiTextCompletionRequest;
import dev.langchain4j.model.workersai.client.WorkersAiTextCompletionResponse;
import dev.langchain4j.model.workersai.spi.WorkersAiLanguageModelBuilderFactory;
import org.slf4j.Logger;

import java.io.IOException;

import static dev.langchain4j.spi.ServiceHelper.loadFactories;

/**
 * WorkerAI Language model.
 * <a href="https://developers.cloudflare.com/api/operations/workers-ai-post-run-model">...</a>
 */
public class WorkersAiLanguageModel extends AbstractWorkersAIModel implements LanguageModel {

    private static final Logger log = org.slf4j.LoggerFactory.getLogger(WorkersAiLanguageModel.class);

    /**
     * Constructor with Builder.
     *
     * @param builder builder.
     */
    public WorkersAiLanguageModel(Builder builder) {
        this(builder.accountId, builder.modelName, builder.apiToken);
    }

    /**
     * Constructor with Builder.
     *
     * @param accountId account identifier
     * @param modelName model name
     * @param apiToken  api token
     */
    public WorkersAiLanguageModel(String accountId, String modelName, String apiToken) {
        super(accountId, modelName, apiToken);
    }

    /**
     * Builder access.
     *
     * @return builder instance
     */
    public static WorkersAiLanguageModel.Builder builder() {
        for (WorkersAiLanguageModelBuilderFactory factory : loadFactories(WorkersAiLanguageModelBuilderFactory.class)) {
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
        public String accountId;
        /**
         * ModelName, preferred as enum for extensibility.
         */
        public String apiToken;
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
         * @param accountId account identifier.
         * @return self reference
         */
        public Builder accountId(String accountId) {
            this.accountId = accountId;
            return this;
        }

        /**
         * Sets the apiToken for the Worker AI model builder.
         *
         * @param apiToken The apiToken to set.
         * @return The current instance of {@link WorkersAiChatModel.Builder}.
         */
        public Builder apiToken(String apiToken) {
            this.apiToken = apiToken;
            return this;
        }

        /**
         * Sets the model name for the Worker AI model builder.
         *
         * @param modelName The name of the model to set.
         * @return The current instance of {@link WorkersAiChatModel.Builder}.
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
        public WorkersAiLanguageModel build() {
            return new WorkersAiLanguageModel(this);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Response<String> generate(String prompt) {
        try {
            retrofit2.Response<WorkersAiTextCompletionResponse> retrofitResponse = workerAiClient
                    .generateText(new WorkersAiTextCompletionRequest(prompt), accountId, modelName)
                    .execute();
            processErrors(retrofitResponse.body(), retrofitResponse.errorBody());
            if (retrofitResponse.body() == null) {
                throw new RuntimeException("Empty response");
            }
            return new Response<>(retrofitResponse.body().getResult().getResponse());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Response<String> generate(Prompt prompt) {
        return generate(prompt.text());
    }
}
