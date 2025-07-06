package dev.langchain4j.model.bedrock.common;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.common.AbstractAiServiceIT;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

import static dev.langchain4j.model.bedrock.TestedModels.AWS_NOVA_LITE;
import static dev.langchain4j.model.bedrock.TestedModels.AWS_NOVA_MICRO;
import static dev.langchain4j.model.bedrock.TestedModels.AWS_NOVA_PRO;
import static dev.langchain4j.model.bedrock.TestedModels.CLAUDE_3_HAIKU;
import static dev.langchain4j.model.bedrock.TestedModels.COHERE_COMMAND_R_PLUS;
import static dev.langchain4j.model.bedrock.TestedModels.MISTRAL_LARGE;

@EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".+")
public class BedrockAiServicesIT extends AbstractAiServiceIT {

    @Override
    protected List<ChatModel> models() {
        return List.of(
                AWS_NOVA_MICRO, AWS_NOVA_LITE, AWS_NOVA_PRO, COHERE_COMMAND_R_PLUS, MISTRAL_LARGE, CLAUDE_3_HAIKU);
    }

    @AfterEach
    void afterEach() {
        sleepIfNeeded();
    }

    public static void sleepIfNeeded() {
        try {
            String ciDelaySeconds = System.getenv("CI_DELAY_SECONDS_BEDROCK");
            if (ciDelaySeconds != null) {
                Thread.sleep(Integer.parseInt(ciDelaySeconds) * 1000L);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
