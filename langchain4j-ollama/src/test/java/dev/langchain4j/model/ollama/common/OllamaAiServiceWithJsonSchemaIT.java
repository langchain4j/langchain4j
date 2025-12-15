package dev.langchain4j.model.ollama.common;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;
import static dev.langchain4j.model.ollama.AbstractOllamaLanguageModelInfrastructure.OLLAMA_BASE_URL;
import static dev.langchain4j.model.ollama.AbstractOllamaLanguageModelInfrastructure.ollamaBaseUrl;
import static dev.langchain4j.model.ollama.OllamaImage.LLAMA_3_1;
import static dev.langchain4j.model.ollama.OllamaImage.OLLAMA_IMAGE;
import static dev.langchain4j.model.ollama.OllamaImage.localOllamaImage;
import static dev.langchain4j.model.ollama.OllamaImage.resolve;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.LC4jOllamaContainer;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.common.AbstractAiServiceWithJsonSchemaIT;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class OllamaAiServiceWithJsonSchemaIT extends AbstractAiServiceWithJsonSchemaIT {

    private static final String MODEL = LLAMA_3_1;
    private static LC4jOllamaContainer ollama;

    static {
        if (isNullOrEmpty(OLLAMA_BASE_URL)) {
            String localOllamaImageWithTools = localOllamaImage(MODEL);
            ollama = new LC4jOllamaContainer(resolve(OLLAMA_IMAGE, localOllamaImageWithTools)).withModel(MODEL);
            ollama.start();
            ollama.commitToImage(localOllamaImageWithTools);
        }
    }

    @Override
    protected List<ChatModel> models() {
        return List.of(OllamaChatModel.builder()
                .baseUrl(ollamaBaseUrl(ollama))
                .modelName(LLAMA_3_1)
                .supportedCapabilities(RESPONSE_FORMAT_JSON_SCHEMA)
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .build());
    }

    @Override
    @Disabled("llama 3.1 cannot do it properly")
    protected void should_extract_pojo_with_missing_data(ChatModel model) {}

    @Override
    @Disabled("llama 3.1 cannot do it properly")
    protected void should_extract_pojo_with_nested_pojo(ChatModel model) {}

    @Override
    @Disabled("llama 3.1 cannot do it properly")
    protected void should_extract_pojo_with_local_date_time_fields(ChatModel model) {}

    @Override
    @Disabled("llama 3.1 cannot do it properly")
    protected void should_extract_pojo_with_uuid(ChatModel model) {}

    @Override
    @Disabled("llama 3.1 cannot do it properly")
    protected void should_extract_list_of_pojo(ChatModel model) {}

    @Override
    @Disabled("llama 3.1 cannot do it properly")
    protected void should_extract_set_of_pojo(ChatModel model) {}
}
