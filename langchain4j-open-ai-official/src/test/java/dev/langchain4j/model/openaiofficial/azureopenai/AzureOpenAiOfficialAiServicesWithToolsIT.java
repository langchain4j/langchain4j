package dev.langchain4j.model.openaiofficial.azureopenai;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServicesWithNewToolsIT;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "AZURE_OPENAI_KEY", matches = ".+")
class AzureOpenAiOfficialAiServicesWithToolsIT extends AiServicesWithNewToolsIT {

    @Override
    protected List<ChatLanguageModel> models() {
        return InternalAzureOpenAiOfficialTestHelper.chatModelsNormalAndStrictTools();
    }

    @Override
    protected boolean supportsMapParameters() {
        // When strictTools=true , Map parameters are not supported as there is no JsonSchemaElement for them at the
        // moment.
        return false;
    }

    @Override
    protected boolean supportsRecursion() {
        return true;
    }

    @Override
    protected boolean verifyModelInteractions() {
        return true;
    }
}
