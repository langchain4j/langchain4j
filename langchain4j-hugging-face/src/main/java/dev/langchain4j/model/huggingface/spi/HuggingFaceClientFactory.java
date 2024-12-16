package dev.langchain4j.model.huggingface.spi;

import dev.langchain4j.model.huggingface.client.HuggingFaceClient;
import java.time.Duration;
import java.util.Optional;

public interface HuggingFaceClientFactory {

    HuggingFaceClient create(Input input);

    interface Input {
        Optional<String> baseUrl();

        String apiKey();

        String modelId();

        Duration timeout();
    }
}
