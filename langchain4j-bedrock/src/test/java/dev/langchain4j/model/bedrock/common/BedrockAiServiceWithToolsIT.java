package dev.langchain4j.model.bedrock.common;

import static dev.langchain4j.model.bedrock.common.BedrockAiServicesIT.sleepIfNeeded;

import dev.langchain4j.model.bedrock.BedrockChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.common.AbstractAiServiceWithToolsIT;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

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
                        .build());
    }

    @Override
    protected List<ChatModel> modelsSupportingMapParametersInTools() {
        return List.of(models().get(0));
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
