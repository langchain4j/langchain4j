package dev.langchain4j.model.azure.common;

import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;

import dev.langchain4j.model.azure.AzureModelBuilders;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.common.AbstractAiServiceIT;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "AZURE_OPENAI_KEY", matches = ".+")
class AzureOpenAiAiServiceIT extends AbstractAiServiceIT {

    @Override
    protected List<ChatModel> models() {
        return List.of(
                AzureModelBuilders.chatModelBuilder()
                        .supportedCapabilities(RESPONSE_FORMAT_JSON_SCHEMA)
                        .strictJsonSchema(true)
                        .build(),
                AzureModelBuilders.chatModelBuilder()
                        .supportedCapabilities(RESPONSE_FORMAT_JSON_SCHEMA)
                        .strictJsonSchema(false)
                        .build());
    }
}
