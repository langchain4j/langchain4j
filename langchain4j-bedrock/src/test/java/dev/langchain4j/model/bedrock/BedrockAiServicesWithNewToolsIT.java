package dev.langchain4j.model.bedrock;

import static dev.langchain4j.model.bedrock.BedrockChatModelWithInvokeAPIIT.sleepIfNeeded;
import static dev.langchain4j.model.bedrock.TestedModelsWithConverseAPI.CLAUDE_3_HAIKU;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServicesWithNewToolsIT;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".+")
class BedrockAiServicesWithNewToolsIT extends AiServicesWithNewToolsIT {

    @Override
    protected List<ChatLanguageModel> models() {
        return List.of(CLAUDE_3_HAIKU.model());
    }

    @AfterEach
    void afterEach() {
        sleepIfNeeded();
    }
}
