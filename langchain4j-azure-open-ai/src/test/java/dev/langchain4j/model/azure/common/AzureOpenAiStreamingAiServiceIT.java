package dev.langchain4j.model.azure.common;

import static dev.langchain4j.model.azure.common.AzureOpenAiStreamingChatModelIT.AZURE_OPEN_AI_STREAMING_CHAT_MODEL;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.common.AbstractStreamingAiServiceIT;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "AZURE_OPENAI_KEY", matches = ".+")
class AzureOpenAiStreamingAiServiceIT extends AbstractStreamingAiServiceIT {

    @Override
    protected List<StreamingChatModel> models() {
        return List.of(
                AZURE_OPEN_AI_STREAMING_CHAT_MODEL
                // TODO add more model configs
                );
    }
}
