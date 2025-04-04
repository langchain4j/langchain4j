package dev.langchain4j.model.mistralai.common;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.mistralai.MistralAiChatModel;
import dev.langchain4j.service.common.AbstractAiServiceWithToolsIT;

import java.util.List;

import static java.util.Collections.singletonList;

class MistralAiAiServiceWithToolsIT extends AbstractAiServiceWithToolsIT {

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
