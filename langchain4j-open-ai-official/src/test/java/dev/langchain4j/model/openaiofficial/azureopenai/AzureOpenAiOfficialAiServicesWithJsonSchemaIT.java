package dev.langchain4j.model.openaiofficial.azureopenai;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServicesWithJsonSchemaIT;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "AZURE_OPENAI_KEY", matches = ".+")
class AzureOpenAiOfficialAiServicesWithJsonSchemaIT extends AiServicesWithJsonSchemaIT {

    @Override
    protected List<ChatLanguageModel> models() {
        return InternalAzureOpenAiOfficialTestHelper.chatModelsWithJsonResponse();
    }

    @Override
    protected boolean supportsRecursion() {
        return true;
    }
}
