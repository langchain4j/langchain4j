package dev.langchain4j.model.bedrock;

import static dev.langchain4j.model.bedrock.BedrockChatModelName.ANTHROPIC_CLAUDE_4_5_HAIKU_V1_0;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.common.AbstractAiServiceWithJsonSchemaIT;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".+")
class BedrockAiServiceWithJsonSchemaIT extends AbstractAiServiceWithJsonSchemaIT {

    @Override
    protected List<ChatModel> models() {
        return List.of(BedrockChatModel.builder()
                .modelId(ANTHROPIC_CLAUDE_4_5_HAIKU_V1_0.toString())
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
