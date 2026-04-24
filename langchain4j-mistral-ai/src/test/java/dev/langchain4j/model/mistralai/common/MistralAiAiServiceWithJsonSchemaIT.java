package dev.langchain4j.model.mistralai.common;

import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;
import static dev.langchain4j.model.mistralai.MistralAiChatModelName.MISTRAL_SMALL_LATEST;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.mistralai.MistralAiChatModel;
import dev.langchain4j.service.common.AbstractAiServiceWithJsonSchemaIT;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "MISTRAL_AI_API_KEY", matches = ".+")
public class MistralAiAiServiceWithJsonSchemaIT extends AbstractAiServiceWithJsonSchemaIT {

    ChatModel strictModel = MistralAiChatModel.builder()
            .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
            .modelName(MISTRAL_SMALL_LATEST)
            .temperature(0.1)
            .logRequests(true)
            .logResponses(true)
            .supportedCapabilities(RESPONSE_FORMAT_JSON_SCHEMA)
            .strictJsonSchema(true)
            .build();

    ChatModel nonStrictModel = MistralAiChatModel.builder()
            .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
            .modelName(MISTRAL_SMALL_LATEST)
            .temperature(0.1)
            .logRequests(true)
            .logResponses(true)
            .supportedCapabilities(RESPONSE_FORMAT_JSON_SCHEMA)
            .build();

    @Override
    protected List<ChatModel> models() {
        return List.of(nonStrictModel, strictModel);
    }

    @Override
    protected boolean isStrictJsonSchemaEnabled(ChatModel model) {
        return model == strictModel;
    }

    @Override
    protected boolean supportsRecursion() {
        return true;
    }
}
