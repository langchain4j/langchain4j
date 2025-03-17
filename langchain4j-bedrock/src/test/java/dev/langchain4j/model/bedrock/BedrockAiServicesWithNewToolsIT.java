package dev.langchain4j.model.bedrock;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServicesWithNewToolsIT;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

import static dev.langchain4j.model.bedrock.BedrockChatModelWithInvokeAPIIT.sleepIfNeeded;
import static dev.langchain4j.model.bedrock.TestedModelsWithConverseAPI.CLAUDE_3_HAIKU;

@EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".+")
class BedrockAiServicesWithNewToolsIT extends AiServicesWithNewToolsIT {

    @Override
    protected List<ChatLanguageModel> models() {
        return List.of(CLAUDE_3_HAIKU);
    }

    @AfterEach
    void afterEach() {
        sleepIfNeeded();
    }
}
