package dev.langchain4j.model.openaiofficial.azureopenai;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.common.AbstractChatModelAndCapabilities;
import dev.langchain4j.service.common.AbstractAiServiceIT;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "AZURE_OPENAI_KEY", matches = ".+")
class AzureOpenAiOfficialAiServiceIT extends AbstractAiServiceIT {

    @Override
    protected List<ChatLanguageModel> models() {
        return InternalAzureOpenAiOfficialTestHelper.chatModelsNormalAndJsonStrict().stream()
                .map(AbstractChatModelAndCapabilities::model)
                .toList();
    }
}
