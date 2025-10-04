package dev.langchain4j.model.mistralai.common;

import static java.util.Collections.singletonList;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.mistralai.MistralAiChatModel;
import dev.langchain4j.service.common.AbstractAiServiceWithToolsIT;
import org.junit.jupiter.api.Disabled;
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

    @Override
    protected boolean verifyModelInteractions() {
        return true;
    }

    @Override
    @Disabled("Mistral is too strict and expects assistant message after tool message")
    protected void should_keep_memory_consistent_using_return_immediate(ChatModel model) {}
}
