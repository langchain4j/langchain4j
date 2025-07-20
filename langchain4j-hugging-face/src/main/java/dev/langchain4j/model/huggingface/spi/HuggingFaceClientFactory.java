package dev.langchain4j.model.huggingface.spi;

import dev.langchain4j.model.huggingface.client.HuggingFaceClient;

import java.time.Duration;

public interface HuggingFaceClientFactory {

    HuggingFaceClient create(Input input);

    interface Input {

        default String baseUrl() {
            return null;
        }

        String apiKey();

        String modelId();

        Duration timeout();
    }
}
