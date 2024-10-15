package dev.langchain4j.model.zhipu;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServicesWithNewToolsIT;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.Collections;
import java.util.List;

import static java.time.Duration.ofSeconds;

@EnabledIfEnvironmentVariable(named = "ZHIPU_API_KEY", matches = ".+")
class ZhipuAiAiServicesWithToolsIT extends AiServicesWithNewToolsIT {

    @Override
    protected List<ChatLanguageModel> models() {
        return Collections.singletonList(
                ZhipuAiChatModel.builder()
                        .apiKey(System.getenv("ZHIPU_API_KEY"))
                        // .model() TODO specify model
                        .temperature(0.0)
                        .callTimeout(ofSeconds(60))
                        .connectTimeout(ofSeconds(60))
                        .readTimeout(ofSeconds(60))
                        .writeTimeout(ofSeconds(60))
                        .logRequests(true)
                        .logResponses(true)
                        .build()
        );
    }
}
