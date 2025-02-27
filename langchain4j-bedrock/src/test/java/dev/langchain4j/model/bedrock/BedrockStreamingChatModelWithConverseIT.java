package dev.langchain4j.model.bedrock;

import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelIT;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".+")
class BedrockStreamingChatModelWithConverseIT extends AbstractStreamingChatModelIT {

    @Override
    protected List<StreamingChatLanguageModel> models() {
        return List.of(
                TestedModelsWithConverseAPI.STREAMING_AWS_NOVA_MICRO,
                TestedModelsWithConverseAPI.STREAMING_AWS_NOVA_LITE,
                TestedModelsWithConverseAPI.STREAMING_AWS_NOVA_PRO,
                TestedModelsWithConverseAPI.STREAMING_AI_JAMBA_1_5_MINI,
                TestedModelsWithConverseAPI.STREAMING_CLAUDE_3_HAIKU,
                TestedModelsWithConverseAPI.STREAMING_COHERE_COMMAND_R_PLUS,
                TestedModelsWithConverseAPI.STREAMING_MISTRAL_LARGE);
    }
}
