package dev.langchain4j.model.bedrock.common;

import dev.langchain4j.model.bedrock.BedrockChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.common.AbstractAiServiceWithJsonSchemaIT;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;

@EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".+")
class BedrockAiServiceWithJsonSchemaIT extends AbstractAiServiceWithJsonSchemaIT {

    static ChatModel model = BedrockChatModel.builder()
            .modelId("us.anthropic.claude-haiku-4-5-20251001-v1:0")
            .supportedCapabilities(RESPONSE_FORMAT_JSON_SCHEMA)
            .logRequests(true)
            .logResponses(true)
            .build();

    @Override
    protected List<ChatModel> models() {
        return List.of(model);
    }

    @Override
    protected boolean isStrictJsonSchemaEnabled(ChatModel model) {
        return true;
    }
}
