package dev.langchain4j.model.openaiofficial.openai;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.common.AbstractAiServiceWithJsonSchemaIT;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static dev.langchain4j.model.openaiofficial.openai.InternalOpenAiOfficialTestHelper.OPEN_AI_CHAT_MODEL_JSON_WITH_STRICT_SCHEMA;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiOfficialAiServicesWithJsonSchemaIT extends AbstractAiServiceWithJsonSchemaIT {

    @Override
    protected List<ChatLanguageModel> models() {
        return InternalOpenAiOfficialTestHelper.chatModelsWithJsonResponse();
    }

    @Override
    protected boolean supportsRecursion() {
        return true;
    }

    @Override
    protected boolean isStrictJsonSchemaEnabled(ChatLanguageModel model) {
        return model == OPEN_AI_CHAT_MODEL_JSON_WITH_STRICT_SCHEMA;
    }
}
