package dev.langchain4j.model.azure.common;

import static dev.langchain4j.model.azure.common.AzureOpenAiResponsesStreamingChatModelIT.AZURE_OPEN_AI_RESPONSES_STREAMING_CHAT_MODEL;

import java.util.List;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.common.AbstractStreamingAiServiceIT;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "AZURE_OPENAI_KEY", matches = ".+")
class AzureOpenAiResponsesStreamingAiServiceIT extends AbstractStreamingAiServiceIT {

    @Override
    protected List<StreamingChatModel> models() {
        return List.of(
                AZURE_OPEN_AI_RESPONSES_STREAMING_CHAT_MODEL
                // TODO add more model configs
                );
    }
}
