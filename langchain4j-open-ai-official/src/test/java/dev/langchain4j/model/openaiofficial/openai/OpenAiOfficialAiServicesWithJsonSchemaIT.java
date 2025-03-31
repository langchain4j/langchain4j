package dev.langchain4j.model.openaiofficial.openai;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServicesWithJsonSchemaIT;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiOfficialAiServicesWithJsonSchemaIT extends AiServicesWithJsonSchemaIT {

    @Override
    protected List<ChatLanguageModel> models() {
        return InternalOpenAiOfficialTestHelper.chatModelsWithJsonResponse();
    }

    @Override
    protected boolean supportsRecursion() {
        return true;
    }
}
