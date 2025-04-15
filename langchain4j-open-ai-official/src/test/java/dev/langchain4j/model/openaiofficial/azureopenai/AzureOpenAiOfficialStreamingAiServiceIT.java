package dev.langchain4j.model.openaiofficial.azureopenai;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.common.AbstractStreamingAiServiceIT;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "AZURE_OPENAI_KEY", matches = ".+")
class AzureOpenAiOfficialStreamingAiServiceIT extends AbstractStreamingAiServiceIT {

    @Override
    protected List<StreamingChatModel> models() {
        return InternalAzureOpenAiOfficialTestHelper.chatModelsStreamingNormalAndJsonStrict();
    }
}
