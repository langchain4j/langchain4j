package dev.langchain4j.model.bedrock;

import static dev.langchain4j.model.bedrock.TestedModelsWithConverseAPI.*;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServicesWithNewToolsIT;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".+")
class BedrockAiServicesWithNewToolsIT extends AiServicesWithNewToolsIT {
    @Override
    protected List<ChatLanguageModel> models() {
        return List.of(AWS_NOVA_MICRO);
    }

    @AfterEach
    void afterEach() throws InterruptedException {
        String ciDelaySeconds = System.getenv("CI_DELAY_SECONDS_BEDROCK");
        if (ciDelaySeconds != null) {
            Thread.sleep(Integer.parseInt(ciDelaySeconds) * 1000L);
        }
    }
}
