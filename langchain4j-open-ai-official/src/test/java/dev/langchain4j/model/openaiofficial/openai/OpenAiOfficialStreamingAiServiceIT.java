package dev.langchain4j.model.openaiofficial.openai;

import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.service.common.AbstractStreamingAiServiceIT;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiOfficialStreamingAiServiceIT extends AbstractStreamingAiServiceIT {

    @Override
    protected List<StreamingChatLanguageModel> models() {
        return InternalOpenAiOfficialTestHelper.chatModelsStreamingNormalAndJsonStrict();
    }
}
