package dev.langchain4j.model.novitaai.client;

import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;

import java.io.IOException;

/**
 * Abstract class for Novita models as they are all initialized the same way.
 */
@Slf4j
public abstract class AbstractNovitaAIModel {

    /**
     * ModelName, preferred as enum for extensibility.
     */
    protected String modelName;

    /**
     * OkHttpClient for the Novita API.
     */
    protected NovitaAiApi novitaClient;

    /**
     * Simple constructor.
     *
     * @param modelName model name.
     * @param apiKey  api apiKey from .
     */
    public AbstractNovitaAIModel(String modelName, String apiKey) {
        if (modelName == null || modelName.isEmpty()) {
            throw new IllegalArgumentException("Model name should not be null or empty");
        }
        this.modelName = modelName;
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalArgumentException("Token should not be null or empty");
        }
        this.novitaClient = NovitaAiClient.createService(apiKey);
    }

}
