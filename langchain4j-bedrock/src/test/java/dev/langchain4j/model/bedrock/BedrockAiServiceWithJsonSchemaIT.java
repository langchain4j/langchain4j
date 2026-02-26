package dev.langchain4j.model.bedrock;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.common.AbstractAiServiceWithJsonSchemaIT;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".+")
class BedrockAiServiceWithJsonSchemaIT extends AbstractAiServiceWithJsonSchemaIT {

    @Override
    protected List<ChatModel> models() {
        return List.of(BedrockChatModel.builder()
                .modelId("anthropic.claude-haiku-4-5-20251001-v1:0")
                .maxRetries(1)
                .logRequests(false)
                .logRequests(true)
                .build());
    }

    @Override
    protected boolean isStrictJsonSchemaEnabled(ChatModel model) {
        // Bedrock models support strict JSON schema
        return true;
    }
}
