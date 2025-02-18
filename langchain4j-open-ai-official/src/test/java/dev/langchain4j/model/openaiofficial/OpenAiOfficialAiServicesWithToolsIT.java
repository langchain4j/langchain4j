package dev.langchain4j.model.openaiofficial;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServicesWithNewToolsIT;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "AZURE_OPENAI_KEY", matches = ".+")
class OpenAiOfficialAiServicesWithToolsIT extends AiServicesWithNewToolsIT {

    @Override
    protected List<ChatLanguageModel> models() {
        return InternalOpenAiOfficialTestHelper.chatModelsNormalAndStrictTools();
    }

    @Override
    protected boolean supportsMapParameters() {
        // When strictTools=true , Map parameters are not supported as there is no JsonSchemaElement for them at the moment.
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
