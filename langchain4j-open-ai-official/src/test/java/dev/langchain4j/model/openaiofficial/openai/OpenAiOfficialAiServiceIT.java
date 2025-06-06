package dev.langchain4j.model.openaiofficial.openai;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialTokenUsage;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.common.AbstractAiServiceIT;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiOfficialAiServiceIT extends AbstractAiServiceIT {

    @Override
    protected List<ChatModel> models() {
        return InternalOpenAiOfficialTestHelper.chatModelsNormalAndJsonStrict();
    }

    @Override
    protected Class<? extends TokenUsage> tokenUsageType(ChatModel chatModel) {
        return OpenAiOfficialTokenUsage.class;
    }
}
