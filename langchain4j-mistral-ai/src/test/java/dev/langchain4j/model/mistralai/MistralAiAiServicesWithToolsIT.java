package dev.langchain4j.model.mistralai;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServicesWithNewToolsIT;

import java.util.List;

import static java.util.Collections.singletonList;

class MistralAiAiServicesWithToolsIT extends AiServicesWithNewToolsIT {

    @Override
    protected List<ChatLanguageModel> models() {
        return singletonList(
                MistralAiChatModel.builder()
                        .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
                        .modelName("ministral-3b-latest")
                        .temperature(0.0)
                        .logRequests(true)
                        .logResponses(true)
                        .build()
        );
    }
}
