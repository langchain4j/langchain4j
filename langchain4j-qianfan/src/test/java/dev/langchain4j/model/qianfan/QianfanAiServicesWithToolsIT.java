package dev.langchain4j.model.qianfan;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServicesWithNewToolsIT;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

import static java.util.Collections.singletonList;

@EnabledIfEnvironmentVariable(named = "QIANFAN_API_KEY", matches = ".+")
class QianfanAiServicesWithToolsIT extends AiServicesWithNewToolsIT {

    @Override
    protected List<ChatLanguageModel> models() {
        return singletonList(
                QianfanChatModel.builder()
                        .apiKey(System.getenv("QIANFAN_API_KEY"))
                        .secretKey(System.getenv("QIANFAN_SECRET_KEY"))
                        .modelName("ERNIE-Bot 4.0")
                        .temperature(0.0)
                        .logRequests(true)
                        .logResponses(true)
                        .build()
        );
    }
}
