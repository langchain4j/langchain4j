package dev.langchain4j.model.bedrock;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.common.AbstractAiServiceIT;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

import static dev.langchain4j.model.bedrock.TestedModelsWithConverseAPI.AWS_NOVA_LITE;
import static dev.langchain4j.model.bedrock.TestedModelsWithConverseAPI.AWS_NOVA_MICRO;
import static dev.langchain4j.model.bedrock.TestedModelsWithConverseAPI.AWS_NOVA_PRO;
import static dev.langchain4j.model.bedrock.TestedModelsWithConverseAPI.CLAUDE_3_HAIKU;
import static dev.langchain4j.model.bedrock.TestedModelsWithConverseAPI.COHERE_COMMAND_R_PLUS;
import static dev.langchain4j.model.bedrock.TestedModelsWithConverseAPI.MISTRAL_LARGE;

@EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".+")
public class BedrockAiServicesIT extends AbstractAiServiceIT {

    @Override
    protected List<ChatLanguageModel> models() {
        return List.of(
                AWS_NOVA_MICRO, AWS_NOVA_LITE, AWS_NOVA_PRO, COHERE_COMMAND_R_PLUS, MISTRAL_LARGE, CLAUDE_3_HAIKU);
    }
}
