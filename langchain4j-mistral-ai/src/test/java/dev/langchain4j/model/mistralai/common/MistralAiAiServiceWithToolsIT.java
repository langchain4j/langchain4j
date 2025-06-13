package dev.langchain4j.model.mistralai.common;

import static java.util.Collections.singletonList;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.mistralai.MistralAiChatModel;
import dev.langchain4j.service.common.AbstractAiServiceWithToolsIT;
import java.util.List;

class MistralAiAiServiceWithToolsIT extends AbstractAiServiceWithToolsIT {

    @Override
    protected List<ChatModel> models() {
        return singletonList(MistralAiChatModel.builder()
                .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
                .modelName("ministral-3b-latest")
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .build());
    }
}
