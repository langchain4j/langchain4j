package dev.langchain4j.model.mistralai;

import static dev.langchain4j.model.mistralai.MistralAiChatModelName.*;

import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServicesWithJsonSchemaIT;
import java.util.List;

public class MistralAiAiServicesWithJsonSchemaIT extends AiServicesWithJsonSchemaIT {
    @Override
    protected List<ChatLanguageModel> models() {
        return List.of(MistralAiChatModel.builder()
                .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
                .modelName(MISTRAL_SMALL_LATEST)
                .temperature(0.1)
                .logRequests(true)
                .logResponses(true)
                .supportedCapabilities(Capability.RESPONSE_FORMAT_JSON_SCHEMA)
                .build());
    }

    @Override
    protected boolean supportsRecursion() {
        return true;
    }
}
