package dev.langchain4j.model.bedrock.common;

import dev.langchain4j.model.bedrock.BedrockChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.common.AbstractAiServiceWithToolsIT;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

import static dev.langchain4j.model.bedrock.TestedModels.CLAUDE_3_HAIKU;
import static dev.langchain4j.model.bedrock.common.BedrockAiServicesIT.sleepIfNeeded;

@EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".+")
class BedrockAiServiceWithToolsIT extends AbstractAiServiceWithToolsIT {

    @Override
    protected List<ChatModel> models() {
        return List.of(
                BedrockChatModel.builder()
                        .modelId("us.anthropic.claude-haiku-4-5-20251001-v1:0")
                        .build(),
                BedrockChatModel.builder()
                        .modelId("us.anthropic.claude-haiku-4-5-20251001-v1:0")
                        .strictTools(true)
                        .build()
        );
    }

    @Override
    protected boolean supportsMultimodalToolResults() {
        return true;
    }

    @AfterEach
    void afterEach() {
        sleepIfNeeded();
    }
}
