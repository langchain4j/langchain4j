package dev.langchain4j.model.azure.common;

import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;

import dev.langchain4j.model.azure.AzureModelBuilders;
import dev.langchain4j.model.azure.AzureOpenAiChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.common.AbstractAiServiceWithJsonSchemaIT;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "AZURE_OPENAI_KEY", matches = ".+")
class AzureOpenAiAiServiceWithJsonSchemaIT extends AbstractAiServiceWithJsonSchemaIT {

    AzureOpenAiChatModel model = AzureModelBuilders.chatModelBuilder()
            .supportedCapabilities(Set.of(RESPONSE_FORMAT_JSON_SCHEMA))
            .strictJsonSchema(false)
            .temperature(0.0)
            .build();

    AzureOpenAiChatModel modelWithStrictJsonSchema = AzureModelBuilders.chatModelBuilder()
            .supportedCapabilities(Set.of(RESPONSE_FORMAT_JSON_SCHEMA))
            .strictJsonSchema(true)
            .temperature(0.0)
            .build();

    @Override
    protected List<ChatModel> models() {
        return List.of(model, modelWithStrictJsonSchema);
    }

    @Override
    protected boolean supportsRecursion() {
        return true;
    }

    @Override
    protected boolean isStrictJsonSchemaEnabled(ChatModel model) {
        return model == modelWithStrictJsonSchema;
    }

    @AfterEach
    void afterEach() throws InterruptedException {
        String ciDelaySeconds = System.getenv("CI_DELAY_SECONDS_AZURE_OPENAI");
        if (ciDelaySeconds != null) {
            Thread.sleep(Integer.parseInt(ciDelaySeconds) * 1000L);
        }
    }
}
