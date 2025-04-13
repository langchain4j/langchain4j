package dev.langchain4j.model.workersai.client;

import okhttp3.ResponseBody;
import org.slf4j.Logger;

import java.io.IOException;

/**
 * Abstract class for WorkerAI models as they are all initialized the same way.
 * <a href="https://developers.cloudflare.com/api/operations/workers-ai-post-run-model">...</a>
 */
public abstract class AbstractWorkersAIModel {

    private static final Logger log = org.slf4j.LoggerFactory.getLogger(AbstractWorkersAIModel.class);
    /**
     * Account identifier, provided by the WorkerAI platform.
     */
    protected String accountId;

    /**
     * ModelName, preferred as enum for extensibility.
     */
    protected String modelName;

    /**
     * OkHttpClient for the WorkerAI API.
     */
    protected WorkersAiApi workerAiClient;

    /**
     * Simple constructor.
     *
     * @param accountId account identifier.
     * @param modelName model name.
     * @param apiToken  api apiToken from .
     */
    public AbstractWorkersAIModel(String accountId, String modelName, String apiToken) {
        if (accountId == null || accountId.isEmpty()) {
            throw new IllegalArgumentException("Account identifier should not be null or empty");
        }
        this.accountId = accountId;
        if (modelName == null || modelName.isEmpty()) {
            throw new IllegalArgumentException("Model name should not be null or empty");
        }
        this.modelName = modelName;
        if (apiToken == null || apiToken.isEmpty()) {
            throw new IllegalArgumentException("Token should not be null or empty");
        }
        this.workerAiClient = WorkersAiClient.createService(apiToken);
    }

    /**
     * Process errors from the API.
     *
     * @param res    response
     * @param errors errors body from retrofit
     * @throws IOException error occurred during invocation
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


}
