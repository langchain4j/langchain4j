package dev.langchain4j.model.ollama;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServicesWithJsonSchemaIT;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;
import static dev.langchain4j.model.chat.request.ResponseFormat.JSON;
import static dev.langchain4j.model.ollama.AbstractOllamaLanguageModelInfrastructure.OLLAMA_BASE_URL;
import static dev.langchain4j.model.ollama.OllamaImage.TOOL_MODEL;

class OllamaAiServicesWithJsonSchemaIT extends AiServicesWithJsonSchemaIT {

    private static final String LOCAL_OLLAMA_IMAGE = String.format("tc-%s-%s", OllamaImage.OLLAMA_IMAGE, TOOL_MODEL);

    static LangChain4jOllamaContainer ollama;

    static {
        String ollamaBaseUrl = System.getenv("OLLAMA_BASE_URL");
        if (isNullOrEmpty(ollamaBaseUrl)) {
            ollama = new LangChain4jOllamaContainer(OllamaImage.resolve(OllamaImage.OLLAMA_IMAGE, LOCAL_OLLAMA_IMAGE))
                    .withModel(TOOL_MODEL);
            ollama.start();
            ollama.commitToImage(LOCAL_OLLAMA_IMAGE);
        }
    }

    public static String ollamaBaseUrl() {
        if (isNullOrEmpty(OLLAMA_BASE_URL)) {
            return ollama.getEndpoint();
        } else {
            return OLLAMA_BASE_URL;
        }
    }

    @Override
    protected List<ChatLanguageModel> models() {
        return List.of(
                OllamaChatModel.builder()
                        .baseUrl(ollamaBaseUrl())
                        .modelName(TOOL_MODEL)
                        .responseFormat(JSON)
                        .capabilities(Set.of(RESPONSE_FORMAT_JSON_SCHEMA))
                        .temperature(0.0)
                        .logRequests(true)
                        .logResponses(true)
                        .build()
        );
    }


    @Test
    @Disabled("llama 3.1 is cannot do it properly")
    @Override
    protected void should_extract_pojo_with_nested_pojo() {
    }

    @Test
    @Disabled("llama 3.1 is cannot do it properly")
    @Override
    protected void should_extract_pojo_with_list_of_pojos() {
    }

    @Test
    @Disabled("llama 3.1 is cannot do it properly")
    @Override
    protected void should_extract_pojo_with_array_of_pojos() {
    }

    @Test
    @Disabled("llama 3.1 is cannot do it properly")
    @Override
    protected void should_extract_pojo_with_set_of_pojos() {
    }

}
