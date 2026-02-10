package dev.langchain4j.model.bedrock.common;

import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;

import dev.langchain4j.model.bedrock.BedrockChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.common.AbstractAiServiceWithJsonSchemaIT;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".+")
class BedrockAiServiceWithJsonSchemaIT extends AbstractAiServiceWithJsonSchemaIT {

    @Override
    protected List<ChatModel> models() {
        return List.of(BedrockChatModel.builder()
                .modelId("us.amazon.nova-lite-v1:0")
                .supportedCapabilities(RESPONSE_FORMAT_JSON_SCHEMA)
                .build());
    }

    @AfterEach
    void afterEach() {
        BedrockAiServicesIT.sleepIfNeeded();
    }
}
