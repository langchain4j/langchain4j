package dev.langchain4j.model.mistralai.common;

import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;
import static dev.langchain4j.model.mistralai.MistralAiChatModelName.MISTRAL_SMALL_LATEST;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.mistralai.MistralAiChatModel;
import dev.langchain4j.service.common.AbstractAiServiceWithJsonSchemaIT;
import java.util.List;

public class MistralAiAiServiceWithJsonSchemaIT extends AbstractAiServiceWithJsonSchemaIT {

    @Override
    protected List<ChatModel> models() {
        // TODO test both strict and non-strict
        return List.of(MistralAiChatModel.builder()
                .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
                .modelName(MISTRAL_SMALL_LATEST)
                .temperature(0.1)
                .logRequests(true)
                .logResponses(true)
                .supportedCapabilities(RESPONSE_FORMAT_JSON_SCHEMA)
                .build());
    }

    @Override
    protected boolean supportsRecursion() {
        return true;
    }
}
