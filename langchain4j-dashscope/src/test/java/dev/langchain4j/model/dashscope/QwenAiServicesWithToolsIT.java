package dev.langchain4j.model.dashscope;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServicesWithNewToolsIT;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

import static dev.langchain4j.model.dashscope.QwenModelName.QWEN_MAX;
import static dev.langchain4j.model.dashscope.QwenTestHelper.apiKey;
import static java.util.Collections.singletonList;

@EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
class QwenAiServicesWithToolsIT extends AiServicesWithNewToolsIT {

    @Override
    protected List<ChatLanguageModel> models() {
        return singletonList(
                QwenChatModel.builder()
                        .apiKey(apiKey())
                        .modelName(QWEN_MAX)
                        .temperature(0.0f)
                        .build()
        );
    }
}
