package dev.langchain4j.model.azure.common;

import dev.langchain4j.model.azure.AzureModelBuilders;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiTokenUsage;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.common.AbstractAiServiceIT;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "AZURE_OPENAI_KEY", matches = ".+")
class AzureOpenAiResponsesAiServiceIT extends AbstractAiServiceIT {

    @Override
    protected List<ChatModel> models() {
        return List.of(
                AzureModelBuilders.responsesChatModelBuilder()
                        .strictJsonSchema(true)
                        .build(),
                AzureModelBuilders.responsesChatModelBuilder()
                        .strictJsonSchema(false)
                        .build());
    }

    @Override
    protected Class<? extends TokenUsage> tokenUsageType(ChatModel chatModel) {
        return OpenAiTokenUsage.class;
    }
}
